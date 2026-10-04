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
package org.openhab.binding.boschsmartcam.internal.auth;

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.SHA_256;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * A PKCE code verifier and its derived S256 code challenge as required by the Bosch authorization server
 * (RFC 7636).
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record PkceChallenge(String verifier, String challenge) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VERIFIER_BYTES = 48;

    public static PkceChallenge create() {
        byte[] randomBytes = new byte[VERIFIER_BYTES];
        RANDOM.nextBytes(randomBytes);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256).digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return new PkceChallenge(verifier, Base64.getUrlEncoder().withoutPadding().encodeToString(digest));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is part of every Java runtime
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
