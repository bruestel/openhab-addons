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

import java.util.StringJoiner;

import org.openhab.binding.homeconnectdirect.internal.service.profile.model.Access;

public class Status {
    public final int uid;
    public final Access access;
    public final boolean available;
    public final Integer min;
    public final Integer max;
    public final Integer stepSize;
    public final Integer enumerationType;

    public Status(int uid, Access access, boolean available, Integer min, Integer max, Integer stepSize,
            Integer enumerationType) {
        this.uid = uid;
        this.access = access;
        this.available = available;
        this.min = min;
        this.max = max;
        this.stepSize = stepSize;
        this.enumerationType = enumerationType;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Status.class.getSimpleName() + "[", "]").add("uid=" + uid).add("access=" + access)
                .add("available=" + available).add("min=" + min).add("max=" + max).add("stepSize=" + stepSize)
                .add("enumerationType=" + enumerationType).toString();
    }
}
