/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.boschsmartcam.internal.local;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * HTTP Digest authentication (RFC 2617) as the cameras ask for it on their RTSP tunnel: MD5, without {@code qop}.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class RtspDigest {

    private static final Pattern PARAMETER = Pattern.compile("(\\w+)=\"?([^\",]*)\"?");

    private final String user;
    private final String password;
    private volatile @Nullable String realm;
    private volatile @Nullable String nonce;
    private volatile @Nullable String opaque;

    public RtspDigest(String user, String password) {
        this.user = user;
        this.password = password;
    }

    /**
     * Takes over the parameters of a {@code WWW-Authenticate} header.
     *
     * @return whether it was a Digest challenge
     */
    public boolean takeChallenge(String header) {
        if (!header.regionMatches(true, 0, "Digest", 0, 6)) {
            return false;
        }
        Matcher matcher = PARAMETER.matcher(header.substring(6));
        while (matcher.find()) {
            switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
                case "realm" -> realm = matcher.group(2);
                case "nonce" -> nonce = matcher.group(2);
                case "opaque" -> opaque = matcher.group(2);
                default -> {
                    // algorithm is MD5, stale needs no handling
                }
            }
        }
        return true;
    }

    /**
     * @return the value of an {@code Authorization} header for the request, or {@code null} before the first
     *         challenge
     */
    public @Nullable String authorization(String method, String uri) {
        String currentRealm = realm;
        String currentNonce = nonce;
        if (currentRealm == null || currentNonce == null) {
            return null;
        }
        String ha1 = md5(user + ":" + currentRealm + ":" + password);
        String ha2 = md5(method + ":" + uri);
        String response = md5(ha1 + ":" + currentNonce + ":" + ha2);
        String currentOpaque = opaque;
        return "Digest username=\"" + user + "\", realm=\"" + currentRealm + "\", nonce=\"" + currentNonce
                + "\", uri=\"" + uri + "\", response=\"" + response + "\""
                + (currentOpaque == null || currentOpaque.isEmpty() ? "" : ", opaque=\"" + currentOpaque + "\"")
                + ", algorithm=MD5";
    }

    static String md5(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is part of every JDK", e);
        }
    }
}
