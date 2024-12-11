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

public class Program {
    public final int uid;
    public final boolean available;
    public final List<Integer> optionUidList;

    public Program(int uid, boolean available, List<Integer> optionUidList) {
        this.uid = uid;
        this.available = available;
        this.optionUidList = optionUidList;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Program.class.getSimpleName() + "[", "]").add("uid=" + uid)
                .add("available=" + available).add("optionUidList=" + optionUidList).toString();
    }
}
