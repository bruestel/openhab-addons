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
import org.openhab.core.thing.type.ChannelGroupTypeUID;

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

    // Channel groups: what a camera reports itself, and per camera on the account what belongs to the user
    public static final String GROUP_LOCAL = "local";
    public static final ChannelGroupTypeUID GROUP_TYPE_NOTIFICATIONS = new ChannelGroupTypeUID(BINDING_ID,
            "notifications");

    // List of all Channel ids, without their group
    public static final String CHANNEL_PRIVACY_MODE = "privacy-mode";
    public static final String CHANNEL_SNAPSHOT_URL = "snapshot-url";
    public static final String CHANNEL_HLS_URL = "hls-url";
    public static final String CHANNEL_EVENT = "event";
    public static final String CHANNEL_LAST_EVENT = "last-event";
    public static final String CHANNEL_LAST_EVENT_TIME = "last-event-time";
    public static final String CHANNEL_RECORDING = "recording";
    public static final String CHANNEL_NOTIFICATIONS = "notifications";
    public static final String CHANNEL_NOTIFICATIONS_STATUS = "notifications-status";

    // Thing properties beyond the ones openHAB defines itself
    public static final String PROPERTY_CAMERA_ID = "cameraId";
    public static final String PROPERTY_PRODUCT_NAME = "productName";
    public static final String PROPERTY_GENERATION = "generation";

    /**
     * Address of the page with the event log of a camera.
     */
    public static final String PROPERTY_EVENTS_PAGE = "eventsPage";

    /**
     * Name of the user the local API creates when it is enabled in the app.
     */
    public static final String DEFAULT_LOCAL_USER = "localuser";

    // Configuration parameters of a camera
    public static final String CONFIG_HOST = "host";

    /**
     * Port of the local API, open on every camera.
     */
    public static final int HTTPS_PORT = 443;

    /**
     * Port of the RTSP tunnel, open only once the local API is enabled in the app.
     */
    public static final int RTSP_PORT = 9554;

    /**
     * Unguessable token that guards the URLs of a camera. Kept as a property so links survive restarts, and so it can
     * be looked up or deleted when a link has to be revoked.
     */
    public static final String PROPERTY_ACCESS_TOKEN = "accessToken";

    // Bosch SingleKey ID (Keycloak) endpoints
    public static final String AUTH_BASE_URL = "https://smarthome.authz.bosch.com/auth/realms/home_auth_provider/protocol/openid-connect";
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
     * Last part of a snapshot URL, which reads {@code /boschsmartcam/<token>/snapshot.jpg}.
     */
    public static final String SNAPSHOT_FILE = "snapshot.jpg";

    /**
     * Page with the event log of a camera, and the stream it follows new events with. Same path and protection as
     * {@link #SNAPSHOT_FILE}.
     */
    public static final String EVENTS_PAGE_FILE = "events.html";
    public static final String EVENTS_STREAM_FILE = "events.stream";

    /**
     * Ten seconds of raw H.264 of the camera. A check of the stream receiver while there is no HLS yet.
     */
    public static final String RAW_VIDEO_FILE = "stream.h264";

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
