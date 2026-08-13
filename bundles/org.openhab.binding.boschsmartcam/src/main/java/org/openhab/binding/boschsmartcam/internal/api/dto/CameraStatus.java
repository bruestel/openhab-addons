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
 * Reachability of a camera as reported by {@code /ping} and {@code /commissioned}.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public enum CameraStatus {

    ONLINE,
    OFFLINE,

    /**
     * The camera is installing a firmware update and is not reachable while it does.
     */
    UPDATING,

    /**
     * Bosch refused the request because too many live sessions are open at once - counted across every client of the
     * account, so the app or another integration can cause this. Says nothing about the camera itself.
     */
    SESSION_LIMIT,

    /**
     * Neither endpoint gave a usable answer.
     */
    UNKNOWN
}
