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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openhab.binding.boschsmartcam.internal.api.dto.CloudEvent;

/**
 * Tests paging, matching and caching of the events the cloud keeps, against a cloud of 250 events.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class CloudEventFeedTest {

    private static final Instant NEWEST = Instant.parse("2026-10-03T15:43:45.435Z");
    private static final int TOTAL = 250;

    private final AtomicInteger requests = new AtomicInteger();
    private final MutableClock clock = new MutableClock();
    private final CloudEventFeed feed = new CloudEventFeed(this::fetch, clock);

    @Test
    public void listsTheNewestFirst() throws Exception {
        List<CloudEvent> events = feed.list(3, null, null);
        assertEquals(List.of(id(0), id(1), id(2)), ids(events));
    }

    @Test
    public void sinceStopsAtTheEventTheClientHas() throws Exception {
        assertEquals(List.of(id(0), id(1)), ids(feed.list(20, null, id(2))));
    }

    @Test
    public void beforePagesBackBeyondTheFirstPage() throws Exception {
        assertEquals(List.of(id(101), id(102)), ids(feed.list(2, id(100), null)));
        assertEquals(List.of(id(99), id(100), id(101)), ids(feed.list(3, id(98), null)));
        assertTrue(feed.list(5, "00000000-0000-0000-0000-999999999999", null).isEmpty());
    }

    @Test
    public void newestPageIsReusedUntilInvalidated() throws Exception {
        feed.list(5, null, null);
        feed.list(5, null, null);
        assertEquals(1, requests.get());

        feed.invalidate();
        feed.list(5, null, null);
        assertEquals(2, requests.get());

        clock.advance(CloudEventFeed.CACHE_DURATION.plusSeconds(1));
        feed.list(5, null, null);
        assertEquals(3, requests.get());
    }

    @Test
    public void localEventIsMatchedByTimeWithinTheWindow() throws Exception {
        CloudEvent match = feed.matching(NEWEST.minusSeconds(60).plusMillis(55), Duration.ofSeconds(3));
        assertNotNull(match);
        assertEquals(id(1), match.id());
        assertNull(feed.matching(NEWEST.plusSeconds(30), Duration.ofSeconds(3)));
    }

    @Test
    public void onlyEventsOfThisCameraAreFound() throws Exception {
        assertNotNull(feed.find(id(3), false));
        assertNull(feed.find("00000000-0000-0000-0000-999999999999", false));
    }

    @Test
    public void clipStateFollowsTheUpload() {
        assertEquals(CloudEvent.ClipState.READY, event(0, "Done", "https://x/clip.mp4").clipState());
        assertEquals(CloudEvent.ClipState.PENDING, event(0, "Pending", null).clipState());
        assertEquals(CloudEvent.ClipState.NONE, event(0, null, null).clipState());
    }

    @Test
    public void timeOfTheCloudIsRead() {
        CloudEvent event = new CloudEvent(id(0), null, "MOVEMENT", List.of("PERSON"),
                "2026-10-03T17:43:45.435+02:00[Europe/Berlin]", false, null, null, null);
        ZonedDateTime time = event.time();
        assertNotNull(time);
        assertEquals(NEWEST, time.toInstant());
        assertEquals("PERSON", event.kind());
    }

    private List<CloudEvent> fetch(int page, int pageSize) {
        requests.incrementAndGet();
        List<CloudEvent> events = new ArrayList<>();
        for (int i = page * pageSize; i < Math.min(TOTAL, (page + 1) * pageSize); i++) {
            events.add(event(i, "Done", "https://x/" + i + "/clip.mp4"));
        }
        return events;
    }

    /**
     * Events one minute apart, the newest first.
     */
    private static CloudEvent event(int index, @Nullable String uploadStatus, @Nullable String clipUrl) {
        String time = NEWEST.minusSeconds(60L * index).atZone(ZoneId.of("Europe/Berlin")).toString();
        return new CloudEvent(id(index), "camera", "MOVEMENT", List.of("PERSON"), time, false,
                "https://x/" + index + "/snap.jpg", clipUrl, uploadStatus);
    }

    private static String id(int index) {
        return "00000000-0000-0000-0000-%012d".formatted(index);
    }

    private static List<@Nullable String> ids(List<CloudEvent> events) {
        return events.stream().map(CloudEvent::id).toList();
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-03T16:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(@Nullable ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
