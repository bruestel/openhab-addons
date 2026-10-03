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
package org.openhab.binding.boschsmartcam.internal.api.dto;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * An event as the cloud keeps it, one entry of {@code GET /v11/events}. The cloud does not know the clip id the
 * camera reports locally; its {@code timestamp} lies within a few hundred milliseconds of the local detection, which
 * is what an event is matched by.
 *
 * The clip is uploaded after the camera finished recording: until then {@code videoClipUploadStatus} is
 * {@code Pending} and there is no {@code videoClipUrl}, the image is there right away.
 *
 * @param timestamp e.g. {@code 2026-10-03T17:43:45.435+02:00[Europe/Berlin]}
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record CloudEvent(@Nullable String id, @Nullable String videoInputId, @Nullable String eventType,
        @Nullable List<String> eventTags, @Nullable String timestamp, @Nullable Boolean isRead,
        @Nullable String imageUrl, @Nullable String videoClipUrl, @Nullable String videoClipUploadStatus) {

    private static final String UPLOAD_DONE = "Done";
    private static final String UPLOAD_PENDING = "Pending";

    public enum ClipState {
        READY,
        PENDING,
        NONE
    }

    /**
     * @return when the event happened, or {@code null} if the cloud sent no readable time
     */
    public @Nullable ZonedDateTime time() {
        String value = timestamp;
        if (value == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * @return what was detected, named like the events the camera reports locally: the first tag such as
     *         {@code PERSON}, otherwise the type of the event such as {@code MOVEMENT}
     */
    public @Nullable String kind() {
        List<String> tags = eventTags;
        if (tags != null && !tags.isEmpty() && !tags.getFirst().isBlank()) {
            return tags.getFirst().toUpperCase(Locale.ROOT);
        }
        String type = eventType;
        return type == null ? null : type.toUpperCase(Locale.ROOT);
    }

    public ClipState clipState() {
        if (videoClipUrl != null && UPLOAD_DONE.equalsIgnoreCase(videoClipUploadStatus)) {
            return ClipState.READY;
        }
        return UPLOAD_PENDING.equalsIgnoreCase(videoClipUploadStatus) ? ClipState.PENDING : ClipState.NONE;
    }
}
