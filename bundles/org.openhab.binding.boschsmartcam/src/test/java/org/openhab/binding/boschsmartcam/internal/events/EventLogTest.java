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
package org.openhab.binding.boschsmartcam.internal.events;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests the in memory event log of a camera.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class EventLogTest {

    private static final Instant T0 = Instant.parse("2026-10-03T08:36:41Z");

    @Test
    public void newestEventComesFirstAndOldOnesAreDropped() {
        EventLog log = new EventLog();
        for (int i = 0; i < EventLog.CAPACITY + 5; i++) {
            log.add(new CameraEvent(T0.plusSeconds(i), "PERSON", String.valueOf(i), null));
        }

        List<CameraEvent> events = log.list();
        assertEquals(EventLog.CAPACITY, events.size());
        assertEquals(String.valueOf(EventLog.CAPACITY + 4), events.getFirst().clipId());
        assertEquals("5", events.getLast().clipId());
    }

    @Test
    public void finishedRecordingIsNotedAndPassedOn() {
        EventLog log = new EventLog();
        List<CameraEvent> seen = new ArrayList<>();
        log.add(new CameraEvent(T0, "PERSON", "10154", null));
        log.addListener(seen::add, 1);

        log.finishRecording("10154", T0.plusSeconds(15));

        assertEquals(T0.plusSeconds(15), log.list().getFirst().recordingEnd());
        assertEquals(1, seen.size());
        assertEquals(T0.plusSeconds(15), seen.getFirst().recordingEnd());
    }

    @Test
    public void unknownClipChangesNothing() {
        EventLog log = new EventLog();
        List<CameraEvent> seen = new ArrayList<>();
        log.add(new CameraEvent(T0, "PERSON", "10154", null));
        log.addListener(seen::add, 1);

        log.finishRecording("99999", T0.plusSeconds(15));

        assertNull(log.list().getFirst().recordingEnd());
        assertTrue(seen.isEmpty());
    }

    @Test
    public void listenersAreLimited() {
        EventLog log = new EventLog();
        Consumer<CameraEvent> first = event -> {
        };
        Consumer<CameraEvent> second = event -> {
        };

        assertTrue(log.addListener(first, 1));
        assertFalse(log.addListener(second, 1));
        log.removeListener(first);
        assertTrue(log.addListener(second, 1));
    }
}
