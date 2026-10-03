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
package org.openhab.binding.boschsmartcam.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link BoschSmartCamAccountConfiguration} holds the configuration of the account bridge.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamAccountConfiguration {

    /**
     * Interval in seconds the account is polled. Commands sent from openHAB are read back right away, and everything
     * the cameras report themselves arrives locally, so the poll only picks up what is changed in the Bosch Smart
     * Camera app:
     * the notifications, and cameras added to or removed from the account.
     */
    public int refreshInterval = 3600;
}
