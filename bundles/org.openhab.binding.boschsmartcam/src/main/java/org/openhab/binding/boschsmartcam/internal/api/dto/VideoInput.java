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

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.CLOUD_OFF;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.CLOUD_ON;

import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * A camera as returned by {@code GET /v11/video_inputs}.
 *
 * Whether the camera is reachable is not taken from here - that is what {@link CameraStatus} and the {@code /ping}
 * endpoint are for.
 *
 * @param id id of the camera, used in every other request
 * @param title the name given to the camera in the Bosch Smart Camera app
 * @param hardwareVersion model code rather than a version, see {@link CameraModel}
 * @param firmwareVersion firmware currently on the camera
 * @param privacyMode {@code ON} means the camera is switched off (shutter closed), {@code OFF} means it is recording
 * @param notificationsEnabledStatus not a plain on/off: seen so far are {@code FOLLOW_CAMERA_SCHEDULE},
 *            {@code FOLLOW_SCHEDULE}, {@code ON_CAMERA_SCHEDULE}, {@code OFF_CAMERA_SCHEDULE},
 *            {@code OFF_OVERRIDE}, {@code OFF_UNTIL} and {@code ALWAYS_OFF}. Rather than listing them,
 *            {@link #areNotificationsEnabled()} goes by {@code OFF} at the start or the end, which also covers
 *            values Bosch may add later
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record VideoInput(@Nullable String id, @Nullable String title, @Nullable String hardwareVersion,
        @Nullable String firmwareVersion, @Nullable String privacyMode, @Nullable String notificationsEnabledStatus) {

    /**
     * @return the model this camera is, or {@code null} if the binding does not know the {@code hardwareVersion}
     */
    public @Nullable CameraModel model() {
        return CameraModel.forCode(hardwareVersion);
    }

    public boolean isPrivacyModeOn() {
        return CLOUD_ON.equalsIgnoreCase(privacyMode);
    }

    /**
     * @return whether notifications are currently delivered: not for anything starting with {@code OFF}, such as
     *         {@code OFF_OVERRIDE}, nor for {@code ALWAYS_OFF}
     */
    public boolean areNotificationsEnabled() {
        String status = notificationsEnabledStatus;
        if (status == null) {
            return false;
        }
        String upper = status.toUpperCase(Locale.ROOT);
        return !upper.startsWith(CLOUD_OFF) && !upper.endsWith("_" + CLOUD_OFF);
    }
}
