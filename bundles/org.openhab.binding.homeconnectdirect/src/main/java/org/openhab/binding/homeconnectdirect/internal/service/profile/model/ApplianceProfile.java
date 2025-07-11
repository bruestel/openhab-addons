/**
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.homeconnectdirect.internal.service.profile.model;

import java.time.OffsetDateTime;

public record ApplianceProfile(String haId, String type, String serialNumber, String brand, String vib, String mac,
        ConnectionType connectionType, String key, String iv, String featureMappingFileName,
        String deviceDescriptionFileName, OffsetDateTime created) {
}
