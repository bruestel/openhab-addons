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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * An event a camera detected, as shown in its event log.
 *
 * @param time when the camera detected it
 * @param kind e.g. {@code PERSON}
 * @param clipId the clip the camera records for it, if any
 * @param recordingEnd when the camera finished that clip, {@code null} while it still records or without a clip
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record CameraEvent(Instant time, String kind, @Nullable String clipId, @Nullable Instant recordingEnd) {

    public CameraEvent withRecordingEnd(Instant end) {
        return new CameraEvent(time, kind, clipId, end);
    }
}
