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
     * Client secret of the open source client Bosch provides for third party integrations. It is not a user secret,
     * it only identifies the client against the authorization server and is the same for every installation.
     */
    public static final String OAUTH_CLIENT_SECRET = "F1jZzsG5Ntw7x2VVc8J6qgsnisMOfaZg";

    // Bosch cloud API
    public static final String API_HOST = "residential.cbs.boschsecurity.com";
    public static final String API_BASE_URL = "https://" + API_HOST;

    // Authorization servlet
    public static final String SERVLET_PATH = "/" + BINDING_ID;

    /**
     * Path {@code my.home-assistant.io} redirects to. It appends this to the instance URL that is stored in the
     * browser, so pointing that setting at openHAB makes the authorization code arrive here automatically.
     *
     * This has to be registered as a global path rather than below {@link #SERVLET_PATH}: the settings page of
     * my.home-assistant.io keeps only protocol and host of whatever is entered
     * ({@code localStorage.hassUrl = `${url.protocol}//${url.host}`}), a path is silently dropped.
     */
    public static final String CALLBACK_PATH = "/auth/external/callback";

    /**
     * Path the forwarding page uses when the user declines. Handled as well so declining ends up on a page that says
     * so instead of a 404. Same global path constraint as {@link #CALLBACK_PATH}.
     */
    public static final String DECLINE_PATH = "/_my_redirect/oauth";

    /**
     * Page of {@code my.home-assistant.io} on which the instance URL used for the redirect can be changed.
     */
    public static final String INSTANCE_URL_SETTINGS = "https://my.home-assistant.io/redirect/_change/?redirect=oauth";

    private BoschSmartCamBindingConstants() {
    }
}
