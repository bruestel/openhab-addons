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
 * How one light of the Eyes Outdoor Camera II shines right now: the front light, or the top or bottom LEDs. Either a
 * color, e.g. {@code #ff4078}, or a white balance from -1 (warm) to 1 (cold) is set, never both. The camera reports
 * these at {@code /sh/data/lighting/manual}.
 *
 * @param brightness 0 to 100, 0 is off
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record LightSettings(@Nullable Integer brightness, @Nullable String color, @Nullable Double whiteBalance) {

    public int brightnessOrZero() {
        Integer value = brightness;
        return value == null ? 0 : value;
    }
}
