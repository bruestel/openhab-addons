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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A camera as returned by {@code GET /v11/video_inputs}.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class VideoInput {

    public @Nullable String id;
    public @Nullable String title;

    /**
     * Connection state of the camera, e.g. {@code ONLINE} or {@code OFFLINE}.
     */
    public @Nullable String status;

    public @Nullable String hardwareVersion;
    public @Nullable String firmwareVersion;

    /**
     * {@code ON} means the camera is switched off (shutter closed), {@code OFF} means it is recording.
     */
    public @Nullable String privacyMode;

    /**
     * {@code FOLLOW_CAMERA_SCHEDULE} and {@code ON_CAMERA_SCHEDULE} mean notifications are on, {@code ALWAYS_OFF}
     * means they are off.
     */
    public @Nullable String notificationsEnabledStatus;

    public boolean isPrivacyModeOn() {
        return "ON".equalsIgnoreCase(privacyMode);
    }

    public boolean areNotificationsEnabled() {
        String status = notificationsEnabledStatus;
        return status != null && !"ALWAYS_OFF".equalsIgnoreCase(status);
    }

    public boolean isOnline() {
        return "ONLINE".equalsIgnoreCase(status);
    }
}
