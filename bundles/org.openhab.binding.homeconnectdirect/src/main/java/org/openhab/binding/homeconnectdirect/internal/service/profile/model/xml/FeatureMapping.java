/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.eclipse.jdt.annotation.NonNullByDefault;

@NonNullByDefault
public class FeatureMapping {
    public final Map<Integer, String> featureMap;
    public final Map<Integer, String> errorMap;
    public final List<EnumDescription> enumDescriptionList;

    public FeatureMapping(Map<Integer, String> featureMap, Map<Integer, String> errorMap,
            List<EnumDescription> enumDescriptionList) {
        this.featureMap = Map.copyOf(featureMap);
        this.errorMap = Map.copyOf(errorMap);
        this.enumDescriptionList = List.copyOf(enumDescriptionList);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", FeatureMapping.class.getSimpleName() + "[", "]").add("featureMap=" + featureMap)
                .add("errorMap=" + errorMap).add("enumDescriptionList=" + enumDescriptionList).toString();
    }
}
