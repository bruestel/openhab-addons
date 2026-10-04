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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Body of {@code PUT /v11/video_inputs/{id}/privacy}.
 *
 * @param privacyMode {@code ON} switches the camera off, {@code OFF} switches it on
 * @param durationInSeconds time after which the camera switches itself on again, {@code null} keeps privacy mode
 *            until it is switched off, which is what the official app sends
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record PrivacyModeRequest(String privacyMode, @Nullable Integer durationInSeconds) {

    public static PrivacyModeRequest of(boolean privacyModeOn, @Nullable Integer durationInSeconds) {
        return new PrivacyModeRequest(privacyModeOn ? CLOUD_ON : CLOUD_OFF, durationInSeconds);
    }
}
