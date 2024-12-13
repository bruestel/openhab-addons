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

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_COFFEE_MAKER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_COOK_PROCESSOR;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_DISHWASHER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_DRYER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_OVEN;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_WASHER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.APPLIANCE_TYPE_WASHER_AND_DRYER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CONFIGURATION_PID;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.openhab.binding.homeconnectdirect.internal.configuration.HomeConnectDirectServletConfiguration;
import org.openhab.core.thing.ThingStatus;
import org.osgi.service.cm.ConfigurationAdmin;

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

    public void checkAuthorization(HttpServletRequest request, HttpServletResponse response, String adminUsername,
            String adminPassword) throws IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"Home Connect Console\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } else {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
            String[] values = credentials.split(":", 2);
            String username = values[0];
            String password = values[1];

            if (!Objects.equals(adminUsername, username) || !Objects.equals(adminPassword, password)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
        }
    }

    public void checkAuthorization(ServletUpgradeRequest request, ServletUpgradeResponse response, String adminUsername,
            String adminPassword) throws IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"Home Connect Console\"");
            response.sendForbidden("Forbidden");
        } else {
            String base64Credentials = authHeader.substring("Basic ".length()).trim();
            String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);
            String[] values = credentials.split(":", 2);
            String username = values[0];
            String password = values[1];

            if (!Objects.equals(adminUsername, username) || !Objects.equals(adminPassword, password)) {
                response.sendForbidden("Forbidden");
            }
        }
    }

    public HomeConnectDirectServletConfiguration getConfiguration(ConfigurationAdmin configurationAdmin) {
        var configuration = new HomeConnectDirectServletConfiguration();
        try {
            var config = configurationAdmin.getConfiguration(CONFIGURATION_PID);
            var properties = config.getProperties();

            if (properties != null) {
                configuration.basicAuthEnabled = Boolean.parseBoolean(properties.get("basicAuthEnabled") + "");
                var usernameObject = properties.get("basicAuthUsername");
                var passwordObject = properties.get("basicAuthPassword");
                if (usernameObject instanceof String username && passwordObject instanceof String password
                        && isNotBlank(username) && isNotBlank(password)) {
                    configuration.basicAuthUsername = username;
                    configuration.basicAuthPassword = password;
                }
            }
        } catch (IOException ignored) {
        }

        return configuration;
    }
}
