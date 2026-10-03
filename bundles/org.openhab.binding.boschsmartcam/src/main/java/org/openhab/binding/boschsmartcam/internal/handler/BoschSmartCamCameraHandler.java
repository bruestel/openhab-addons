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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.net.ssl.SSLHandshakeException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.boschsmartcam.internal.BoschSmartCamCameraConfiguration;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.CameraModel;
import org.openhab.binding.boschsmartcam.internal.api.dto.VideoInput;
import org.openhab.binding.boschsmartcam.internal.auth.BoschSmartCamAuthService;
import org.openhab.binding.boschsmartcam.internal.events.CameraEvent;
import org.openhab.binding.boschsmartcam.internal.events.EventLog;
import org.openhab.binding.boschsmartcam.internal.local.CameraIdentity;
import org.openhab.binding.boschsmartcam.internal.local.CameraTrust;
import org.openhab.binding.boschsmartcam.internal.local.LocalCameraClient;
import org.openhab.binding.boschsmartcam.internal.local.OnvifEvent;
import org.openhab.binding.boschsmartcam.internal.local.PullPointSubscriber;
import org.openhab.binding.boschsmartcam.internal.net.CidrMatcher;
import org.openhab.binding.boschsmartcam.internal.video.CameraStream;
import org.openhab.binding.boschsmartcam.internal.video.HlsStream;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BoschSmartCamCameraHandler} talks to a single camera through its local API. Everything the camera can
 * tell about itself is read there.
 *
 * Events arrive through an ONVIF PullPoint subscription the handler keeps open. As long as the camera answers it, the
 * thing is online; a failing cloud does not change that.
 *
 * The account bridge is optional. With one the privacy mode can be switched, because the local API cannot change
 * anything.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamCameraHandler extends BaseThingHandler {

    private static final int MIN_SNAPSHOT_CACHE_SECONDS = 1;
    private static final long MIN_REFRESH_AGE_SECONDS = 5;

    /**
     * Delay before connecting again after the camera was lost, doubled with every failed attempt.
     */
    private static final Duration FIRST_RETRY = Duration.ofSeconds(5);
    private static final Duration MAX_RETRY = Duration.ofSeconds(60);

    /**
     * Without a clip id, events of the same kind within this window count as one.
     */
    private static final Duration EVENT_WINDOW = Duration.ofSeconds(10);
    private static final int REMEMBERED_EVENTS = 100;

    private static final String TOPIC_PRIVACY_MODE = "PrivacyMode";
    private static final String TOPIC_CLIP_RECORDING = "ClipRecording";
    private static final String TOPIC_DETECTED_SUFFIX = "Detected";
    private static final String ITEM_STATE = "State";
    private static final String ITEM_CLIP_ID = "ClipId";
    private static final String ITEM_START_TIME = "Starttime";
    private static final String ITEM_END_TIME = "Endtime";
    private static final String OPERATION_INITIALIZED = "Initialized";

    private static final String CHANNEL_LOCAL_PRIVACY_MODE = GROUP_LOCAL + "#" + CHANNEL_PRIVACY_MODE;
    private static final String CHANNEL_LOCAL_SNAPSHOT_URL = GROUP_LOCAL + "#" + CHANNEL_SNAPSHOT_URL;
    private static final String CHANNEL_LOCAL_HLS_URL = GROUP_LOCAL + "#" + CHANNEL_HLS_URL;

    /**
     * The live stream stops when nobody fetched its playlist or a segment for this long.
     */
    private static final Duration HLS_IDLE_TIMEOUT = Duration.ofSeconds(30);
    private static final String CHANNEL_LOCAL_EVENT = GROUP_LOCAL + "#" + CHANNEL_EVENT;
    private static final String CHANNEL_LOCAL_LAST_EVENT = GROUP_LOCAL + "#" + CHANNEL_LAST_EVENT;
    private static final String CHANNEL_LOCAL_LAST_EVENT_TIME = GROUP_LOCAL + "#" + CHANNEL_LAST_EVENT_TIME;
    private static final String CHANNEL_LOCAL_RECORDING = GROUP_LOCAL + "#" + CHANNEL_RECORDING;

    private final Logger logger = LoggerFactory.getLogger(BoschSmartCamCameraHandler.class);

    private final BoschSmartCamAuthService authService;
    private final HttpClient cameraHttpClient;
    private final Supplier<HttpClient> trustAllHttpClient;
    private final CameraTrust cameraTrust;
    private final Supplier<CidrMatcher> snapshotNetworks;
    private final String openhabBaseUrl;

    private BoschSmartCamCameraConfiguration config = new BoschSmartCamCameraConfiguration();
    private String accessToken = "";
    private Duration snapshotCache = Duration.ofSeconds(3);
    private @Nullable LocalCameraClient client;
    private @Nullable PullPointSubscriber subscriber;
    private final EventLog eventLog = new EventLog();
    private @Nullable HlsStream hls;
    private @Nullable CameraStream hlsSource;
    private @Nullable ScheduledFuture<?> hlsIdleJob;
    private volatile Instant hlsLastAccess = Instant.EPOCH;
    private @Nullable ScheduledFuture<?> connectJob;
    private Duration nextRetry = FIRST_RETRY;

    /**
     * The camera repeats an event every second while it lasts, and sends each one for two sources. Events already
     * passed on, by kind and clip id, with the time they were seen.
     */
    private final Map<String, Instant> recentEvents = new LinkedHashMap<>() {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.@Nullable Entry<String, Instant> eldest) {
            return size() > REMEMBERED_EVENTS;
        }
    };

    /**
     * Who answers at the configured host, read from its certificate. {@code null} until that was checked, and again
     * after the camera was unreachable, since the address may now belong to another device.
     */
    private volatile @Nullable CameraIdentity identity;

    /**
     * Id of this camera in the cloud, found through its MAC address. Only known with an account bridge.
     */
    private volatile @Nullable String cameraId;

    private volatile boolean privacyModeOn;
    private volatile Instant lastRefresh = Instant.EPOCH;

    public BoschSmartCamCameraHandler(Thing thing, BoschSmartCamAuthService authService, HttpClient cameraHttpClient,
            Supplier<HttpClient> trustAllHttpClient, CameraTrust cameraTrust, Supplier<CidrMatcher> snapshotNetworks,
            String openhabBaseUrl) {
        super(thing);
        this.authService = authService;
        this.cameraHttpClient = cameraHttpClient;
        this.trustAllHttpClient = trustAllHttpClient;
        this.cameraTrust = cameraTrust;
        this.snapshotNetworks = snapshotNetworks;
        this.openhabBaseUrl = openhabBaseUrl;
    }

    @Override
    public void initialize() {
        config = getConfigAs(BoschSmartCamCameraConfiguration.class);
        if (config.host.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error.no-host");
            return;
        }
        if (config.password.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error.no-password");
            return;
        }

        identity = null;
        cameraId = null;
        snapshotCache = Duration.ofSeconds(Math.max(MIN_SNAPSHOT_CACHE_SECONDS, config.snapshotCacheSeconds));
        accessToken = currentOrNewAccessToken();
        if (config.trustAllCertificates) {
            logger.warn("{} accepts any certificate, the camera is not verified", getThing().getUID());
        }
        HttpClient httpClient = config.trustAllCertificates ? trustAllHttpClient.get() : cameraHttpClient;
        client = new LocalCameraClient(httpClient, config.host, config.user, config.password);
        subscriber = new PullPointSubscriber(httpClient, config.host,
                LocalCameraClient.basicAuthorization(config.user, config.password), new EventListener());
        authService.addCamera(accessToken, this);

        updateStatus(ThingStatus.UNKNOWN);
        nextRetry = FIRST_RETRY;
        connectJob = scheduler.schedule(this::connect, 0, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        ScheduledFuture<?> job = connectJob;
        if (job != null) {
            job.cancel(true);
            connectJob = null;
        }
        PullPointSubscriber localSubscriber = subscriber;
        if (localSubscriber != null) {
            localSubscriber.stop();
            subscriber = null;
        }
        stopHls();
        authService.removeCamera(accessToken);
        cameraTrust.release(config.host);
        client = null;
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        // the camera works without the cloud, so unlike the default this leaves the status of the thing alone
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            scheduler.execute(() -> {
                BoschSmartCamAccountHandler accountHandler = getAccountHandler();
                if (accountHandler != null) {
                    updateFromCloud(accountHandler.getCameras());
                }
            });
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            refresh(channelUID.getId());
            return;
        }
        if (CHANNEL_LOCAL_PRIVACY_MODE.equals(channelUID.getId()) && command instanceof OnOffType onOff) {
            setPrivacyMode(onOff == OnOffType.ON);
        }
    }

    /**
     * Switches the privacy mode through the cloud. The local API cannot change it, so without an account the
     * command is refused and the switch goes back to what the camera reports.
     */
    private void setPrivacyMode(boolean on) {
        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        String id = accountHandler == null ? null : resolveCameraId(accountHandler);
        if (accountHandler == null || id == null) {
            logger.info("The privacy mode of {} can only be switched with a Bosch account bridge, the local API is "
                    + "read only", getThing().getUID());
            updateState(CHANNEL_LOCAL_PRIVACY_MODE, OnOffType.from(privacyModeOn));
            return;
        }
        try {
            accountHandler.getApi().setPrivacyMode(id, on, null);
            // the camera confirms the change through the event subscription within a second
            updateState(CHANNEL_LOCAL_PRIVACY_MODE, OnOffType.from(on));
        } catch (BoschSmartCamException e) {
            logger.warn("Could not switch the privacy mode of {}: {}", getThing().getUID(), e.getMessage());
            updateState(CHANNEL_LOCAL_PRIVACY_MODE, OnOffType.from(privacyModeOn));
        }
    }

    /**
     * Makes sure the right camera answers and opens the event subscription. Tried again with a growing delay as long
     * as that fails. The subscription delivers the complete state of the camera first, so nothing else is read.
     */
    private synchronized void connect() {
        LocalCameraClient localClient = client;
        PullPointSubscriber localSubscriber = subscriber;
        if (localClient == null || localSubscriber == null) {
            return;
        }
        if (identity == null && !checkIdentity(localClient)) {
            retryLater();
            return;
        }
        localSubscriber.start();
    }

    private synchronized void retryLater() {
        if (client == null) {
            // disposed in the meantime
            return;
        }
        Duration delay = nextRetry;
        nextRetry = nextRetry.multipliedBy(2).compareTo(MAX_RETRY) > 0 ? MAX_RETRY : nextRetry.multipliedBy(2);
        connectJob = scheduler.schedule(this::connect, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Answers a {@code REFRESH}, which openHAB sends for instance when an item is linked. Everything but the privacy
     * mode is known already, the event channels are taken from the event log.
     */
    private void refresh(String channelId) {
        switch (channelId) {
            case CHANNEL_LOCAL_PRIVACY_MODE -> {
                if (Duration.between(lastRefresh, Instant.now()).getSeconds() >= MIN_REFRESH_AGE_SECONDS) {
                    lastRefresh = Instant.now();
                    scheduler.execute(this::refreshPrivacyMode);
                }
            }
            case CHANNEL_LOCAL_SNAPSHOT_URL -> {
                if (identity != null) {
                    updateState(CHANNEL_LOCAL_SNAPSHOT_URL, new StringType(getSnapshotUrl()));
                }
            }
            case CHANNEL_LOCAL_HLS_URL -> {
                if (identity != null) {
                    updateState(CHANNEL_LOCAL_HLS_URL, new StringType(getUrl(HlsStream.PLAYLIST_FILE)));
                }
            }
            case CHANNEL_LOCAL_LAST_EVENT, CHANNEL_LOCAL_LAST_EVENT_TIME, CHANNEL_LOCAL_RECORDING -> {
                List<CameraEvent> events = eventLog.list();
                if (events.isEmpty()) {
                    return;
                }
                CameraEvent last = events.getFirst();
                switch (channelId) {
                    case CHANNEL_LOCAL_LAST_EVENT -> updateState(channelId, new StringType(last.kind()));
                    case CHANNEL_LOCAL_LAST_EVENT_TIME ->
                        updateState(channelId, new DateTimeType(last.time().atZone(ZoneId.systemDefault())));
                    default ->
                        updateState(channelId, OnOffType.from(last.clipId() != null && last.recordingEnd() == null));
                }
            }
            default -> logger.trace("Nothing to refresh for {}", channelId);
        }
    }

    /**
     * Reads the privacy mode on request. Normally not needed, the camera reports every change.
     */
    private void refreshPrivacyMode() {
        LocalCameraClient localClient = client;
        if (localClient == null) {
            return;
        }
        try {
            updatePrivacyMode(localClient.isPrivacyModeOn());
        } catch (BoschSmartCamException e) {
            logger.debug("Could not read the privacy mode of {}: {}", getThing().getUID(), e.getMessage());
        }
    }

    private void updatePrivacyMode(boolean on) {
        privacyModeOn = on;
        updateState(CHANNEL_LOCAL_PRIVACY_MODE, OnOffType.from(on));
    }

    /**
     * Receives what the PullPoint subscription delivers. Runs on the threads of the HTTP client, so it only passes
     * the events on.
     */
    private class EventListener implements PullPointSubscriber.Listener {

        @Override
        public void onNotifications(List<OnvifEvent> events) {
            nextRetry = FIRST_RETRY;
            updateStatus(ThingStatus.ONLINE);
            for (OnvifEvent event : events) {
                handleEvent(event);
            }
        }

        @Override
        public void onFailure(BoschSmartCamException e) {
            logger.debug("Events of {} interrupted: {}", getThing().getUID(), e.getMessage());
            reportLocalFailure(e);
            // the address may have been handed to another device in the meantime
            identity = null;
            retryLater();
        }
    }

    private void handleEvent(OnvifEvent event) {
        String topic = event.topic();
        logger.trace("{} reported {} ({}) {}", getThing().getUID(), topic,
                Objects.requireNonNullElse(event.propertyOperation(), "event"), event.data());
        if (TOPIC_PRIVACY_MODE.equals(topic)) {
            updatePrivacyMode(event.isTrue(ITEM_STATE));
        } else if (TOPIC_CLIP_RECORDING.equals(topic)) {
            // the camera only reports the end of a recording, the start comes with the detection
            boolean recording = event.isTrue(ITEM_STATE);
            updateState(CHANNEL_LOCAL_RECORDING, OnOffType.from(recording));
            String clipId = event.get(ITEM_CLIP_ID);
            if (!recording && clipId != null && !clipId.isBlank()) {
                eventLog.finishRecording(clipId, recordingEnd(event));
            }
        } else if (topic.endsWith(TOPIC_DETECTED_SUFFIX) && event.isTrue(ITEM_STATE)
                && !OPERATION_INITIALIZED.equals(event.propertyOperation())) {
            String kind = topic.substring(0, topic.length() - TOPIC_DETECTED_SUFFIX.length()).toUpperCase(Locale.ROOT);
            if (isNewEvent(kind, event)) {
                logger.debug("{} detected {}, clip {}", getThing().getUID(), kind, event.get(ITEM_CLIP_ID));
                ZonedDateTime time = eventTime(event);
                String clipId = event.get(ITEM_CLIP_ID);
                boolean withClip = clipId != null && !clipId.isBlank();
                triggerChannel(CHANNEL_LOCAL_EVENT, kind);
                updateState(CHANNEL_LOCAL_LAST_EVENT, new StringType(kind));
                updateState(CHANNEL_LOCAL_LAST_EVENT_TIME, new DateTimeType(time));
                if (withClip) {
                    updateState(CHANNEL_LOCAL_RECORDING, OnOffType.ON);
                }
                eventLog.add(new CameraEvent(time.toInstant(), kind, withClip ? clipId : null, null));
            }
        }
    }

    /**
     * @return whether the event was not passed on yet - the same clip, or the same kind within a short window when
     *         the camera sends no clip id
     */
    private boolean isNewEvent(String kind, OnvifEvent event) {
        String clipId = event.get(ITEM_CLIP_ID);
        Instant now = Instant.now();
        synchronized (recentEvents) {
            if (clipId != null && !clipId.isBlank()) {
                return recentEvents.putIfAbsent(kind + "/" + clipId, now) == null;
            }
            Instant last = recentEvents.get(kind);
            recentEvents.put(kind, now);
            return last == null || Duration.between(last, now).compareTo(EVENT_WINDOW) > 0;
        }
    }

    /**
     * @return the end the camera gives for the clip, or now if it gives none
     */
    private static Instant recordingEnd(OnvifEvent event) {
        String end = event.get(ITEM_END_TIME);
        if (end != null && !end.isBlank()) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(end));
            } catch (NumberFormatException e) {
                // fall through to now
            }
        }
        return Instant.now();
    }

    /**
     * @return when the event started: the start of its clip if the camera already knows it, which it only does from
     *         the second message on, otherwise the time of the message
     */
    private static ZonedDateTime eventTime(OnvifEvent event) {
        String start = event.get(ITEM_START_TIME);
        Instant time = event.time();
        if (start != null && !start.isBlank()) {
            try {
                time = Instant.ofEpochMilli(Long.parseLong(start));
            } catch (NumberFormatException e) {
                // keep the time of the message
            }
        }
        return (time == null ? Instant.now() : time).atZone(ZoneId.systemDefault());
    }

    /**
     * Makes sure the configured host is the camera this thing stands for, and reads what does not change while it
     * runs. Done once after starting and again after the camera was unreachable.
     *
     * @return whether the camera at the host is the expected one
     */
    private boolean checkIdentity(LocalCameraClient localClient) {
        CameraIdentity found;
        try {
            found = cameraTrust.readIdentity(config.host, HTTPS_PORT, config.trustAllCertificates);
        } catch (SSLHandshakeException e) {
            logger.debug("The certificate of {} is not trusted: {}", config.host, e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/offline.camera-untrusted [\"" + config.host + "\"]");
            return false;
        } catch (IOException e) {
            logger.debug("No Bosch camera answered at {}: {}", config.host, e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/offline.camera-not-reachable [\"" + config.host + "\"]");
            return false;
        }
        String expected = getThing().getProperties().get(Thing.PROPERTY_MAC_ADDRESS);
        if (expected != null && !expected.isBlank() && !expected.equals(found.macAddress())) {
            PullPointSubscriber localSubscriber = subscriber;
            if (localSubscriber != null) {
                localSubscriber.stop();
            }
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error.other-camera [\"" + config.host + "\", \"" + found.macAddress() + "\", \""
                            + expected + "\"]");
            return false;
        }

        Map<String, String> properties = new HashMap<>(editProperties());
        properties.put(Thing.PROPERTY_VENDOR, "Bosch");
        properties.put(Thing.PROPERTY_MAC_ADDRESS, found.macAddress());
        putIfPresent(properties, Thing.PROPERTY_SERIAL_NUMBER, found.serialNumber());
        try {
            putIfPresent(properties, Thing.PROPERTY_FIRMWARE_VERSION, localClient.getFirmwareVersion());
        } catch (BoschSmartCamException e) {
            reportLocalFailure(e);
            return false;
        }
        updateProperties(properties);
        identity = found;
        // from now on every connection to the host has to present exactly this camera
        cameraTrust.bind(config.host, found.macAddress());

        // only now: openHAB drops state updates of a handler that is still initializing
        updateState(CHANNEL_LOCAL_SNAPSHOT_URL, new StringType(getSnapshotUrl()));
        updateState(CHANNEL_LOCAL_HLS_URL, new StringType(getUrl(HlsStream.PLAYLIST_FILE)));
        updateProperty(PROPERTY_EVENTS_PAGE, getUrl(EVENTS_PAGE_FILE));
        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        if (accountHandler != null) {
            updateFromCloud(accountHandler.getCameras());
        }
        return true;
    }

    private void reportLocalFailure(BoschSmartCamException e) {
        if (e.getHttpStatus() == HttpStatus.UNAUTHORIZED_401) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error.local-credentials");
        } else {
            logger.debug("Talking to {} failed: {}", getThing().getUID(), e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    /**
     * Takes over what the cloud knows about this camera. Called with the result of every poll of the account bridge.
     */
    public void updateFromCloud(List<VideoInput> cameras) {
        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        String id = accountHandler == null ? null : resolveCameraId(accountHandler);
        if (id == null) {
            return;
        }
        VideoInput camera = cameras.stream().filter(input -> id.equals(input.id())).findFirst().orElse(null);
        if (camera == null) {
            return;
        }

        Map<String, String> properties = new HashMap<>(editProperties());
        properties.put(PROPERTY_CAMERA_ID, id);
        putIfPresent(properties, Thing.PROPERTY_MODEL_ID, camera.hardwareVersion());
        CameraModel model = camera.model();
        if (model != null) {
            properties.put(PROPERTY_PRODUCT_NAME, model.getProductName());
            properties.put(PROPERTY_GENERATION, String.valueOf(model.getGeneration()));
        }
        updateProperties(properties);
    }

    /**
     * @return the cloud id of this camera, or {@code null} as long as it is not known which camera this is or the
     *         account does not have it
     */
    private @Nullable String resolveCameraId(BoschSmartCamAccountHandler accountHandler) {
        String known = cameraId;
        CameraIdentity localIdentity = identity;
        if (known != null || localIdentity == null) {
            return known;
        }
        try {
            String found = accountHandler.findCameraId(localIdentity.macAddress());
            if (found == null) {
                logger.debug("{} with MAC address {} is not part of {}", getThing().getUID(),
                        localIdentity.macAddress(), accountHandler.getThing().getUID());
            }
            cameraId = found;
            return found;
        } catch (BoschSmartCamException e) {
            logger.debug("Could not look up {} in the cloud: {}", getThing().getUID(), e.getMessage());
            return null;
        }
    }

    /**
     * @return the current still image of the camera, reused from the cache while it is fresh enough
     */
    public byte[] getSnapshot() throws BoschSmartCamException {
        LocalCameraClient localClient = client;
        if (localClient == null) {
            throw new BoschSmartCamException("The camera is not initialized");
        }
        return localClient.getSnapshot(snapshotCache);
    }

    /**
     * @param remoteAddress address the snapshot request came from
     * @return whether that address is in the networks the binding allows
     */
    public boolean isAllowedFrom(String remoteAddress) {
        return snapshotNetworks.get().matches(remoteAddress);
    }

    public String getSnapshotUrl() {
        return getUrl(SNAPSHOT_FILE);
    }

    private String getUrl(String file) {
        return openhabBaseUrl + SERVLET_PATH + "/" + accessToken + "/" + file;
    }

    /**
     * Starts receiving the live stream of the camera. The caller has to stop it.
     */
    public CameraStream openStream(CameraStream.Sink sink) {
        CameraStream stream = new CameraStream(cameraTrust, scheduler, config.host, config.user, config.password,
                config.trustAllCertificates, 1, config.streamAudio, sink);
        stream.start();
        return stream;
    }

    /**
     * @return the live stream as HLS, started if it is not running yet. It stops again on its own once nobody
     *         fetches it for {@link #HLS_IDLE_TIMEOUT}.
     */
    public synchronized HlsStream startHls() {
        HlsStream current = hls;
        if (current == null || current.isFailed()) {
            stopHls();
            current = new HlsStream(getThing().getUID().getId());
            hls = current;
            hlsSource = openStream(current);
            hlsIdleJob = scheduler.scheduleWithFixedDelay(this::stopIdleHls, 10, 10, TimeUnit.SECONDS);
            logger.debug("Started the live stream of {}", getThing().getUID());
        }
        hlsLastAccess = Instant.now();
        return current;
    }

    /**
     * @return the running live stream, without starting one
     */
    public synchronized @Nullable HlsStream getRunningHls() {
        HlsStream current = hls;
        if (current != null) {
            hlsLastAccess = Instant.now();
        }
        return current;
    }

    private synchronized void stopIdleHls() {
        if (Duration.between(hlsLastAccess, Instant.now()).compareTo(HLS_IDLE_TIMEOUT) > 0) {
            logger.debug("Nobody watches the live stream of {} any more", getThing().getUID());
            stopHls();
        }
    }

    private synchronized void stopHls() {
        ScheduledFuture<?> job = hlsIdleJob;
        if (job != null) {
            job.cancel(false);
            hlsIdleJob = null;
        }
        CameraStream source = hlsSource;
        if (source != null) {
            source.stop();
            hlsSource = null;
        }
        hls = null;
    }

    public EventLog getEventLog() {
        return eventLog;
    }

    public String getLabel() {
        String label = getThing().getLabel();
        return label == null || label.isBlank() ? getThing().getUID().getId() : label;
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
        updateProperties(properties);
        return token;
    }

    private static void putIfPresent(Map<String, String> properties, String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }

    private @Nullable BoschSmartCamAccountHandler getAccountHandler() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof BoschSmartCamAccountHandler accountHandler) {
            return accountHandler;
        }
        return null;
    }
}
