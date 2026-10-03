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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openhab.binding.boschsmartcam.internal.video.H264Depacketizer.AccessUnit;

/**
 * Tests putting H.264 pictures back together from RTP packets.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class H264DepacketizerTest {

    private static final byte IDR = 0x65;
    private static final byte NON_IDR = 0x41;

    private final List<AccessUnit> units = new ArrayList<>();
    private H264Depacketizer depacketizer = new H264Depacketizer(units::add);
    private int sequence;

    @BeforeEach
    public void setUp() {
        units.clear();
        depacketizer = new H264Depacketizer(units::add);
        sequence = 100;
    }

    @Test
    public void singleUnitPacketsMakeAPicture() {
        send(1000, true, new byte[] { IDR, 1, 2, 3 });

        assertEquals(1, units.size());
        assertTrue(units.getFirst().keyframe());
        assertArrayEquals(new byte[] { IDR, 1, 2, 3 }, units.getFirst().nalUnits().getFirst());
    }

    @Test
    public void aggregatedUnitsAreSplit() {
        // STAP-A with an SPS of three and a PPS of two bytes, then the picture
        send(1000, false, new byte[] { 0x78, 0, 3, 0x67, 1, 2, 0, 2, 0x68, 3 });
        send(1000, true, new byte[] { IDR, 9 });

        assertEquals(1, units.size());
        List<byte[]> nalUnits = units.getFirst().nalUnits();
        assertEquals(3, nalUnits.size());
        assertArrayEquals(new byte[] { 0x67, 1, 2 }, nalUnits.get(0));
        assertArrayEquals(new byte[] { 0x68, 3 }, nalUnits.get(1));
    }

    @Test
    public void fragmentsAreJoinedWithTheirHeaderRebuilt() {
        // FU-A: indicator keeps NRI 3 of the IDR, the header carries start/end and type 5
        send(1000, false, new byte[] { 0x7c, (byte) 0x85, 1, 2 });
        send(1000, false, new byte[] { 0x7c, 0x05, 3, 4 });
        send(1000, true, new byte[] { 0x7c, 0x45, 5 });

        assertEquals(1, units.size());
        assertArrayEquals(new byte[] { IDR, 1, 2, 3, 4, 5 }, units.getFirst().nalUnits().getFirst());
        assertTrue(units.getFirst().keyframe());
    }

    @Test
    public void picturesBeforeTheFirstKeyframeAreDropped() {
        send(1000, true, new byte[] { NON_IDR, 1 });
        send(4000, true, new byte[] { IDR, 2 });
        send(7000, true, new byte[] { NON_IDR, 3 });

        assertEquals(2, units.size());
        assertEquals(4000, units.getFirst().timestamp());
    }

    @Test
    public void lossDropsEverythingUpToTheNextKeyframe() {
        send(1000, true, new byte[] { IDR, 1 });
        sequence++; // a lost packet
        send(4000, true, new byte[] { NON_IDR, 2 });
        send(7000, true, new byte[] { NON_IDR, 3 });
        send(10000, true, new byte[] { IDR, 4 });

        assertEquals(2, units.size());
        assertEquals(1000, units.get(0).timestamp());
        assertEquals(10000, units.get(1).timestamp());
    }

    @Test
    public void missingMarkerIsCoveredByTheTimestamp() {
        send(1000, false, new byte[] { IDR, 1 });
        send(4000, true, new byte[] { NON_IDR, 2 });

        assertEquals(2, units.size());
    }

    private void send(long timestamp, boolean marker, byte[] payload) {
        depacketizer.process(new RtpPacket(35, marker, sequence++ & 0xffff, timestamp, payload));
    }
}
