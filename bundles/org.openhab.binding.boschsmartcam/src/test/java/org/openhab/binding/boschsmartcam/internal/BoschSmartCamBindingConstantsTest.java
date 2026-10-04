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

import static org.junit.jupiter.api.Assertions.*;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests the constants openHAB puts limits on.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamBindingConstantsTest {

    @Test
    public void httpClientNamesAreShortEnoughForOpenhab() {
        // a longer name made the camera fail to initialize with trustAllCertificates
        assertTrue(CAMERA_HTTP_CLIENT_NAME.length() <= MAX_HTTP_CLIENT_NAME_LENGTH);
        assertTrue(TRUST_ALL_HTTP_CLIENT_NAME.length() <= MAX_HTTP_CLIENT_NAME_LENGTH);
    }
}
