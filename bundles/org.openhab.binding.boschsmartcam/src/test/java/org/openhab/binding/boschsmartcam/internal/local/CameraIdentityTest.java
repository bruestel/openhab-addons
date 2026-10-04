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
package org.openhab.binding.boschsmartcam.internal.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests how MAC addresses and firmware versions are brought into shape.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class CameraIdentityTest {

    @Test
    public void macAddressFromCertificateAndCloudLooksTheSame() {
        assertEquals("64:da:a0:12:34:56", CameraIdentity.normalizeMacAddress("64-da-a0-12-34-56"));
        assertEquals("64:da:a0:12:34:56", CameraIdentity.normalizeMacAddress("64:DA:A0:12:34:56"));
        assertEquals("64:da:a0:12:34:56", CameraIdentity.normalizeMacAddress("64daa0123456"));
    }

    @Test
    public void noMacAddressIsRejected() {
        assertNull(CameraIdentity.normalizeMacAddress(null));
        assertNull(CameraIdentity.normalizeMacAddress("404000257314030202"));
        assertNull(CameraIdentity.normalizeMacAddress("64-da-a0-12-34"));
    }

    @Test
    public void thingIdIsTheMacAddressWithoutSeparators() {
        assertEquals("64daa0123456", new CameraIdentity("64:da:a0:12:34:56", null).thingId());
    }

    @Test
    public void firmwareVersionLosesThePadding() {
        assertEquals("9.40.202", LocalCameraClient.withoutLeadingZeros("9.40.0202"));
        assertEquals("9.40.104", LocalCameraClient.withoutLeadingZeros("9.40.104"));
        assertEquals("10.0.0", LocalCameraClient.withoutLeadingZeros("10.00.000"));
    }
}
