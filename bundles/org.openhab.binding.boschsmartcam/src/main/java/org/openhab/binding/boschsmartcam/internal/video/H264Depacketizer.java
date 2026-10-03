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

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Puts the NAL units of an H.264 RTP stream (RFC 6184) back together into access units, one per picture. Handles
 * single NAL unit packets, STAP-A and FU-A, which is what the cameras send.
 *
 * When a packet is lost, the picture it belonged to is dropped and so is everything up to the next keyframe - the
 * pictures in between refer to the lost one and would only decode to garbage.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class H264Depacketizer {

    public static final int NAL_IDR = 5;
    public static final int NAL_SPS = 7;
    public static final int NAL_PPS = 8;
    private static final int NAL_STAP_A = 24;
    private static final int NAL_FU_A = 28;

    /**
     * The NAL units of one picture.
     *
     * @param timestamp the RTP timestamp, 90 kHz
     * @param nalUnits the NAL units without start codes
     * @param keyframe whether the picture can be decoded on its own
     */
    public record AccessUnit(long timestamp, List<byte[]> nalUnits, boolean keyframe) {
    }

    private final Consumer<AccessUnit> sink;
    private final List<byte[]> nalUnits = new ArrayList<>();
    private long timestamp = -1;
    private int lastSequenceNumber = -1;
    private @Nullable ByteArrayOutputStream fragment;
    private boolean broken;
    private boolean waitingForKeyframe = true;

    public H264Depacketizer(Consumer<AccessUnit> sink) {
        this.sink = sink;
    }

    public void process(RtpPacket packet) {
        if (lastSequenceNumber >= 0 && ((lastSequenceNumber + 1) & 0xffff) != packet.sequenceNumber()) {
            fragment = null;
            broken = true;
        }
        lastSequenceNumber = packet.sequenceNumber();
        if (timestamp >= 0 && packet.timestamp() != timestamp) {
            // the marker of the previous picture got lost
            flush();
        }
        timestamp = packet.timestamp();

        byte[] payload = packet.payload();
        if (payload.length > 0) {
            int type = payload[0] & 0x1f;
            if (type == NAL_STAP_A) {
                unpackAggregate(payload);
            } else if (type == NAL_FU_A) {
                unpackFragment(payload);
            } else if (type > 0 && type < NAL_STAP_A) {
                nalUnits.add(payload);
            }
        }
        if (packet.marker()) {
            flush();
        }
    }

    private void unpackAggregate(byte[] payload) {
        int offset = 1;
        while (offset + 2 <= payload.length) {
            int size = ((payload[offset] & 0xff) << 8) | (payload[offset + 1] & 0xff);
            offset += 2;
            if (size == 0 || offset + size > payload.length) {
                broken = true;
                return;
            }
            nalUnits.add(Arrays.copyOfRange(payload, offset, offset + size));
            offset += size;
        }
    }

    private void unpackFragment(byte[] payload) {
        if (payload.length < 2) {
            return;
        }
        int header = payload[1] & 0xff;
        boolean start = (header & 0x80) != 0;
        boolean end = (header & 0x40) != 0;
        ByteArrayOutputStream current = fragment;
        if (start) {
            current = new ByteArrayOutputStream();
            // the NAL header is rebuilt from the indicator and the type in the fragment header
            current.write((payload[0] & 0xe0) | (header & 0x1f));
            fragment = current;
        } else if (current == null) {
            // the start of this unit was lost
            broken = true;
            return;
        }
        current.write(payload, 2, payload.length - 2);
        if (end) {
            nalUnits.add(current.toByteArray());
            fragment = null;
        }
    }

    private void flush() {
        if (!nalUnits.isEmpty()) {
            boolean keyframe = nalUnits.stream().anyMatch(nal -> (nal[0] & 0x1f) == NAL_IDR);
            if (keyframe) {
                waitingForKeyframe = false;
            }
            if (broken) {
                waitingForKeyframe = true;
            } else if (!waitingForKeyframe) {
                sink.accept(new AccessUnit(timestamp, List.copyOf(nalUnits), keyframe));
            }
        }
        nalUnits.clear();
        fragment = null;
        broken = false;
        timestamp = -1;
    }
}
