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
import org.openhab.binding.boschsmartcam.internal.local.CameraIdentity;

/**
 * Answer of {@code GET /v11/video_inputs/{id}/wifiinfo}, e.g.
 * {@code {"ssid":"home","signalStrength":100,"ipAddress":"192.168.0.42","macAddress":"64-da-a0-12-34-56"}}.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record WifiInfo(@Nullable String macAddress, @Nullable String ipAddress, @Nullable String ssid,
        @Nullable Integer signalStrength) {

    /**
     * @return the MAC address in the form {@code 64:da:a0:12:34:56} or {@code null} if the answer carried none
     */
    public @Nullable String normalizedMacAddress() {
        return CameraIdentity.normalizeMacAddress(macAddress);
    }
}
