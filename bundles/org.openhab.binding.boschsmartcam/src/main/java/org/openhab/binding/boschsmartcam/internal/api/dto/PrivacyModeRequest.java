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
 * Body of {@code PUT /v11/video_inputs/{id}/privacy}.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class PrivacyModeRequest {

    public String privacyMode;

    /**
     * Time after which the camera switches itself on again. {@code null} keeps privacy mode until it is switched off,
     * which is what the official app sends.
     */
    public @Nullable Integer durationInSeconds;

    public PrivacyModeRequest(boolean privacyModeOn, @Nullable Integer durationInSeconds) {
        this.privacyMode = privacyModeOn ? "ON" : "OFF";
        this.durationInSeconds = durationInSeconds;
    }
}
