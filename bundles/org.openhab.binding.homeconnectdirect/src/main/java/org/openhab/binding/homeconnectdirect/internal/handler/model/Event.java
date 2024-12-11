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
package org.openhab.binding.homeconnectdirect.internal.handler.model;

public record Event(String name, Object value) {

    public int getValueAsInt() {
        try {
            if (value instanceof Number number) {
                return number.intValue();
            } else {
                return Double.valueOf(value.toString()).intValue();
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public boolean getValueAsBoolean() {
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public String getValueAsString() {
        return String.valueOf(value);
    }
}
