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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.CloudEvent;

/**
 * The events the cloud keeps for one camera, read page by page.
 *
 * The newest page is reused for a few seconds, so a client that asks often does not reach the cloud every time; a new
 * local event drops it. Every event seen is remembered by its id, which is how an image or clip asked for by id is
 * known to belong to this camera.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class CloudEventFeed {

    @FunctionalInterface
    public interface Source {
        List<CloudEvent> fetch(int page, int pageSize) throws BoschSmartCamException;
    }

    static final int PAGE_SIZE = 100;
    static final Duration CACHE_DURATION = Duration.ofSeconds(10);
    /**
     * How far back a client can page with {@code before}.
     */
    static final int MAX_PAGES = 10;
    private static final int MAX_KNOWN = 1000;
    /**
     * Enough to find an event that just happened.
     */
    private static final int RECENT_PAGE_SIZE = 5;

    private final Source source;
    private final Clock clock;

    /**
     * The newest page and when it was fetched. The cloud is never asked while a lock is held; two clients asking at
     * once may fetch it twice, which does no harm.
     */
    private record Page(List<CloudEvent> events, Instant fetched) {
    }

    private volatile @Nullable Page newest;
    private final Map<String, CloudEvent> known = new LinkedHashMap<>() {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.@Nullable Entry<String, CloudEvent> eldest) {
            return size() > MAX_KNOWN;
        }
    };

    public CloudEventFeed(Source source) {
        this(source, Clock.systemUTC());
    }

    CloudEventFeed(Source source, Clock clock) {
        this.source = source;
        this.clock = clock;
    }

    /**
     * Drops the cached newest page, so the next request sees an event that just happened.
     */
    public void invalidate() {
        newest = null;
    }

    /**
     * @param limit how many events to return at most
     * @param before only events older than the one with this id, for paging back
     * @param since only events newer than the one with this id, for a client that already has the older ones
     * @return events, newest first; empty if {@code before} is not among the last {@link #MAX_PAGES} pages
     */
    public List<CloudEvent> list(int limit, @Nullable String before, @Nullable String since)
            throws BoschSmartCamException {
        List<CloudEvent> result = new ArrayList<>();
        if (before == null) {
            for (CloudEvent event : newest()) {
                if (result.size() >= limit || (since != null && since.equals(event.id()))) {
                    break;
                }
                result.add(event);
            }
            return result;
        }
        boolean found = false;
        for (int page = 0; page < MAX_PAGES && result.size() < limit; page++) {
            List<CloudEvent> events = page == 0 ? newest() : fetch(page, PAGE_SIZE);
            for (CloudEvent event : events) {
                if (found && result.size() < limit) {
                    result.add(event);
                }
                found |= before.equals(event.id());
            }
            if (events.size() < PAGE_SIZE) {
                break;
            }
        }
        return result;
    }

    /**
     * @param needsClip whether the clip is wanted; an event remembered while its clip was still uploading is then
     *            looked up again
     * @return the event with this id if it belongs to this camera, after a look at the newest page if it is not known
     *         yet
     */
    public @Nullable CloudEvent find(String id, boolean needsClip) throws BoschSmartCamException {
        CloudEvent event = known(id);
        if (event == null || (needsClip && event.videoClipUrl() == null)) {
            invalidate();
            newest();
            event = known(id);
        }
        return event;
    }

    private @Nullable CloudEvent known(String id) {
        synchronized (known) {
            return known.get(id);
        }
    }

    /**
     * Asks the cloud for its newest events, bypassing the cache, and returns the one that happened closest to a
     * local event, so long as it lies within the window.
     */
    public @Nullable CloudEvent matching(Instant localTime, Duration window) throws BoschSmartCamException {
        CloudEvent best = null;
        Duration bestDistance = window;
        for (CloudEvent event : fetch(0, RECENT_PAGE_SIZE)) {
            ZonedDateTime time = event.time();
            if (time == null) {
                continue;
            }
            Duration distance = Duration.between(time.toInstant(), localTime).abs();
            if (distance.compareTo(bestDistance) <= 0) {
                best = event;
                bestDistance = distance;
            }
        }
        return best;
    }

    private List<CloudEvent> newest() throws BoschSmartCamException {
        Page cached = newest;
        Instant now = clock.instant();
        if (cached == null || cached.fetched().plus(CACHE_DURATION).isBefore(now)) {
            cached = new Page(fetch(0, PAGE_SIZE), now);
            newest = cached;
        }
        return cached.events();
    }

    private List<CloudEvent> fetch(int page, int pageSize) throws BoschSmartCamException {
        List<CloudEvent> events = source.fetch(page, pageSize);
        synchronized (known) {
            for (CloudEvent event : events) {
                String id = event.id();
                if (id != null) {
                    known.put(id, event);
                }
            }
        }
        return events;
    }
}
