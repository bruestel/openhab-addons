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
package org.openhab.binding.homeconnectdirect.internal.servlet;

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_COFFEE_MAKER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_COOK_PROCESSOR;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_DISHWASHER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_DRYER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_OVEN;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_WASHER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_WASHER_AND_DRYER;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingStatus;

/**
 *
 * Home Connect Direct servlet utilities collection.
 *
 * @author Jonas Brüstel - Initial Contribution
 */
@NonNullByDefault
public class ServletUtils {
    public String getLabelByType(String type) {
        if (APPLIANCE_TYPE_COFFEE_MAKER.equalsIgnoreCase(type)) {
            return "Coffee Maker";
        } else if (APPLIANCE_TYPE_WASHER.equalsIgnoreCase(type)) {
            return "Washer";
        } else if (APPLIANCE_TYPE_WASHER_AND_DRYER.equalsIgnoreCase(type)) {
            return "Washer and Dryer";
        } else if (APPLIANCE_TYPE_DRYER.equalsIgnoreCase(type)) {
            return "Dryer";
        } else if (APPLIANCE_TYPE_COOK_PROCESSOR.equalsIgnoreCase(type)) {
            return "Cook Processor";
        } else if (APPLIANCE_TYPE_DISHWASHER.equalsIgnoreCase(type)) {
            return "Dishwasher";
        } else if (APPLIANCE_TYPE_OVEN.equalsIgnoreCase(type)) {
            return "Oven";
        } else {
            return "Generic Appliance";
        }
    }

    public boolean isWasher(String type) {
        return APPLIANCE_TYPE_WASHER.equalsIgnoreCase(type) || APPLIANCE_TYPE_WASHER_AND_DRYER.equalsIgnoreCase(type);
    }

    public boolean isDryer(String type) {
        return APPLIANCE_TYPE_DRYER.equalsIgnoreCase(type);
    }

    public boolean isCoffeeMaker(String type) {
        return APPLIANCE_TYPE_COFFEE_MAKER.equalsIgnoreCase(type);
    }

    public boolean isDishwasher(String type) {
        return APPLIANCE_TYPE_DISHWASHER.equalsIgnoreCase(type);
    }

    public boolean isCookProcessor(String type) {
        return APPLIANCE_TYPE_COOK_PROCESSOR.equalsIgnoreCase(type);
    }

    public boolean isOven(String type) {
        return APPLIANCE_TYPE_OVEN.equalsIgnoreCase(type);
    }

    public boolean isGeneric(String type) {
        return !(isWasher(type) || !isDryer(type) || !isOven(type) || isCoffeeMaker(type) || isDishwasher(type)
                || isCookProcessor(type));
    }

    public String formatDateTime(OffsetDateTime offsetDateTime) {
        var formatter = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm:ss a z", Locale.ENGLISH);
        return formatter.format(offsetDateTime.atZoneSameInstant(ZoneId.systemDefault()));
    }

    public String getBadgeStyle(ThingStatus status) {
        return switch (status) {
            case ONLINE -> "text-bg-success";
            case OFFLINE -> "text-bg-danger";
            case INITIALIZING, UNINITIALIZED -> "text-bg-secondary";
            default -> "text-bg-warning";
        };
    }
}
