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

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Answer of {@code PUT /v11/video_inputs/{id}/connection}, the credentials to talk to the camera directly.
 *
 * Bosch hands out a fresh pair on every call and stops accepting the previous one for new connections after about a
 * minute, so they are worth caching but not for long.
 *
 * @param user digest user, looks like {@code cbs-XXXXXXXX}
 * @param password digest password
 * @param urls addresses of the camera in the local network, e.g. {@code 192.168.0.42:443}
 * @param bufferingTime buffering time in milliseconds the camera suggests, unused here
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record LocalConnection(@Nullable String user, @Nullable String password, @Nullable List<String> urls,
        @Nullable Integer bufferingTime) {

    /**
     * @return the first address of the camera or {@code null} if the answer carried none
     */
    public @Nullable String host() {
        List<String> localUrls = urls;
        if (localUrls == null || localUrls.isEmpty()) {
            return null;
        }
        return localUrls.getFirst();
    }

    public boolean isUsable() {
        return user != null && password != null && host() != null;
    }
}
