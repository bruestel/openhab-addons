/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.boschsmartcam.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link BoschSmartCamBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamBindingConstants {

    public static final String BINDING_ID = "boschsmartcam";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ACCOUNT = new ThingTypeUID(BINDING_ID, "account");
    public static final ThingTypeUID THING_TYPE_CAMERA = new ThingTypeUID(BINDING_ID, "camera");

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_ACCOUNT, THING_TYPE_CAMERA);

    // List of all Channel ids
    public static final String CHANNEL_PRIVACY_MODE = "privacy-mode";
    public static final String CHANNEL_NOTIFICATIONS = "notifications";
    public static final String CHANNEL_STATUS = "status";

    // Configuration parameters
    public static final String CONFIG_CAMERA_ID = "cameraId";

    // Bosch SingleKey ID (Keycloak) endpoints
    private static final String AUTH_BASE_URL = "https://smarthome.authz.bosch.com/auth/realms/home_auth_provider/protocol/openid-connect";
    public static final String OAUTH_AUTHORIZE_URL = AUTH_BASE_URL + "/auth";
    public static final String OAUTH_TOKEN_URL = AUTH_BASE_URL + "/token";
    public static final String OAUTH_CLIENT_ID = "oss_residential_app";
    public static final String OAUTH_SCOPE = "email offline_access profile openid";

    /**
     * The only redirect URI accepted by the Bosch authorization server for this client. Every other value - including
     * an openHAB URL - is rejected with {@code invalid redirect_uri}, which is why the authorization code has to be
     * transferred back to openHAB manually.
     */
    public static final String OAUTH_REDIRECT_URI = "https://my.home-assistant.io/redirect/oauth";

    /**
     * Client secret of the residential app. This is not a user secret, it only identifies the app against the
     * authorization server, but it is not published by Bosch either, so it has to be supplied by the installation.
     * Either fill it in here at build time or set the {@code clientSecret} parameter on the account thing.
     */
    public static final String OAUTH_CLIENT_SECRET = "";

    // Bosch cloud API
    public static final String API_HOST = "residential.cbs.boschsecurity.com";
    public static final String API_BASE_URL = "https://" + API_HOST;

    // Authorization servlet
    public static final String SERVLET_PATH = "/" + BINDING_ID;

    private BoschSmartCamBindingConstants() {
    }
}
