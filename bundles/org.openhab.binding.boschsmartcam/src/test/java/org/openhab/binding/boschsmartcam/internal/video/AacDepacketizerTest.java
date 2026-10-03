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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.binding.boschsmartcam.internal.video.AacDepacketizer.AacFrame;

/**
 * Tests taking AAC frames out of RTP packets in the AAC-hbr format.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class AacDepacketizerTest {

    @Test
    public void framesAreSplitByTheirHeaders() {
        List<AacFrame> frames = new ArrayList<>();
        AacDepacketizer depacketizer = new AacDepacketizer(frames::add, 13, 3);
        // two AU headers of 16 bits: size 3 and size 2, index 0, then the frames
        byte[] payload = { 0x00, 0x20, 0x00, 0x18, 0x00, 0x10, 1, 2, 3, 4, 5 };

        depacketizer.process(new RtpPacket(96, true, 1, 16000, payload));

        assertEquals(2, frames.size());
        assertArrayEquals(new byte[] { 1, 2, 3 }, frames.get(0).data());
        assertEquals(16000, frames.get(0).timestamp());
        assertArrayEquals(new byte[] { 4, 5 }, frames.get(1).data());
        assertEquals(16000 + AacDepacketizer.SAMPLES_PER_FRAME, frames.get(1).timestamp());
    }

    @Test
    public void adtsHeaderOfTheCamerasIsRemoved() {
        // the header the cameras send: no CRC, AAC-LC, 16 kHz, mono, frame length 10
        byte[] frame = { (byte) 0xff, (byte) 0xf1, 0x60, 0x40, 0x01, 0x5f, (byte) 0xfc, 1, 2, 3 };

        assertArrayEquals(new byte[] { 1, 2, 3 }, AacDepacketizer.withoutAdtsHeader(frame));
    }

    @Test
    public void rawFrameIsPassedOn() {
        byte[] frame = { 0x21, 0x10, 5, 6, 7, 8, 9, 10 };

        assertArrayEquals(frame, AacDepacketizer.withoutAdtsHeader(frame));
    }

    @Test
    public void syncWordWithAWrongLengthIsNoHeader() {
        // looks like a header, but announces 20 bytes for a frame of 10
        byte[] frame = { (byte) 0xff, (byte) 0xf1, 0x60, 0x40, 0x02, (byte) 0x9f, (byte) 0xfc, 1, 2, 3 };

        assertArrayEquals(frame, AacDepacketizer.withoutAdtsHeader(frame));
    }

    @Test
    public void cutOffPacketIsIgnored() {
        List<AacFrame> frames = new ArrayList<>();
        AacDepacketizer depacketizer = new AacDepacketizer(frames::add, 13, 3);
        // announces 100 bytes, carries 2
        byte[] payload = { 0x00, 0x10, 0x03, 0x20, 1, 2 };

        depacketizer.process(new RtpPacket(96, true, 1, 0, payload));

        assertTrue(frames.isEmpty());
    }
}
