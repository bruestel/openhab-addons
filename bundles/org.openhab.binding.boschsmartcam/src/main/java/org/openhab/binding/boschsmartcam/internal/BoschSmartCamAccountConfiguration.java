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
     * Interval in seconds the camera settings are polled from the Bosch cloud. Changes made through openHAB are read
     * back right away, so the poll only has to pick up changes made elsewhere, e.g. in the Bosch app.
     */
    public int refreshInterval = 300;

    /**
     * Networks that may fetch the snapshot URLs, as CIDR blocks. Defaults to loopback plus the private ranges of IPv4
     * and IPv6, so the images do not leave the local network even if a link does.
     */
    public String snapshotAllowedNetworks = "127.0.0.0/8, ::1/128, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16, fc00::/7, fe80::/10";
}
