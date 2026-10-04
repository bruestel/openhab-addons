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
package org.openhab.binding.boschsmartcam.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Supplies a valid OAuth access token to the {@link BoschSmartCamApi}.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
@FunctionalInterface
public interface AccessTokenProvider {

    /**
     * Returns an access token that is valid at the time of the call, refreshing it if needed.
     *
     * @throws BoschSmartCamException if no token is available, e.g. because the account is not authorized (yet)
     */
    String getAccessToken() throws BoschSmartCamException;
}
