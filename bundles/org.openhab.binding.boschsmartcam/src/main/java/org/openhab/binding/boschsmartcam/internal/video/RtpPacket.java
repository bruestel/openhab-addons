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

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * An RTP packet as defined in RFC 3550, reduced to what the binding needs.
 *
 * @param payloadType the payload type, which the SDP maps to a codec
 * @param marker the marker bit; for H.264 it marks the last packet of an access unit
 * @param sequenceNumber 16 bit counter to notice lost packets
 * @param timestamp 32 bit media clock of the payload, unsigned
 * @param payload the payload without header, extension and padding
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record RtpPacket(int payloadType, boolean marker, int sequenceNumber, long timestamp, byte[] payload) {

    private static final int HEADER_LENGTH = 12;

    public static RtpPacket parse(byte[] data) throws IOException {
        if (data.length < HEADER_LENGTH || (data[0] & 0xc0) != 0x80) {
            throw new IOException("Not an RTP packet of version 2");
        }
        boolean padding = (data[0] & 0x20) != 0;
        boolean extension = (data[0] & 0x10) != 0;
        int csrcCount = data[0] & 0x0f;
        boolean marker = (data[1] & 0x80) != 0;
        int payloadType = data[1] & 0x7f;
        int sequenceNumber = ((data[2] & 0xff) << 8) | (data[3] & 0xff);
        long timestamp = ((long) (data[4] & 0xff) << 24) | ((data[5] & 0xff) << 16) | ((data[6] & 0xff) << 8)
                | (data[7] & 0xff);

        int start = HEADER_LENGTH + 4 * csrcCount;
        if (extension) {
            if (data.length < start + 4) {
                throw new IOException("RTP header extension is cut off");
            }
            int words = ((data[start + 2] & 0xff) << 8) | (data[start + 3] & 0xff);
            start += 4 + 4 * words;
        }
        int end = data.length;
        if (padding && end > start) {
            end -= data[end - 1] & 0xff;
        }
        if (start > end) {
            throw new IOException("RTP packet without payload");
        }
        return new RtpPacket(payloadType, marker, sequenceNumber, timestamp, Arrays.copyOfRange(data, start, end));
    }
}
