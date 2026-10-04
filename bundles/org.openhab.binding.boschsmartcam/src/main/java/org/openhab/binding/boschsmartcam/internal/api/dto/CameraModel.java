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
 * The camera models behind a model code, which the camera names as {@code Model} in its ONVIF device information and
 * the cloud as {@code hardwareVersion} of a {@link VideoInput}. Despite its name that field does not carry a version.
 *
 * The product names are the ones Bosch sells the cameras under. Only the second generation is listed, the first lacks
 * the local API the binding is built on.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public enum CameraModel {

    EYES_INDOOR_II("HOME_Eyes_Indoor", "Eyes Indoor Camera II"),
    EYES_OUTDOOR_II("HOME_Eyes_Outdoor", "Eyes Outdoor Camera II");

    private final String code;
    private final String productName;

    CameraModel(String code, String productName) {
        this.code = code;
        this.productName = productName;
    }

    public String getProductName() {
        return productName;
    }

    /**
     * @param code the model code of a camera, e.g. {@code HOME_Eyes_Outdoor}
     * @return the matching model or {@code null} for a model this binding does not know yet
     */
    public static @Nullable CameraModel forCode(@Nullable String code) {
        if (code != null) {
            for (CameraModel model : values()) {
                if (model.code.equalsIgnoreCase(code)) {
                    return model;
                }
            }
        }
        return null;
    }
}
