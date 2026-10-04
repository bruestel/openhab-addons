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

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Tests how the notification setting of the cloud is read as on or off.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class VideoInputTest {

    @Test
    public void settingsThatDeliverNotificationsAreOn() {
        assertTrue(withStatus("FOLLOW_CAMERA_SCHEDULE").areNotificationsEnabled());
        assertTrue(withStatus("FOLLOW_SCHEDULE").areNotificationsEnabled());
        assertTrue(withStatus("ON_CAMERA_SCHEDULE").areNotificationsEnabled());
    }

    @Test
    public void settingsThatSuppressNotificationsAreOff() {
        // ALWAYS_OFF is what the binding writes; the cloud may report it as such
        assertFalse(withStatus("ALWAYS_OFF").areNotificationsEnabled());
        assertFalse(withStatus("OFF_OVERRIDE").areNotificationsEnabled());
        assertFalse(withStatus("OFF_UNTIL").areNotificationsEnabled());
        assertFalse(withStatus("OFF_CAMERA_SCHEDULE").areNotificationsEnabled());
        assertFalse(withStatus(null).areNotificationsEnabled());
    }

    private static VideoInput withStatus(@Nullable String status) {
        return new VideoInput("id", "Garden", "HOME_Eyes_Outdoor", "9.40.202", "OFF", status);
    }
}
