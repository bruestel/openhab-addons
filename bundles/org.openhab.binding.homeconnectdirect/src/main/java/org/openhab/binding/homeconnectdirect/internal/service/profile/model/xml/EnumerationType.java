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
import java.util.StringJoiner;

public class EnumerationType {
    public final int id;
    public final Integer subsetOf;
    public final List<Integer> values;

    public EnumerationType(int id, Integer subsetOf, List<Integer> values) {
        this.id = id;
        this.subsetOf = subsetOf;
        this.values = values;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", EnumerationType.class.getSimpleName() + "[", "]").add("id=" + id)
                .add("values=" + values).add("subsetOf=" + subsetOf).toString();
    }
}
