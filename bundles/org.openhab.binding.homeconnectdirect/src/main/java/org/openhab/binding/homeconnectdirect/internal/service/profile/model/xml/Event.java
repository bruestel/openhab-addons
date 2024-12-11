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

import org.openhab.binding.homeconnectdirect.internal.service.profile.model.Handling;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.Level;

public class Event {
    public final int uid;
    public final Level level;
    public final Handling handling;
    public final Integer enumerationType;

    public Event(int uid, Level level, Handling handling, Integer enumerationType) {
        this.uid = uid;
        this.level = level;
        this.handling = handling;
        this.enumerationType = enumerationType;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", Event.class.getSimpleName() + "[", "]").add("uid=" + uid).add("level=" + level)
                .add("handling=" + handling).add("enumerationType=" + enumerationType).toString();
    }
}
