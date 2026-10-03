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

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The last events of a camera, kept in memory, and whoever follows them live.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class EventLog {

    public static final int CAPACITY = 50;

    private final Deque<CameraEvent> events = new ArrayDeque<>();
    private final List<Consumer<CameraEvent>> listeners = new CopyOnWriteArrayList<>();

    public void add(CameraEvent event) {
        synchronized (events) {
            events.addFirst(event);
            while (events.size() > CAPACITY) {
                events.removeLast();
            }
        }
        listeners.forEach(listener -> listener.accept(event));
    }

    /**
     * Notes when the clip of an event was finished. Listeners get the event again with the end filled in.
     */
    public void finishRecording(String clipId, Instant end) {
        CameraEvent updated = null;
        synchronized (events) {
            List<CameraEvent> copy = List.copyOf(events);
            events.clear();
            for (CameraEvent event : copy) {
                if (updated == null && clipId.equals(event.clipId()) && event.recordingEnd() == null) {
                    updated = event.withRecordingEnd(end);
                    events.addLast(updated);
                } else {
                    events.addLast(event);
                }
            }
        }
        CameraEvent changed = updated;
        if (changed != null) {
            listeners.forEach(listener -> listener.accept(changed));
        }
    }

    /**
     * @return the events, newest first
     */
    public List<CameraEvent> list() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    /**
     * @param maxListeners how many may follow the log at once
     * @return whether the listener was added, {@code false} if there are already as many as allowed
     */
    public synchronized boolean addListener(Consumer<CameraEvent> listener, int maxListeners) {
        if (listeners.size() >= maxListeners) {
            return false;
        }
        listeners.add(Objects.requireNonNull(listener));
        return true;
    }

    public synchronized void removeListener(Consumer<CameraEvent> listener) {
        listeners.remove(listener);
    }
}
