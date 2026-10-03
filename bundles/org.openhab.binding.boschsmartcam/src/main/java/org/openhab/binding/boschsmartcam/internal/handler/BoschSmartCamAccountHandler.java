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
package org.openhab.binding.boschsmartcam.internal.handler;

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.boschsmartcam.internal.BoschSmartCamAccountConfiguration;
import org.openhab.binding.boschsmartcam.internal.api.AccessTokenProvider;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamApi;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.VideoInput;
import org.openhab.binding.boschsmartcam.internal.api.dto.WifiInfo;
import org.openhab.binding.boschsmartcam.internal.auth.BoschSmartCamAuthService;
import org.openhab.binding.boschsmartcam.internal.auth.PkceChallenge;
import org.openhab.binding.boschsmartcam.internal.local.CameraIdentity;
import org.openhab.core.auth.client.oauth2.AccessTokenRefreshListener;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BoschSmartCamAccountHandler} holds the authorization against Bosch SingleKey ID and polls the camera
 * settings of the account. Cameras do not need it, it only adds what the local API cannot do: changing settings and
 * the features of the app.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamAccountHandler extends BaseBridgeHandler
        implements AccessTokenProvider, AccessTokenRefreshListener {

    private static final long MIN_POLL_AGE_SECONDS = 10;

    private final Logger logger = LoggerFactory.getLogger(BoschSmartCamAccountHandler.class);

    private final OAuthFactory oAuthFactory;
    private final HttpClient httpClient;
    private final BoschSmartCamAuthService authService;

    private BoschSmartCamAccountConfiguration config = new BoschSmartCamAccountConfiguration();
    private @Nullable OAuthClientService oAuthService;
    private @Nullable BoschSmartCamApi api;
    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable PkceChallenge pkceChallenge;

    private volatile List<VideoInput> cameras = List.of();
    private volatile Instant lastPoll = Instant.EPOCH;

    /**
     * Cloud id of a camera by its MAC address. The camera list does not carry the address, it takes an extra request
     * per camera, so it is only asked once.
     */
    private final Map<String, String> cameraIdsByMacAddress = new ConcurrentHashMap<>();

    public BoschSmartCamAccountHandler(Bridge bridge, OAuthFactory oAuthFactory, HttpClient httpClient,
            BoschSmartCamAuthService authService) {
        super(bridge);
        this.oAuthFactory = oAuthFactory;
        this.httpClient = httpClient;
        this.authService = authService;
    }

    @Override
    public void initialize() {
        config = getConfigAs(BoschSmartCamAccountConfiguration.class);
        cameraIdsByMacAddress.clear();
        authService.addAccountHandler(this);

        oAuthService = createOAuthService();
        api = new BoschSmartCamApi(httpClient, this);
        updateStatus(ThingStatus.UNKNOWN);

        int interval = Math.max(30, config.refreshInterval);
        pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, interval, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        authService.removeAccountHandler(this);
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
        OAuthClientService service = oAuthService;
        if (service != null) {
            service.removeAccessTokenRefreshListener(this);
            oAuthFactory.ungetOAuthService(getHandle());
            oAuthService = null;
        }
        api = null;
        cameras = List.of();
    }

    @Override
    public void handleRemoval() {
        oAuthFactory.deleteServiceAndAccessToken(getHandle());
        super.handleRemoval();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            refreshFromCloud();
            return;
        }
        if (!CHANNEL_NOTIFICATIONS.equals(channelUID.getIdWithoutGroup()) || !(command instanceof OnOffType onOff)) {
            return;
        }
        Channel channel = getThing().getChannel(channelUID);
        String cameraId = channel == null ? null : channel.getProperties().get(PROPERTY_CAMERA_ID);
        if (cameraId == null) {
            logger.debug("No camera of {} belongs to {}", getHandle(), channelUID);
            return;
        }
        try {
            getApi().setNotifications(cameraId, onOff == OnOffType.ON);
            updateState(channelUID, onOff);
            scheduleDelayedPoll();
        } catch (BoschSmartCamException e) {
            logger.warn("Could not switch {}: {}", channelUID, e.getMessage());
        }
    }

    @Override
    public void onAccessTokenResponse(AccessTokenResponse tokenResponse) {
        logger.debug("Refreshed the access token of {}, it expires in {} seconds", getHandle(),
                tokenResponse.getExpiresIn());
    }

    // --- API access -------------------------------------------------------------------------------------------

    @Override
    public String getAccessToken() throws BoschSmartCamException {
        OAuthClientService service = oAuthService;
        if (service == null) {
            throw new BoschSmartCamException("Account is not initialized");
        }
        try {
            AccessTokenResponse response = service.getAccessTokenResponse();
            String accessToken = response == null ? null : response.getAccessToken();
            if (accessToken == null || accessToken.isBlank()) {
                throw new BoschSmartCamException("Account is not authorized", 401);
            }
            return accessToken;
        } catch (OAuthException | OAuthResponseException | IOException e) {
            throw new BoschSmartCamException("Could not obtain an access token: " + e.getMessage(), e);
        }
    }

    /**
     * @return the cameras of the last successful poll
     */
    public List<VideoInput> getCameras() {
        return cameras;
    }

    /**
     * Finds the camera of this account that uses the given MAC address.
     *
     * @param macAddress address in the form {@code 64:da:a0:12:34:56}
     * @return the cloud id of that camera, or {@code null} if it does not belong to this account
     */
    public @Nullable String findCameraId(String macAddress) throws BoschSmartCamException {
        String known = cameraIdsByMacAddress.get(macAddress);
        if (known != null) {
            return known;
        }
        if (cameras.isEmpty()) {
            // the first poll has not happened yet
            cameras = getApi().getVideoInputs();
        }
        for (VideoInput camera : cameras) {
            String cameraId = camera.id();
            if (cameraId == null || cameraIdsByMacAddress.containsValue(cameraId)) {
                continue;
            }
            String cameraMacAddress = getMacAddress(cameraId);
            if (macAddress.equals(cameraMacAddress)) {
                return cameraId;
            }
        }
        return null;
    }

    /**
     * @param macAddress address in the form {@code 64:da:a0:12:34:56}
     * @return the camera of this account that uses that address, or {@code null} if the account does not have it
     */
    public @Nullable VideoInput findCamera(String macAddress) throws BoschSmartCamException {
        String cameraId = findCameraId(macAddress);
        if (cameraId == null) {
            return null;
        }
        return cameras.stream().filter(camera -> cameraId.equals(camera.id())).findFirst().orElse(null);
    }

    /**
     * @return the MAC address of the camera with the given cloud id, or {@code null} if the cloud does not tell
     */
    public @Nullable String getMacAddress(String cameraId) throws BoschSmartCamException {
        String known = getKnownMacAddress(cameraId);
        return known != null ? known : readWifiInfo(cameraId).normalizedMacAddress();
    }

    /**
     * @return the MAC address of the camera with the given cloud id if it was read before, without asking the cloud
     */
    public @Nullable String getKnownMacAddress(String cameraId) {
        return cameraIdsByMacAddress.entrySet().stream().filter(entry -> entry.getValue().equals(cameraId))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    /**
     * Reads the network of a camera from the cloud, including the address it has there right now.
     */
    public WifiInfo readWifiInfo(String cameraId) throws BoschSmartCamException {
        WifiInfo wifiInfo = getApi().getWifiInfo(cameraId);
        String macAddress = wifiInfo.normalizedMacAddress();
        if (macAddress != null) {
            cameraIdsByMacAddress.put(macAddress, cameraId);
        }
        return wifiInfo;
    }

    public BoschSmartCamApi getApi() throws BoschSmartCamException {
        BoschSmartCamApi localApi = api;
        if (localApi == null) {
            throw new BoschSmartCamException("Account is not initialized");
        }
        return localApi;
    }

    /**
     * Reads the camera settings from the cloud unless that just happened. Used for {@code REFRESH} commands, which
     * openHAB sends per channel, so without the throttle a single item refresh would cause a burst of requests.
     */
    public void refreshFromCloud() {
        if (Duration.between(lastPoll, Instant.now()).getSeconds() < MIN_POLL_AGE_SECONDS) {
            return;
        }
        scheduler.execute(this::poll);
    }

    /**
     * Polls the camera settings and pushes them to the camera things.
     */
    public void poll() {
        lastPoll = Instant.now();
        if (!isAuthorized()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "@text/offline.conf-error.not-authorized [\"" + SERVLET_PATH + "\"]");
            return;
        }
        try {
            List<VideoInput> videoInputs = getApi().getVideoInputs();
            cameras = videoInputs;
            updateStatus(ThingStatus.ONLINE);
            syncNotificationChannels(videoInputs);
            Map<String, String> groups = notificationGroups();
            for (VideoInput camera : videoInputs) {
                String groupId = camera.id() == null ? null : groups.get(camera.id());
                if (groupId == null) {
                    continue;
                }
                updateState(new ChannelUID(getThing().getUID(), groupId, CHANNEL_NOTIFICATIONS),
                        OnOffType.from(camera.areNotificationsEnabled()));
                String status = camera.notificationsEnabledStatus();
                updateState(new ChannelUID(getThing().getUID(), groupId, CHANNEL_NOTIFICATIONS_STATUS),
                        status == null || status.isBlank() ? UnDefType.UNDEF : new StringType(status));
            }
            for (Thing thing : getThing().getThings()) {
                ThingHandler handler = thing.getHandler();
                if (handler instanceof BoschSmartCamCameraHandler cameraHandler) {
                    cameraHandler.updateFromCloud(videoInputs);
                }
            }
        } catch (BoschSmartCamException e) {
            logger.debug("Polling the Bosch cloud failed", e);
            if (e.isAuthorizationFailure()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                        "@text/offline.conf-error.not-authorized [\"" + SERVLET_PATH + "\"]");
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        }
    }

    /**
     * Gives every camera of the account a channel group for its notifications, named after the camera, and removes
     * the groups of cameras that are gone. Notifications go to the phones of this user, so they belong to the
     * account rather than to the camera thing - and they can be switched for cameras that are no thing at all.
     *
     * A group is named after the MAC address of its camera, like the camera thing. The cloud id the commands need is
     * kept as a property of the channels, so the MAC address is only looked up once, for a camera new to the account.
     */
    private void syncNotificationChannels(List<VideoInput> videoInputs) {
        ThingHandlerCallback callback = getCallback();
        if (callback == null) {
            return;
        }
        Set<String> cameraIds = videoInputs.stream().map(VideoInput::id).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> existing = notificationGroups();

        // groups of cameras that are gone, and groups of an earlier version that carry no cloud id
        List<Channel> stale = getThing().getChannels().stream().filter(channel -> {
            String cameraId = channel.getProperties().get(PROPERTY_CAMERA_ID);
            return cameraId == null || !cameraIds.contains(cameraId);
        }).toList();

        // new cameras get a group; existing groups are relabeled when the name of the camera or of the channel
        // types changed
        List<Channel> replaced = new ArrayList<>();
        List<Channel> added = new ArrayList<>();
        for (VideoInput camera : videoInputs) {
            String cameraId = camera.id();
            if (cameraId == null) {
                continue;
            }
            String groupId = existing.get(cameraId);
            if (groupId == null) {
                try {
                    groupId = notificationGroupId(cameraId);
                } catch (BoschSmartCamException e) {
                    logger.debug("Could not read the MAC address of {}, trying again with the next poll: {}",
                            camera.title(), e.getMessage());
                    continue;
                }
            }
            String title = camera.title();
            for (ChannelBuilder builder : callback.createChannelBuilders(
                    new ChannelGroupUID(getThing().getUID(), groupId), GROUP_TYPE_NOTIFICATIONS)) {
                Channel channel = builder.withProperties(Map.of(PROPERTY_CAMERA_ID, cameraId)).build();
                Channel wanted = title == null || title.isBlank() ? channel
                        : ChannelBuilder.create(channel).withLabel(title + " " + channel.getLabel()).build();
                Channel current = getThing().getChannel(wanted.getUID());
                if (current == null) {
                    added.add(wanted);
                } else if (!Objects.equals(current.getLabel(), wanted.getLabel())) {
                    replaced.add(current);
                    added.add(wanted);
                }
            }
        }
        stale = new ArrayList<>(stale);
        stale.addAll(replaced);

        if (!stale.isEmpty() || !added.isEmpty()) {
            updateThing(editThing().withoutChannels(stale).withChannels(added).build());
        }
    }

    /**
     * @return the channel groups of the notifications by the cloud id of their camera
     */
    private Map<String, String> notificationGroups() {
        Map<String, String> groups = new HashMap<>();
        for (Channel channel : getThing().getChannels()) {
            String cameraId = channel.getProperties().get(PROPERTY_CAMERA_ID);
            String groupId = channel.getUID().getGroupId();
            if (cameraId != null && groupId != null) {
                groups.put(cameraId, groupId);
            }
        }
        return groups;
    }

    /**
     * @return the id of the notification group of a camera: its MAC address without separators, like the camera
     *         thing, or its cloud id in lower case without dashes if the cloud does not tell the MAC address
     */
    private String notificationGroupId(String cameraId) throws BoschSmartCamException {
        String macAddress = getMacAddress(cameraId);
        return macAddress != null ? CameraIdentity.thingId(macAddress)
                : cameraId.replace("-", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Triggers a poll shortly after a setting was changed, giving the camera time to apply it.
     */
    public void scheduleDelayedPoll() {
        scheduler.schedule(this::poll, 5, TimeUnit.SECONDS);
    }

    // --- Authorization ----------------------------------------------------------------------------------------

    public boolean isAuthorized() {
        OAuthClientService service = oAuthService;
        if (service == null) {
            return false;
        }
        try {
            AccessTokenResponse response = service.getAccessTokenResponse();
            return response != null && response.getRefreshToken() != null;
        } catch (OAuthException | OAuthResponseException | IOException e) {
            logger.debug("Could not read the stored token of {}: {}", getHandle(), e.getMessage());
            return false;
        }
    }

    /**
     * Builds the URL the user has to open to log in with the Bosch SingleKey ID. A new PKCE challenge is created for
     * every call, the verifier is kept until the authorization code is handed back.
     */
    public String formatAuthorizationUrl() throws BoschSmartCamException {
        OAuthClientService service = oAuthService;
        if (service == null) {
            throw new BoschSmartCamException("Account is not initialized");
        }
        PkceChallenge challenge = PkceChallenge.create();
        pkceChallenge = challenge;
        try {
            // openHAB core does not support PKCE, so the challenge is appended to the generated URL
            return service.getAuthorizationUrl(OAUTH_REDIRECT_URI, null, getHandle()) + "&code_challenge="
                    + URLEncoder.encode(challenge.challenge(), StandardCharsets.UTF_8) + "&code_challenge_method=S256";
        } catch (OAuthException e) {
            throw new BoschSmartCamException("Could not create the authorization URL: " + e.getMessage(), e);
        }
    }

    /**
     * Exchanges the authorization code contained in the redirect URL for the tokens.
     *
     * @param redirectUrlWithParams the complete URL the browser was redirected to
     * @return the label of the authorized account
     */
    public String authorize(String redirectUrlWithParams) throws BoschSmartCamException {
        OAuthClientService service = oAuthService;
        if (service == null) {
            throw new BoschSmartCamException("Account is not initialized");
        }
        PkceChallenge challenge = pkceChallenge;
        if (challenge == null) {
            throw new BoschSmartCamException("No login in progress, please start the login again");
        }
        try {
            String code = service.extractAuthCodeFromAuthResponse(redirectUrlWithParams);
            service.addExtraAuthField("code_verifier", challenge.verifier());
            service.getAccessTokenResponseByAuthorizationCode(code, OAUTH_REDIRECT_URI);
        } catch (OAuthException | OAuthResponseException | IOException e) {
            throw new BoschSmartCamException("Authorization failed: " + e.getMessage(), e);
        } finally {
            pkceChallenge = null;
            // the code verifier must not be sent with the following refresh requests
            recreateOAuthService();
        }
        scheduler.execute(this::poll);
        return getLabel();
    }

    /**
     * Removes the stored tokens so the account can be authorized with a different user.
     */
    public void deauthorize() {
        OAuthClientService service = oAuthService;
        if (service != null) {
            service.removeAccessTokenRefreshListener(this);
        }
        oAuthFactory.deleteServiceAndAccessToken(getHandle());
        oAuthService = createOAuthService();
        cameras = List.of();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                "@text/offline.conf-error.not-authorized [\"" + SERVLET_PATH + "\"]");
    }

    public String getLabel() {
        String label = getThing().getLabel();
        return label == null || label.isBlank() ? getThing().getUID().getAsString() : label;
    }

    public boolean matchesState(String state) {
        return getHandle().equals(state);
    }

    private String getHandle() {
        return getThing().getUID().getAsString();
    }

    private OAuthClientService createOAuthService() {
        OAuthClientService service = oAuthFactory.createOAuthClientService(getHandle(), OAUTH_TOKEN_URL,
                OAUTH_AUTHORIZE_URL, OAUTH_CLIENT_ID, OAUTH_CLIENT_SECRET, OAUTH_SCOPE, false);
        service.addAccessTokenRefreshListener(this);
        return service;
    }

    /**
     * Drops the in memory instance of the OAuth service and creates a new one from the persisted tokens. Used to get
     * rid of the one time PKCE code verifier that has to be added as an extra field for the code exchange.
     */
    private void recreateOAuthService() {
        OAuthClientService service = oAuthService;
        if (service != null) {
            service.removeAccessTokenRefreshListener(this);
        }
        oAuthFactory.ungetOAuthService(getHandle());
        oAuthService = createOAuthService();
    }
}
