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

import java.util.Arrays;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Takes the AAC frames out of an RTP stream in the {@code mpeg4-generic} format of RFC 3640 (AAC-hbr): a block of
 * AU headers telling the size of each frame, followed by the frames.
 *
 * The cameras put an ADTS header in front of every frame, which the format does not provide for. MP4 needs the frames
 * raw, so the header is removed; frames without one are passed on as they are.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class AacDepacketizer {

    /**
     * Samples per AAC frame, which is what the timestamps advance by.
     */
    public static final int SAMPLES_PER_FRAME = 1024;

    /**
     * @param timestamp the RTP timestamp in units of the sample rate
     * @param data the raw AAC frame, without ADTS header
     */
    public record AacFrame(long timestamp, byte[] data) {
    }

    private final Consumer<AacFrame> sink;
    private final int sizeLength;
    private final int indexLength;

    /**
     * @param sizeLength bits of the size in each AU header, {@code sizelength} in the SDP, 13 for AAC-hbr
     * @param indexLength bits of the index, {@code indexlength} in the SDP, 3 for AAC-hbr
     */
    public AacDepacketizer(Consumer<AacFrame> sink, int sizeLength, int indexLength) {
        this.sink = sink;
        this.sizeLength = sizeLength;
        this.indexLength = indexLength;
    }

    public void process(RtpPacket packet) {
        byte[] payload = packet.payload();
        if (payload.length < 2) {
            return;
        }
        int headersBits = ((payload[0] & 0xff) << 8) | (payload[1] & 0xff);
        int headerBits = sizeLength + indexLength;
        if (headerBits == 0 || headersBits % headerBits != 0) {
            return;
        }
        int count = headersBits / headerBits;
        int dataOffset = 2 + (headersBits + 7) / 8;
        if (dataOffset > payload.length) {
            return;
        }
        long timestamp = packet.timestamp();
        for (int i = 0; i < count; i++) {
            int size = readBits(payload, 16 + i * headerBits, sizeLength);
            if (dataOffset + size > payload.length) {
                // a frame split over several packets, which the cameras do not send at their bit rates
                return;
            }
            sink.accept(new AacFrame(timestamp,
                    withoutAdtsHeader(Arrays.copyOfRange(payload, dataOffset, dataOffset + size))));
            dataOffset += size;
            timestamp += SAMPLES_PER_FRAME;
        }
    }

    /**
     * @return the frame without its ADTS header if it has one: sync word, and a frame length that matches
     */
    static byte[] withoutAdtsHeader(byte[] frame) {
        if (frame.length < 7 || (frame[0] & 0xff) != 0xff || (frame[1] & 0xf6) != 0xf0) {
            return frame;
        }
        int frameLength = ((frame[3] & 0x03) << 11) | ((frame[4] & 0xff) << 3) | ((frame[5] & 0xe0) >> 5);
        boolean withCrc = (frame[1] & 0x01) == 0;
        int headerLength = withCrc ? 9 : 7;
        if (frameLength != frame.length || frame.length <= headerLength) {
            return frame;
        }
        return Arrays.copyOfRange(frame, headerLength, frame.length);
    }

    private static int readBits(byte[] data, int bitOffset, int length) {
        int value = 0;
        for (int i = 0; i < length; i++) {
            int bit = bitOffset + i;
            value = (value << 1) | ((data[bit / 8] >> (7 - bit % 8)) & 1);
        }
        return value;
    }
}
