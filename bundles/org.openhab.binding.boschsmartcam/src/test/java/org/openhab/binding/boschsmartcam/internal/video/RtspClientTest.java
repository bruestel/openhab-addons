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
package org.openhab.binding.boschsmartcam.internal.video;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests the digest the RTSP client answers authentication challenges with.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class RtspClientTest {

    @Test
    public void md5MatchesTheKnownValue() {
        // RFC 1321, test suite
        assertEquals("900150983cd24fb0d6963f7d28e17f72", RtspClient.md5("abc"));
    }
}
