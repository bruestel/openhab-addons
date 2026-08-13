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

/**
 * Body of {@code PUT /v11/video_inputs/{id}/enable_notifications}. Note that the field is named differently than the
 * one returned by {@code GET /v11/video_inputs}.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class NotificationsRequest {

    public String enabledNotificationsStatus;

    public NotificationsRequest(boolean enabled) {
        this.enabledNotificationsStatus = enabled ? "FOLLOW_CAMERA_SCHEDULE" : "ALWAYS_OFF";
    }
}
