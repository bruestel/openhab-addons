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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.BoschSmartCamCameraConfiguration;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.CameraModel;
import org.openhab.binding.boschsmartcam.internal.api.dto.CameraStatus;
import org.openhab.binding.boschsmartcam.internal.api.dto.VideoInput;
import org.openhab.binding.boschsmartcam.internal.auth.BoschSmartCamAuthService;
import org.openhab.binding.boschsmartcam.internal.diagnostics.OnvifProbe;
import org.openhab.binding.boschsmartcam.internal.snapshot.SnapshotFetcher;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BoschSmartCamCameraHandler} exposes the settings of a single camera. Snapshots and video streams are
 * fetched directly from the camera and are not part of this binding.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamCameraHandler extends BaseThingHandler {

    private static final int MIN_SNAPSHOT_CACHE_SECONDS = 5;

    private final Logger logger = LoggerFactory.getLogger(BoschSmartCamCameraHandler.class);

    private final BoschSmartCamAuthService authService;
    private final org.eclipse.jetty.client.HttpClient cameraHttpClient;
    private final String openhabBaseUrl;

    private String cameraId = "";
    private String accessToken = "";
    private Duration snapshotCache = Duration.ofSeconds(15);
    private @Nullable SnapshotFetcher snapshotFetcher;

    public BoschSmartCamCameraHandler(Thing thing, BoschSmartCamAuthService authService,
            org.eclipse.jetty.client.HttpClient cameraHttpClient, String openhabBaseUrl) {
        super(thing);
        this.authService = authService;
        this.cameraHttpClient = cameraHttpClient;
        this.openhabBaseUrl = openhabBaseUrl;
    }

    @Override
    public void initialize() {
        BoschSmartCamCameraConfiguration config = getConfigAs(BoschSmartCamCameraConfiguration.class);
        cameraId = config.cameraId;
        if (cameraId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error.no-camera-id");
            return;
        }

        snapshotCache = Duration.ofSeconds(Math.max(MIN_SNAPSHOT_CACHE_SECONDS, config.snapshotCacheSeconds));
        accessToken = currentOrNewAccessToken();
        snapshotFetcher = new SnapshotFetcher(cameraHttpClient, cameraId,
                () -> getRequiredAccountHandler().getApi().openLocalConnection(cameraId));
        authService.addCamera(accessToken, this);

        updateStatus(ThingStatus.UNKNOWN);

        // off the initializing thread: reading the reachability talks to the cloud, and without it the thing would
        // stay UNKNOWN until the next poll of the bridge
        scheduler.execute(() -> {
            // only after initializing: openHAB drops state updates of a handler that is still starting up
            updateState(CHANNEL_SNAPSHOT_URL, new StringType(getSnapshotUrl()));

            BoschSmartCamAccountHandler accountHandler = getAccountHandler();
            if (accountHandler == null) {
                return;
            }
            List<VideoInput> cameras = accountHandler.getCameras();
            if (cameras.isEmpty()) {
                // nothing cached yet, the poll of the bridge pushes the settings to this thing as well
                accountHandler.refreshFromCloud();
            } else {
                updateFromCameras(cameras, true);
            }
        });
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        if (accountHandler == null) {
            return;
        }

        if (command instanceof RefreshType) {
            // show what is known right away, the fresh values from the cloud follow
            updateFromCameras(accountHandler.getCameras());
            accountHandler.refreshFromCloud();
            return;
        }

        if (!(command instanceof OnOffType onOffCommand)) {
            return;
        }

        boolean enabled = onOffCommand == OnOffType.ON;
        try {
            switch (channelUID.getId()) {
                case CHANNEL_PRIVACY_MODE -> accountHandler.getApi().setPrivacyMode(cameraId, enabled, null);
                case CHANNEL_NOTIFICATIONS -> accountHandler.getApi().setNotifications(cameraId, enabled);
                default -> {
                    return;
                }
            }
            updateState(channelUID, onOffCommand);
            // the camera needs a moment to apply the change, so read the confirmed state a bit later
            accountHandler.scheduleDelayedPoll();
        } catch (BoschSmartCamException e) {
            logger.debug("Could not send command {} to channel {}", command, channelUID, e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    /**
     * Applies the settings of the last poll without asking the cloud whether the camera is reachable.
     */
    public void updateFromCameras(List<VideoInput> cameras) {
        updateFromCameras(cameras, false);
    }

    /**
     * Applies the settings of the last poll.
     *
     * @param withReachability whether to ask the cloud whether the camera is reachable, which costs an extra request
     *            per camera and must not be done while initializing
     */
    public void updateFromCameras(List<VideoInput> cameras, boolean withReachability) {
        updateState(CHANNEL_SNAPSHOT_URL, new StringType(getSnapshotUrl()));

        VideoInput camera = cameras.stream().filter(input -> cameraId.equals(input.id())).findFirst().orElse(null);
        if (camera == null) {
            if (!cameras.isEmpty()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.GONE, "@text/offline.camera-not-in-account");
            }
            return;
        }

        updateProperties(camera);
        updateState(CHANNEL_PRIVACY_MODE, OnOffType.from(camera.isPrivacyModeOn()));
        updateState(CHANNEL_NOTIFICATIONS, OnOffType.from(camera.areNotificationsEnabled()));
        updateRawState(CHANNEL_NOTIFICATIONS_STATUS, camera.notificationsEnabledStatus());

        if (withReachability) {
            updateReachability();
        }
    }

    private void updateReachability() {
        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        if (accountHandler == null) {
            return;
        }

        CameraStatus status;
        try {
            status = accountHandler.getApi().getCameraStatus(cameraId);
        } catch (BoschSmartCamException e) {
            logger.debug("Could not read the state of {}", cameraId, e);
            return;
        }

        updateState(CHANNEL_STATUS, status == CameraStatus.UNKNOWN ? UnDefType.UNDEF : new StringType(status.name()));

        switch (status) {
            // a session limit says nothing about the camera, the settings keep working
            case ONLINE, SESSION_LIMIT -> updateStatus(ThingStatus.ONLINE);
            case OFFLINE -> updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/offline.camera-not-reachable");
            case UPDATING -> updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/offline.camera-updating");
            // keep whatever the thing had rather than flapping on an inconclusive answer
            case UNKNOWN -> logger.debug("Neither endpoint told whether {} is reachable", cameraId);
        }
    }

    @Override
    public void dispose() {
        authService.removeCamera(accessToken);
        SnapshotFetcher fetcher = snapshotFetcher;
        if (fetcher != null) {
            fetcher.clear();
            snapshotFetcher = null;
        }
    }

    /**
     * @return the current still image of the camera, reused from the cache while it is fresh enough
     */
    public byte[] getSnapshot() throws BoschSmartCamException {
        SnapshotFetcher fetcher = snapshotFetcher;
        if (fetcher == null) {
            throw new BoschSmartCamException("The camera is not initialized");
        }
        return fetcher.getSnapshot(snapshotCache);
    }

    /**
     * Collects what the camera says about ONVIF, read only. Diagnostic aid while it is still open whether the
     * cameras can deliver events without the cloud.
     */
    public String probeOnvif() {
        OnvifProbe probe = new OnvifProbe();
        try {
            probe.add("GET /v11/video_inputs/{id}/onvif_user",
                    getRequiredAccountHandler().getApi().getOnvifUser(cameraId));
        } catch (BoschSmartCamException e) {
            probe.addFailure("GET /v11/video_inputs/{id}/onvif_user", e);
        }

        SnapshotFetcher fetcher = snapshotFetcher;
        if (fetcher == null) {
            probe.add("local", "the camera is not initialized");
            return probe.toString();
        }
        for (String command : List.of(OnvifProbe.RCP_NETWORK_SERVICES, OnvifProbe.RCP_ONVIF_SCOPES)) {
            String name = "RCP " + command;
            try {
                probe.add(name, OnvifProbe.describeRcp(fetcher.fetchFromCamera(OnvifProbe.rcpReadPath(command))));
            } catch (BoschSmartCamException e) {
                probe.addFailure(name, e);
            }
        }
        return probe.toString();
    }

    /**
     * @param remoteAddress address the snapshot request came from
     * @return whether that address is in the networks the account allows
     */
    public boolean isAllowedFrom(String remoteAddress) {
        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        if (accountHandler == null) {
            return false;
        }
        return accountHandler.getSnapshotNetworks().matches(remoteAddress);
    }

    public String getSnapshotUrl() {
        return url(SNAPSHOT_FILE);
    }

    private String url(String file) {
        return openhabBaseUrl + SERVLET_PATH + "/" + file + "?" + PARAM_TOKEN + "=" + accessToken;
    }

    /**
     * @return the account this camera belongs to, needed to talk to the cloud
     */
    private BoschSmartCamAccountHandler getRequiredAccountHandler() throws BoschSmartCamException {
        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        if (accountHandler == null) {
            throw new BoschSmartCamException("The camera has no account bridge");
        }
        return accountHandler;
    }

    /**
     * Reuses the token of a previous run so links stay valid, and creates one when there is none yet. Deleting the
     * property is therefore how a link is revoked.
     */
    private String currentOrNewAccessToken() {
        String stored = getThing().getProperties().get(PROPERTY_ACCESS_TOKEN);
        if (stored != null && !stored.isBlank()) {
            return stored;
        }
        String token = UUID.randomUUID().toString();
        Map<String, String> properties = new HashMap<>(editProperties());
        properties.put(PROPERTY_ACCESS_TOKEN, token);
        // a token of an earlier version of the binding, no longer used
        properties.remove("snapshotToken");
        updateProperties(properties);
        return token;
    }

    /**
     * Puts a value on a channel as the cloud sent it, so states this binding does not interpret stay visible.
     */
    private void updateRawState(String channelId, @Nullable String value) {
        updateState(channelId, value == null || value.isBlank() ? UnDefType.UNDEF : new StringType(value));
    }

    private void updateProperties(VideoInput camera) {
        Map<String, String> properties = new HashMap<>(editProperties());
        properties.put(Thing.PROPERTY_VENDOR, "Bosch");
        putIfPresent(properties, Thing.PROPERTY_MODEL_ID, camera.hardwareVersion());
        putIfPresent(properties, Thing.PROPERTY_FIRMWARE_VERSION, camera.firmwareVersion());
        putIfPresent(properties, CONFIG_CAMERA_ID, camera.id());

        CameraModel model = camera.model();
        if (model != null) {
            properties.put(PROPERTY_PRODUCT_NAME, model.getProductName());
            properties.put(PROPERTY_GENERATION, String.valueOf(model.getGeneration()));
        }
        updateProperties(properties);
    }

    private static void putIfPresent(Map<String, String> properties, String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }

    private @Nullable BoschSmartCamAccountHandler getAccountHandler() {
        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return null;
        }
        if (bridge.getHandler() instanceof BoschSmartCamAccountHandler accountHandler) {
            return accountHandler;
        }
        return null;
    }
}
