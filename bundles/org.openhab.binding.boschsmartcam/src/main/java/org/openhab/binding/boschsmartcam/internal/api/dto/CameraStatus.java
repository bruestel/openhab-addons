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

    /**
     * Not reachable. Bosch answers either {@code OFFLINE} or {@code UNREACHABLE}, both end up here.
     */
    OFFLINE,

    /**
     * The camera is installing a firmware update and is not reachable while it does. Bosch distinguishes
     * {@code UPDATING_REGULAR}, {@code UPDATING_FORCED} and {@code UPDATING_APP0}, which only differ in why the
     * update runs.
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
