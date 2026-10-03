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
 * The {@link BoschSmartCamCameraConfiguration} holds the configuration of a camera thing.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamCameraConfiguration {

    /**
     * Address of the camera in the local network.
     */
    public String host = "";

    /**
     * User of the local API. The app shows it together with the password once the local data interface is enabled.
     */
    public String user = BoschSmartCamBindingConstants.DEFAULT_LOCAL_USER;

    public String password = "";

    /**
     * How long a fetched still image is reused before the camera is asked again. Whoever opens the snapshot URL is
     * served from that cache, so the number of viewers does not matter.
     */
    public int snapshotCacheSeconds = 3;

    /**
     * Offer the events the cloud keeps for this camera, with their images and clips, as a small JSON API below the
     * snapshot address. Needs an account.
     */
    public boolean publishEventsApi = false;

    /**
     * Accept any certificate instead of only those below the root Bosch publishes. A way out should a firmware update
     * ever bring a new root.
     */
    public boolean trustAllCertificates = false;
}
