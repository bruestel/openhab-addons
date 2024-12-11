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
package org.openhab.binding.homeconnectdirect.internal.service.profile.converter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.EnumDescription;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.FeatureMapping;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

public class FeatureMappingConverter implements Converter {

    @Override
    @SuppressWarnings("rawtypes")
    public boolean canConvert(Class type) {
        return type.equals(FeatureMapping.class);
    }

    @Override
    public void marshal(Object o, HierarchicalStreamWriter hierarchicalStreamWriter,
            MarshallingContext marshallingContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {

        Map<Integer, String> featureMap = new HashMap<>();
        Map<Integer, String> errorMap = new HashMap<>();
        List<EnumDescription> enums = new ArrayList<>();

        read(reader, featureMap, errorMap, enums);
        return new FeatureMapping(featureMap, errorMap, enums);
    }

    private void read(HierarchicalStreamReader reader, Map<Integer, String> featureMap, Map<Integer, String> errorMap,
            List<EnumDescription> enums) {
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            String nodeName = reader.getNodeName();

            if ("featureDescription".equals(nodeName)) {
                read(reader, featureMap, errorMap, enums);
            } else if ("feature".equals(nodeName)) {
                var uid = mapHexIdString(reader.getAttribute("refUID"));
                var value = reader.getValue();
                featureMap.put(uid, value);
            } else if ("errorDescription".equals(nodeName)) {
                read(reader, featureMap, errorMap, enums);
            } else if ("error".equals(nodeName)) {
                var id = mapHexIdString(reader.getAttribute("refEID"));
                var value = reader.getValue();
                errorMap.put(id, value);
            } else if ("enumDescriptionList".equals(nodeName)) {
                read(reader, featureMap, errorMap, enums);
            } else if ("enumDescription".equals(nodeName)) {
                var id = mapHexIdString(reader.getAttribute("refENID"));
                var key = reader.getAttribute("enumKey");
                var values = new HashMap<Integer, String>();

                while (reader.hasMoreChildren()) {
                    reader.moveDown();
                    if ("enumMember".equals(reader.getNodeName())) {
                        var enumValue = mapInteger(reader.getAttribute("refValue"));
                        var mappingValue = reader.getValue();
                        values.put(enumValue, mappingValue);
                    }
                    reader.moveUp();
                }

                enums.add(new EnumDescription(id, key, values));
            }

            reader.moveUp();
        }
    }

    private @Nullable Integer mapInteger(@Nullable String integer) {
        if (integer == null) {
            return null;
        }

        try {
            return Integer.parseInt(integer);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int mapHexIdString(String hexIdString) {
        try {
            return Integer.parseInt(hexIdString, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
