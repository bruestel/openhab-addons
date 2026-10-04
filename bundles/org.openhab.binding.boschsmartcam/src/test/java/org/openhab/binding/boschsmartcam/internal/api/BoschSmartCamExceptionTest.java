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

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests that the codes of the Bosch cloud are told apart from faults.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamExceptionTest {

    @Test
    public void codesOfTheBoschCloudAreExpected() {
        BoschSmartCamException privacy = new BoschSmartCamException("PUT … failed with HTTP 443", 443);
        assertTrue(privacy.isExpected());
        assertFalse(privacy.isRateLimited());
        assertEquals("the camera is in privacy mode", privacy.getReason());
        assertTrue(new BoschSmartCamException("quota", 444).isRateLimited());
    }

    @Test
    public void otherFailuresKeepTheirMessage() {
        BoschSmartCamException failure = new BoschSmartCamException("GET … failed with HTTP 500", 500);
        assertFalse(failure.isExpected());
        assertEquals("GET … failed with HTTP 500", failure.getReason());
    }
}
