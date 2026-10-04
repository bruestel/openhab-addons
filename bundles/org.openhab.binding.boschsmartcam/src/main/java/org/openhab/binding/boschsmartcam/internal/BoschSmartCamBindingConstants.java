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
    public static final String GROUP_CLOUD = "cloud";
    public static final String GROUP_LIGHT = "light";
    public static final String GROUP_ALARM = "alarm";
    public static final ChannelGroupTypeUID GROUP_TYPE_LIGHT = new ChannelGroupTypeUID(BINDING_ID, GROUP_LIGHT);
    public static final ChannelGroupTypeUID GROUP_TYPE_NOTIFICATIONS = new ChannelGroupTypeUID(BINDING_ID,
            "notifications");

    // List of all Channel ids, without their group
    public static final String CHANNEL_PRIVACY_MODE = "privacy-mode";
    public static final String CHANNEL_SNAPSHOT_URL = "snapshot-url";
    public static final String CHANNEL_RTSP_URL = "rtsp-url";
    public static final String CHANNEL_RTSP_SUBSTREAM_URL = "rtsp-substream-url";
    public static final String CHANNEL_RTSPS_URL = "rtsps-url";
    public static final String CHANNEL_RTSPS_SUBSTREAM_URL = "rtsps-substream-url";
    public static final String CHANNEL_CAMERA_RTSPS_URL = "camera-rtsps-url";
    public static final String CHANNEL_EVENT = "event";
    public static final String CHANNEL_LAST_EVENT = "last-event";
    public static final String CHANNEL_LAST_EVENT_TIME = "last-event-time";
    public static final String CHANNEL_RECORDING = "recording";
    public static final String CHANNEL_LAST_CLIP_SNAPSHOT_URL = "last-clip-snapshot-url";
    public static final String CHANNEL_LAST_CLIP_URL = "last-clip-url";
    public static final String CHANNEL_CLIP_READY = "clip-ready";
    public static final String CHANNEL_FRONT_LIGHT = "front-light";
    public static final String CHANNEL_TOP_BOTTOM_LIGHT = "top-bottom-light";
    public static final String CHANNEL_MOTION_LIGHT = "motion-light";
    public static final String CHANNEL_SIREN = "siren";
    public static final String CHANNEL_NOTIFICATIONS = "notifications";
    public static final String CHANNEL_NOTIFICATIONS_STATUS = "notifications-status";

    // Thing properties beyond the ones openHAB defines itself
    public static final String PROPERTY_CAMERA_ID = "cameraId";
    public static final String PROPERTY_RTSPS_CERTIFICATE = "rtspsCertificate";
    public static final String PROPERTY_RTSPS_CERTIFICATE_SHA256 = "rtspsCertificateSha256";
    public static final String PROPERTY_PRODUCT_NAME = "productName";
    public static final String PROPERTY_GENERATION = "generation";

    /**
     * Address of the page with the event log of a camera.
     */

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
     * Path of the stream in full resolution with sound. {@code inst=2} gives the small resolution.
     */
    public static final String RTSP_PATH = "/rtsp_tunnel?line=1&inst=1&enableaudio=1";

    /**
     * Query of the small stream without sound, the one a video recorder detects motion on.
     */
    public static final String RTSP_SUBSTREAM_QUERY = "?line=1&inst=2&enableaudio=0";

    /**
     * Unguessable token that guards the URLs of a camera. Kept as a property so links survive restarts, and so it can
     * be looked up or deleted when a link has to be revoked.
     */
    public static final String PROPERTY_EVENTS_API = "eventsApiUrl";

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
     * Names of the HTTP clients for the cameras, verifying the certificate and trusting any. openHAB refuses names
     * longer than {@link #MAX_HTTP_CLIENT_NAME_LENGTH} characters.
     */
    public static final String CAMERA_HTTP_CLIENT_NAME = BINDING_ID;
    public static final String TRUST_ALL_HTTP_CLIENT_NAME = BINDING_ID + "-all";
    public static final int MAX_HTTP_CLIENT_NAME_LENGTH = 20;

    /**
     * Last part of a snapshot URL, which reads {@code /boschsmartcam/<token>/snapshot.jpg}.
     */
    public static final String SNAPSHOT_FILE = "snapshot.jpg";
    public static final String EVENTS_PATH = "events";
    public static final String EVENT_IMAGE_FILE = "image.jpg";
    public static final String EVENT_CLIP_FILE = "clip.mp4";

    /**
     * Page with the event log of a camera, and the stream it follows new events with. Same path and protection as
     * {@link #SNAPSHOT_FILE}.
     */

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
