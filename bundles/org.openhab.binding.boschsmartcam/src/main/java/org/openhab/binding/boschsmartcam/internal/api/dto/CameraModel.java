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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The camera models behind the {@code hardwareVersion} of a {@link VideoInput}. Despite its name that field does not
 * carry a version but a model code.
 *
 * The product names are the ones Bosch sells the cameras under. The generation matters because the two differ in the
 * endpoints they support.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public enum CameraModel {

    INDOOR("INDOOR", "360° Innenkamera", 1),
    OUTDOOR("OUTDOOR", "Eyes Außenkamera", 1),
    EYES_INDOOR_II("HOME_Eyes_Indoor", "Eyes Innenkamera II", 2),
    EYES_OUTDOOR_II("HOME_Eyes_Outdoor", "Eyes Außenkamera II", 2);

    private final String hardwareVersion;
    private final String productName;
    private final int generation;

    CameraModel(String hardwareVersion, String productName, int generation) {
        this.hardwareVersion = hardwareVersion;
        this.productName = productName;
        this.generation = generation;
    }

    public String getProductName() {
        return productName;
    }

    public int getGeneration() {
        return generation;
    }

    /**
     * @param hardwareVersion the {@code hardwareVersion} of a camera
     * @return the matching model or {@code null} for a model this binding does not know yet
     */
    public static @Nullable CameraModel forHardwareVersion(@Nullable String hardwareVersion) {
        if (hardwareVersion != null) {
            for (CameraModel model : values()) {
                if (model.hardwareVersion.equalsIgnoreCase(hardwareVersion)) {
                    return model;
                }
            }
        }
        return null;
    }
}
