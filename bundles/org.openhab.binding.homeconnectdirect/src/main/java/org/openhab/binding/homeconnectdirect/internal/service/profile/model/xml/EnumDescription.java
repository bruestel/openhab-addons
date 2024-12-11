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

import java.util.Map;
import java.util.StringJoiner;

public class EnumDescription {
    public final int id;
    public final String key;
    public final Map<Integer, String> values;

    public EnumDescription(int id, String key, Map<Integer, String> values) {
        this.id = id;
        this.key = key;
        this.values = Map.copyOf(values);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", EnumDescription.class.getSimpleName() + "[", "]").add("id=" + id)
                .add("key='" + key + "'").add("values=" + values).toString();
    }
}
