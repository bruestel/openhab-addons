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
 * Answer of {@code GET /v11/video_inputs/{id}/commissioned}, used to tell whether a camera is reachable when
 * {@code /ping} does not answer.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record Commissioned(@Nullable Boolean connected, @Nullable Boolean commissioned, @Nullable Boolean configured) {

    public boolean isReachable() {
        return Boolean.TRUE.equals(connected) && Boolean.TRUE.equals(commissioned);
    }

    public boolean isConfigured() {
        return Boolean.TRUE.equals(configured);
    }
}
