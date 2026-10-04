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

import java.time.Instant;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * One notification the camera delivered through its PullPoint subscription.
 *
 * @param topic the topic below {@code tns1:BoschSmartHome/}, e.g. {@code PersonDetected}
 * @param time when the camera created the message
 * @param propertyOperation {@code Initialized}, {@code Changed} or {@code Deleted} for a property, {@code null} for
 *            an event
 * @param data the {@code Data} items, e.g. {@code State}, {@code ClipId} or {@code Reason}
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public record OnvifEvent(String topic, @Nullable Instant time, @Nullable String propertyOperation,
        Map<String, String> data) {

    public @Nullable String get(String name) {
        return data.get(name);
    }

    /**
     * @return whether the item is {@code true}, as the camera writes booleans as text
     */
    public boolean isTrue(String name) {
        return "true".equalsIgnoreCase(data.get(name));
    }
}
