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
package org.openhab.binding.boschsmartcam.internal.video;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The media of an RTSP session as the camera describes them in SDP (RFC 8866), reduced to what is needed to receive
 * them.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public final class Sdp {

    /**
     * One {@code m=} section.
     *
     * @param type {@code video} or {@code audio}
     * @param payloadType the RTP payload type of the stream
     * @param encoding the codec in upper case, e.g. {@code H264} or {@code MPEG4-GENERIC}
     * @param clockRate the RTP clock in Hz
     * @param channels audio channels, 1 if not given
     * @param parameters the {@code a=fmtp} parameters, names in lower case
     * @param control the URL to set the stream up with, already resolved against the base
     */
    public record Media(String type, int payloadType, String encoding, int clockRate, int channels,
            Map<String, String> parameters, String control) {

        public @Nullable String parameter(String name) {
            return parameters.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private Sdp() {
    }

    /**
     * @param base the URL relative controls are resolved against, the {@code Content-Base} of the answer or the
     *            requested URL
     */
    public static List<Media> parse(String sdp, String base) {
        List<Media> media = new ArrayList<>();
        String sessionControl = null;
        MediaBuilder current = null;
        for (String rawLine : sdp.split("\\r?\\n")) {
            String line = rawLine.strip();
            if (line.startsWith("m=")) {
                if (current != null) {
                    current.build(base, sessionControl).ifPresent(media::add);
                }
                String[] parts = line.substring(2).split(" ");
                current = new MediaBuilder(parts[0], parts.length > 3 ? parseInt(parts[3], -1) : -1);
            } else if (line.startsWith("a=control:")) {
                String control = line.substring("a=control:".length()).strip();
                if (current == null) {
                    sessionControl = control;
                } else {
                    current.control = control;
                }
            } else if (current != null && line.startsWith("a=rtpmap:")) {
                // a=rtpmap:96 H264/90000 or a=rtpmap:97 mpeg4-generic/16000/1
                String[] parts = line.substring("a=rtpmap:".length()).split(" ", 2);
                if (parts.length == 2 && parseInt(parts[0], -2) == current.payloadType) {
                    String[] codec = parts[1].strip().split("/");
                    current.encoding = codec[0].toUpperCase(Locale.ROOT);
                    current.clockRate = codec.length > 1 ? parseInt(codec[1], 0) : 0;
                    current.channels = codec.length > 2 ? parseInt(codec[2], 1) : 1;
                }
            } else if (current != null && line.startsWith("a=fmtp:")) {
                String[] parts = line.substring("a=fmtp:".length()).split(" ", 2);
                if (parts.length == 2) {
                    for (String parameter : parts[1].split(";")) {
                        int equals = parameter.indexOf('=');
                        if (equals > 0) {
                            current.parameters.put(parameter.substring(0, equals).strip().toLowerCase(Locale.ROOT),
                                    parameter.substring(equals + 1).strip());
                        }
                    }
                }
            }
        }
        if (current != null) {
            current.build(base, sessionControl).ifPresent(media::add);
        }
        return media;
    }

    /**
     * Resolves a control URL the way ffmpeg does, which is known to work with the cameras: a relative control is
     * appended to the base with a slash, even if the base carries a query.
     */
    static String resolve(String base, @Nullable String sessionControl, @Nullable String control) {
        if (control == null || control.isBlank() || "*".equals(control)) {
            return sessionControl == null || "*".equals(sessionControl) ? base : resolve(base, null, sessionControl);
        }
        if (control.contains("://")) {
            return control;
        }
        return (base.endsWith("/") ? base : base + "/") + control;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static class MediaBuilder {
        final String type;
        final int payloadType;
        String encoding = "";
        int clockRate;
        int channels = 1;
        final Map<String, String> parameters = new HashMap<>();
        @Nullable
        String control;

        MediaBuilder(String type, int payloadType) {
            this.type = type;
            this.payloadType = payloadType;
        }

        Optional<Media> build(String base, @Nullable String sessionControl) {
            if (payloadType < 0 || encoding.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Media(type, payloadType, encoding, clockRate, channels, Map.copyOf(parameters),
                    resolve(base, sessionControl, control)));
        }
    }
}
