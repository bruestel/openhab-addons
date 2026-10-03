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

import java.io.IOException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests reading RTP headers.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class RtpPacketTest {

    @Test
    public void headerFieldsAreRead() throws IOException {
        byte[] data = { (byte) 0x80, (byte) 0xa3, 0x12, 0x34, (byte) 0xfe, (byte) 0xdc, (byte) 0xba, (byte) 0x98, 0, 0,
                0, 1, 0x65, 7 };

        RtpPacket packet = RtpPacket.parse(data);

        assertTrue(packet.marker());
        assertEquals(35, packet.payloadType());
        assertEquals(0x1234, packet.sequenceNumber());
        assertEquals(0xfedcba98L, packet.timestamp());
        assertArrayEquals(new byte[] { 0x65, 7 }, packet.payload());
    }

    @Test
    public void paddingAndExtensionAreSkipped() throws IOException {
        // padding and extension bit set, one extension word, two bytes of padding
        byte[] data = { (byte) 0xb0, 35, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 9, 9, 9, 9, 0x41, 5, 0, 2 };

        assertArrayEquals(new byte[] { 0x41, 5 }, RtpPacket.parse(data).payload());
    }

    @Test
    public void otherVersionsAreRefused() {
        assertThrows(IOException.class, () -> RtpPacket.parse(new byte[12]));
    }
}
