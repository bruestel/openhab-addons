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
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.net.ssl.SSLHandshakeException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.boschsmartcam.internal.BoschSmartCamCameraConfiguration;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamApi;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamApi.Light;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.CameraModel;
import org.openhab.binding.boschsmartcam.internal.api.dto.CloudEvent;
import org.openhab.binding.boschsmartcam.internal.api.dto.LightSettings;
import org.openhab.binding.boschsmartcam.internal.auth.BoschSmartCamAuthService;
import org.openhab.binding.boschsmartcam.internal.events.CameraEvent;
import org.openhab.binding.boschsmartcam.internal.events.CloudEventFeed;
import org.openhab.binding.boschsmartcam.internal.events.EventLog;
import org.openhab.binding.boschsmartcam.internal.local.CameraIdentity;
import org.openhab.binding.boschsmartcam.internal.local.CameraTrust;
import org.openhab.binding.boschsmartcam.internal.local.LocalCameraClient;
import org.openhab.binding.boschsmartcam.internal.local.LocalCameraClient.AlarmStatus;
import org.openhab.binding.boschsmartcam.internal.local.LocalCameraClient.ManualLighting;
import org.openhab.binding.boschsmartcam.internal.local.OnvifEvent;
import org.openhab.binding.boschsmartcam.internal.local.PullPointSubscriber;
import org.openhab.binding.boschsmartcam.internal.local.RtspGateway;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.storage.Storage;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BoschSmartCamCameraHandler} talks to a single camera through its local API. Everything the camera can
 * tell about itself is read there.
 *
 * Events arrive through an ONVIF PullPoint subscription the handler keeps open. As long as the camera answers it, the
 * thing is online; a failing cloud does not change that.
 *
 * An account is optional; the camera finds one that knows it by itself. With one the privacy mode can be switched,
 * because the local API cannot change
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

    /**
     * A token goes into paths and addresses, so it has to be long and plain.
     */
    private static final Pattern ACCESS_TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{16,}");

    private static final String CHANNEL_LOCAL_PRIVACY_MODE = GROUP_LOCAL + "#" + CHANNEL_PRIVACY_MODE;
    private static final String CHANNEL_LOCAL_SNAPSHOT_URL = GROUP_LOCAL + "#" + CHANNEL_SNAPSHOT_URL;
    private static final String CHANNEL_LOCAL_RTSP_URL = GROUP_LOCAL + "#" + CHANNEL_RTSP_URL;
    private static final String CHANNEL_LOCAL_RTSP_SUBSTREAM_URL = GROUP_LOCAL + "#" + CHANNEL_RTSP_SUBSTREAM_URL;
    private static final String CHANNEL_LOCAL_RTSPS_URL = GROUP_LOCAL + "#" + CHANNEL_RTSPS_URL;
    private static final String CHANNEL_LOCAL_RTSPS_SUBSTREAM_URL = GROUP_LOCAL + "#" + CHANNEL_RTSPS_SUBSTREAM_URL;
    private static final String CHANNEL_LOCAL_CAMERA_RTSPS_URL = GROUP_LOCAL + "#" + CHANNEL_CAMERA_RTSPS_URL;
    private static final String CHANNEL_LOCAL_EVENT = GROUP_LOCAL + "#" + CHANNEL_EVENT;
    private static final String CHANNEL_LOCAL_LAST_EVENT = GROUP_LOCAL + "#" + CHANNEL_LAST_EVENT;
    private static final String CHANNEL_LOCAL_LAST_EVENT_TIME = GROUP_LOCAL + "#" + CHANNEL_LAST_EVENT_TIME;
    private static final String CHANNEL_LOCAL_RECORDING = GROUP_LOCAL + "#" + CHANNEL_RECORDING;
    private static final String CHANNEL_CLOUD_LAST_CLIP_SNAPSHOT_URL = GROUP_CLOUD + "#"
            + CHANNEL_LAST_CLIP_SNAPSHOT_URL;
    private static final String CHANNEL_CLOUD_LAST_CLIP_URL = GROUP_CLOUD + "#" + CHANNEL_LAST_CLIP_URL;
    private static final String CHANNEL_CLOUD_CLIP_READY = GROUP_CLOUD + "#" + CHANNEL_CLIP_READY;
    private static final String CHANNEL_CLOUD_EVENTS_API_URL = GROUP_CLOUD + "#" + CHANNEL_EVENTS_API_URL;
    private static final String CHANNEL_LIGHT_FRONT = GROUP_LIGHT + "#" + CHANNEL_FRONT_LIGHT;
    private static final String CHANNEL_LIGHT_TOP_BOTTOM = GROUP_LIGHT + "#" + CHANNEL_TOP_BOTTOM_LIGHT;
    private static final String CHANNEL_LIGHT_MOTION = GROUP_LIGHT + "#" + CHANNEL_MOTION_LIGHT;
    private static final String CHANNEL_ALARM_SIREN = GROUP_ALARM + "#" + CHANNEL_SIREN;

    private static final String TOPIC_LIGHT_FRONT = "LightStatusFront";
    private static final String TOPIC_LIGHT_TOP = "LightStatusTop";
    private static final String TOPIC_LIGHT_BOTTOM = "LightStatusBottom";
    /**
     * Reported when an alarm starts or stops, e.g. {@code manual_alarm} and {@code alarm_muted}.
     */
    private static final String TOPIC_ALARM_MODE = "AlarmMode";
    private static final String ITEM_BRIGHTNESS = "Brightness";
    private static final String ALARM_NONE = "NONE";
    /**
     * After a command the cloud passes on to the camera, the camera is read again this much later.
     */
    private static final int READ_BACK_SECONDS = 3;
    /**
     * The camera reports the top and bottom LEDs one after the other, some 25 ms apart. Their common state waits this
     * long, so switching both off does not show ON in between.
     */
    private static final int TOP_BOTTOM_SETTLE_MILLIS = 300;

    /**
     * How far apart the cloud and the camera may date the same event. Measured, they are some 50 ms apart.
     */
    private static final Duration CLOUD_MATCH_WINDOW = Duration.ofSeconds(3);
    /**
     * When the cloud is asked for an event after it was detected locally. The image is there once the camera has
     * finished recording, some 15 seconds in, the clip usually another 15 seconds later.
     */
    private static final int[] CLOUD_LOOKUP_DELAYS_SECONDS = { 5, 10, 15, 15, 30, 60, 120 };

    /**
     * How much longer each attempt to reach the camera waits than the one before, up to {@link #MAX_RETRY}.
     */
    private static final int RETRY_BACKOFF_FACTOR = 2;

    /**
     * The certificate of the gateway as PEM, in lines of 64 characters as RFC 7468 asks for.
     */
    private static final String PEM_BEGIN = "-----BEGIN CERTIFICATE-----\n";
    private static final String PEM_END = "\n-----END CERTIFICATE-----\n";
    private static final int PEM_LINE_LENGTH = 64;
    private static final byte[] PEM_LINE_SEPARATOR = { '\n' };

    /**
     * Shown in the log for an event that is not a change of a property, which carry no operation.
     */
    private static final String PLAIN_EVENT = "event";

    private final Logger logger = LoggerFactory.getLogger(BoschSmartCamCameraHandler.class);

    private final BoschSmartCamAuthService authService;
    private final HttpClient cameraHttpClient;
    private final CameraContext context;
    private final CameraTrust cameraTrust;
    private final Storage<String> accessTokens;
    private final String openhabBaseUrl;

    private BoschSmartCamCameraConfiguration config = new BoschSmartCamCameraConfiguration();
    private String accessToken = "";
    private Duration snapshotCache = Duration
            .ofSeconds(BoschSmartCamCameraConfiguration.DEFAULT_SNAPSHOT_CACHE_SECONDS);
    private @Nullable LocalCameraClient client;
    private @Nullable PullPointSubscriber subscriber;
    private final EventLog eventLog = new EventLog();
    private final CloudEventFeed cloudEvents = new CloudEventFeed(this::fetchCloudEvents);
    private final Set<ScheduledFuture<?>> cloudLookups = ConcurrentHashMap.newKeySet();
    private volatile Instant latestLocalEvent = Instant.EPOCH;
    private volatile @Nullable String lastImageEventId;
    private volatile @Nullable String lastClipEventId;
    // the top and bottom LEDs are switched together but reported apart
    private volatile int topBrightness;
    private volatile int bottomBrightness;
    private @Nullable ScheduledFuture<?> topBottomUpdate;
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
     * Id of this camera in the cloud, found through its MAC address. Only known with an account.
     */
    private volatile @Nullable String cameraId;

    /**
     * The account the cloud is reached through, see {@link #getAccountHandler(Set)}.
     */
    private volatile @Nullable BoschSmartCamAccountHandler account;

    private volatile boolean privacyModeOn;
    private volatile Instant lastRefresh = Instant.EPOCH;

    public BoschSmartCamCameraHandler(Thing thing, BoschSmartCamAuthService authService, HttpClient cameraHttpClient,
            CameraContext context, CameraTrust cameraTrust, Storage<String> accessTokens, String openhabBaseUrl) {
        super(thing);
        this.accessTokens = accessTokens;
        this.authService = authService;
        this.cameraHttpClient = cameraHttpClient;
        this.context = context;
        this.cameraTrust = cameraTrust;
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
        account = null;
        snapshotCache = Duration.ofSeconds(Math.max(MIN_SNAPSHOT_CACHE_SECONDS, config.snapshotCacheSeconds));
        String token = currentOrNewAccessToken();
        if (!ACCESS_TOKEN_PATTERN.matcher(token).matches()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error.invalid-token");
            return;
        }
        accessToken = token;
        if (config.trustAllCertificates) {
            logger.warn("{} accepts any certificate, the camera is not verified", getThing().getUID());
        }
        HttpClient httpClient = config.trustAllCertificates ? context.getTrustAllHttpClient() : cameraHttpClient;
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
        cloudLookups.forEach(lookup -> lookup.cancel(true));
        cloudLookups.clear();
        synchronized (this) {
            ScheduledFuture<?> pending = topBottomUpdate;
            if (pending != null) {
                pending.cancel(true);
                topBottomUpdate = null;
            }
        }
        authService.removeCamera(accessToken);
        cameraTrust.release(config.host);
        client = null;
    }

    /**
     * Told by an account when it goes offline or away. The camera then looks for another account that knows it; if
     * there is none, the addresses of the last clip lead nowhere, as the clips are fetched through an account.
     */
    public void accountGone(BoschSmartCamAccountHandler gone) {
        if (!gone.equals(account)) {
            return;
        }
        account = null;
        if (getAccountHandler() == null) {
            lastImageEventId = null;
            lastClipEventId = null;
            updateState(CHANNEL_CLOUD_LAST_CLIP_SNAPSHOT_URL, UnDefType.UNDEF);
            updateState(CHANNEL_CLOUD_LAST_CLIP_URL, UnDefType.UNDEF);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            refresh(channelUID.getId());
            return;
        }
        String channelId = channelUID.getId();
        if (CHANNEL_LOCAL_PRIVACY_MODE.equals(channelId) && command instanceof OnOffType onOff) {
            setPrivacyMode(onOff == OnOffType.ON);
            return;
        }
        switch (channelId) {
            case CHANNEL_LIGHT_FRONT -> {
                if (command instanceof OnOffType onOff) {
                    inCloud("switch the front light of",
                            (api, id) -> api.setLightOn(id, Light.FRONT, onOff == OnOffType.ON));
                }
            }
            case CHANNEL_LIGHT_TOP_BOTTOM -> {
                if (command instanceof OnOffType onOff) {
                    inCloud("switch the top and bottom light of",
                            (api, id) -> api.setLightOn(id, Light.TOP_AND_BOTTOM, onOff == OnOffType.ON));
                }
            }
            case CHANNEL_LIGHT_MOTION -> {
                if (command instanceof OnOffType onOff) {
                    inCloud("switch the motion light of", (api, id) -> api.setMotionLight(id, onOff == OnOffType.ON));
                }
            }
            case CHANNEL_ALARM_SIREN -> {
                if (command instanceof OnOffType onOff) {
                    inCloud("switch the siren of", (api, id) -> api.setPanicAlarm(id, onOff == OnOffType.ON));
                }
            }
            default -> {
            }
        }
    }

    @FunctionalInterface
    private interface CloudCall {
        void run(BoschSmartCamApi api, String cameraId) throws BoschSmartCamException;
    }

    /**
     * Sends a command through the cloud, as the local API only reads, and reads the camera back once the cloud passed
     * it on. Without an account the command is refused and the channels show what the camera reports.
     *
     * @param what what is done, for the log, e.g. {@code "switch the siren of"}
     */
    private void inCloud(String what, CloudCall call) {
        inCloud(what, call, () -> {
        });
    }

    /**
     * @param failed what to do when the command could not be sent, e.g. show the state of the camera again
     */
    private void inCloud(String what, CloudCall call, Runnable failed) {
        scheduler.execute(() -> {
            // an account the camera is only shared with may not be allowed to, then the next one that knows it is tried
            Set<BoschSmartCamAccountHandler> refused = new HashSet<>();
            while (true) {
                BoschSmartCamAccountHandler accountHandler = getAccountHandler(refused);
                String id = accountHandler == null ? null : resolveCameraId(accountHandler);
                if (accountHandler == null || id == null) {
                    if (refused.isEmpty()) {
                        logger.info("Can only {} {} with a Bosch account, the local API is read only", what,
                                getThing().getUID());
                    } else {
                        logger.info("Could not {} {}: no account that knows the camera may do that, an account it is "
                                + "only shared with may not", what, getThing().getUID());
                    }
                    failed.run();
                    break;
                }
                try {
                    call.run(accountHandler.getApi(), id);
                    break;
                } catch (BoschSmartCamException e) {
                    if (e.isAuthorizationFailure()) {
                        logger.debug("{} refused to {} {}, trying another account", accountHandler.getThing().getUID(),
                                what, getThing().getUID());
                        refused.add(accountHandler);
                        continue;
                    }
                    e.log(logger, what, getThing().getUID());
                    failed.run();
                    break;
                }
            }
            scheduler.schedule(this::refreshSettings, READ_BACK_SECONDS, TimeUnit.SECONDS);
        });
    }

    /**
     * Switches the privacy mode through the cloud. The local API cannot change it, so without an account the
     * command is refused and the switch goes back to what the camera reports.
     */
    private void setPrivacyMode(boolean on) {
        // the camera confirms the change through the event subscription within a second
        inCloud("switch the privacy mode of", (api, id) -> api.setPrivacyMode(id, on, null),
                () -> updateState(CHANNEL_LOCAL_PRIVACY_MODE, OnOffType.from(privacyModeOn)));
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
        nextRetry = nextRetry.multipliedBy(RETRY_BACKOFF_FACTOR).compareTo(MAX_RETRY) > 0 ? MAX_RETRY
                : nextRetry.multipliedBy(RETRY_BACKOFF_FACTOR);
        connectJob = scheduler.schedule(this::connect, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * Answers a {@code REFRESH}, which openHAB sends for instance when an item is linked. Everything but the privacy
     * mode is known already, the event channels are taken from the event log.
     */
    private void refresh(String channelId) {
        switch (channelId) {
            case CHANNEL_LIGHT_FRONT, CHANNEL_LIGHT_TOP_BOTTOM, CHANNEL_LIGHT_MOTION, CHANNEL_ALARM_SIREN ->
                scheduler.execute(this::refreshSettings);
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
            case CHANNEL_LOCAL_RTSP_URL, CHANNEL_LOCAL_RTSP_SUBSTREAM_URL, CHANNEL_LOCAL_RTSPS_URL,
                    CHANNEL_LOCAL_RTSPS_SUBSTREAM_URL, CHANNEL_LOCAL_CAMERA_RTSPS_URL ->
                updateRtspUrls();
            case CHANNEL_CLOUD_LAST_CLIP_SNAPSHOT_URL -> {
                String id = lastImageEventId;
                if (id != null) {
                    updateState(channelId, new StringType(getEventFileUrl(id, EVENT_IMAGE_FILE)));
                }
            }
            case CHANNEL_CLOUD_LAST_CLIP_URL -> {
                String id = lastClipEventId;
                if (id != null) {
                    updateState(channelId, new StringType(getEventFileUrl(id, EVENT_CLIP_FILE)));
                }
            }
            case CHANNEL_CLOUD_EVENTS_API_URL -> updateEventsApiUrl();
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
                Objects.requireNonNullElse(event.propertyOperation(), PLAIN_EVENT), event.data());
        if (TOPIC_PRIVACY_MODE.equals(topic)) {
            updatePrivacyMode(event.isTrue(ITEM_STATE));
        } else if (TOPIC_ALARM_MODE.equals(topic)) {
            // reported when an alarm starts or stops, and after every new subscription, so nothing missed meanwhile
            // stays; what sounds is read from the camera rather than derived from the names of the modes
            scheduler.execute(this::refreshAlarm);
        } else if (TOPIC_LIGHT_FRONT.equals(topic) || TOPIC_LIGHT_TOP.equals(topic)
                || TOPIC_LIGHT_BOTTOM.equals(topic)) {
            updateLight(topic, brightness(event.get(ITEM_BRIGHTNESS)));
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
                followCloudEvent(time.toInstant(), kind);
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
     * @return the MAC address the thing was made for: the one seen before, or the id of the thing when that is a MAC
     *         address, as discovery names them; {@code null} for a thing named otherwise that never saw its camera.
     *         The id matters for things from files, which lose their properties on every restart.
     */
    private @Nullable String expectedMacAddress() {
        String seen = getThing().getProperties().get(Thing.PROPERTY_MAC_ADDRESS);
        if (seen != null && !seen.isBlank()) {
            return seen;
        }
        return CameraIdentity.normalizeMacAddress(getThing().getUID().getId());
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
        String expected = expectedMacAddress();
        if (expected != null && !expected.equals(found.macAddress())) {
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
        properties.put(Thing.PROPERTY_VENDOR, VENDOR);
        properties.put(Thing.PROPERTY_MAC_ADDRESS, found.macAddress());
        putIfPresent(properties, Thing.PROPERTY_SERIAL_NUMBER, found.serialNumber());
        try {
            putIfPresent(properties, Thing.PROPERTY_FIRMWARE_VERSION, localClient.getFirmwareVersion());
        } catch (BoschSmartCamException e) {
            reportLocalFailure(e);
            return false;
        }
        CameraModel model = null;
        try {
            String code = localClient.getModelCode();
            putIfPresent(properties, Thing.PROPERTY_MODEL_ID, code);
            model = CameraModel.forCode(code);
            if (model != null) {
                properties.put(PROPERTY_PRODUCT_NAME, model.getProductName());
            }
        } catch (BoschSmartCamException e) {
            // only the light channels depend on it, everything else works without
            logger.debug("Could not read the model of {}: {}", getThing().getUID(), e.getMessage());
        }
        updateProperties(properties);
        identity = found;
        // from now on every connection to the host has to present exactly this camera
        cameraTrust.bind(config.host, found.macAddress());

        // only now: openHAB drops state updates of a handler that is still initializing
        updateState(CHANNEL_LOCAL_SNAPSHOT_URL, new StringType(getSnapshotUrl()));
        updateRtspUrls();
        updateEventsApiUrl();
        if (model != null) {
            updateLightChannels(model == CameraModel.EYES_OUTDOOR_II);
        }
        scheduler.execute(this::refreshSettings);
        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        if (accountHandler != null) {
            updateFromCloud(accountHandler);
        }
        return true;
    }

    private void reportLocalFailure(BoschSmartCamException e) {
        if (e.getHttpStatus() == HttpStatus.UNAUTHORIZED_401) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error.local-credentials");
        } else {
            logger.debug("Talking to {} failed: {}", getThing().getUID(), e.getMessage());
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "@text/offline.comm-error.camera [\"" + BoschSmartCamException.asTextArgument(e.getReason())
                            + "\"]");
        }
    }

    /**
     * Takes over the id the cloud knows this camera by. Every account calls this after each of its polls; an account
     * that does not know the camera is ignored, the first one that does serves it from then on.
     */
    public void updateFromCloud(BoschSmartCamAccountHandler source) {
        CameraIdentity localIdentity = identity;
        if (localIdentity == null) {
            return;
        }
        String id;
        try {
            id = source.findCameraId(localIdentity.macAddress());
        } catch (BoschSmartCamException e) {
            logger.debug("Could not look up {} in {}: {}", getThing().getUID(), source.getThing().getUID(),
                    e.getMessage());
            return;
        }
        if (id == null) {
            return;
        }
        BoschSmartCamAccountHandler current = account;
        if (current == null || !current.isOnline()) {
            account = source;
        }
        cameraId = id;
        if (!id.equals(getThing().getProperties().get(PROPERTY_CAMERA_ID))) {
            updateProperty(PROPERTY_CAMERA_ID, id);
        }
        // settings changed in the app are not reported by the camera, so they are read along with the account
        scheduler.execute(this::refreshSettings);
    }

    /**
     * Only the Eyes Outdoor Camera II has lights. The camera names its model in its ONVIF device information; the
     * lights show their state without an account, switching them needs one.
     */
    private void updateLightChannels(boolean hasLights) {
        List<Channel> existing = getThing().getChannelsOfGroup(GROUP_LIGHT);
        if (hasLights != existing.isEmpty()) {
            return;
        }
        if (!hasLights) {
            updateThing(editThing().withoutChannels(existing).build());
            return;
        }
        ThingHandlerCallback callback = getCallback();
        if (callback == null) {
            return;
        }
        // withChannels would replace all channels of the thing, so they are added one by one
        ThingBuilder builder = editThing();
        callback.createChannelBuilders(new ChannelGroupUID(getThing().getUID(), GROUP_LIGHT), GROUP_TYPE_LIGHT)
                .forEach(channel -> builder.withChannel(channel.build()));
        updateThing(builder.build());
    }

    /**
     * Reads what the camera does not report through the event subscription: the light settings and the alarm.
     */
    private void refreshSettings() {
        LocalCameraClient localClient = client;
        if (localClient == null || identity == null) {
            return;
        }
        try {
            if (getThing().getChannel(CHANNEL_LIGHT_FRONT) != null) {
                updateState(CHANNEL_LIGHT_MOTION, OnOffType.from(localClient.isMotionLightOn()));
                ManualLighting lighting = localClient.getLighting();
                updateLight(TOPIC_LIGHT_FRONT, brightness(lighting.front()));
                updateLight(TOPIC_LIGHT_TOP, brightness(lighting.top()));
                updateLight(TOPIC_LIGHT_BOTTOM, brightness(lighting.bottom()));
            }
        } catch (BoschSmartCamException e) {
            logger.debug("Could not read the settings of {}: {}", getThing().getUID(), e.getMessage());
        }
        refreshAlarm();
    }

    /**
     * Reads whether an alarm sounds.
     */
    private void refreshAlarm() {
        LocalCameraClient localClient = client;
        if (localClient == null) {
            return;
        }
        AlarmStatus status;
        try {
            status = localClient.getAlarmStatus();
        } catch (BoschSmartCamException e) {
            logger.debug("Could not read the alarm of {}: {}", getThing().getUID(), e.getMessage());
            return;
        }
        String type = status.alarmType();
        boolean sounding = type != null && !ALARM_NONE.equalsIgnoreCase(type);
        updateState(CHANNEL_ALARM_SIREN, OnOffType.from(sounding));
    }

    /**
     * A light shows ON while it shines, whatever made it: the buttons, motion or dusk.
     *
     * @param topic the light as the camera reports it, e.g. {@code LightStatusTop}
     */
    private void updateLight(String topic, int brightness) {
        if (getThing().getChannel(CHANNEL_LIGHT_FRONT) == null) {
            // no lights, or the account has not told the model yet
            return;
        }
        switch (topic) {
            case TOPIC_LIGHT_FRONT -> updateState(CHANNEL_LIGHT_FRONT, OnOffType.from(brightness > 0));
            case TOPIC_LIGHT_TOP -> topBrightness = brightness;
            case TOPIC_LIGHT_BOTTOM -> bottomBrightness = brightness;
            default -> {
            }
        }
        if (!TOPIC_LIGHT_FRONT.equals(topic)) {
            synchronized (this) {
                ScheduledFuture<?> pending = topBottomUpdate;
                if (pending != null) {
                    pending.cancel(false);
                }
                topBottomUpdate = scheduler.schedule(
                        () -> updateState(CHANNEL_LIGHT_TOP_BOTTOM,
                                OnOffType.from(topBrightness > 0 || bottomBrightness > 0)),
                        TOP_BOTTOM_SETTLE_MILLIS, TimeUnit.MILLISECONDS);
            }
        }
    }

    private static int brightness(@Nullable LightSettings light) {
        return light == null ? 0 : light.brightnessOrZero();
    }

    private static int brightness(@Nullable String reported) {
        try {
            return reported == null ? 0 : (int) Math.round(Double.parseDouble(reported));
        } catch (NumberFormatException e) {
            return 0;
        }
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
        if (localClient == null || identity == null) {
            // until the camera at the host proved to be this one, its login is not sent there
            throw new BoschSmartCamException("The camera is not initialized or not confirmed yet");
        }
        return localClient.getSnapshot(snapshotCache);
    }

    /**
     * @param remoteAddress address a request for the snapshot, a stream or the events came from
     * @return whether that address is in the networks the binding allows
     */
    public boolean isAllowedFrom(String remoteAddress) {
        return context.getAllowedNetworks().matches(remoteAddress);
    }

    /**
     * None of the addresses carries user or password. Through the gateway none is needed, plain or over TLS, the token
     * in the address stands in for them; the one directly at the camera asks for the ones of the camera.
     */
    /**
     * Sets the stream addresses again, after the RTSP gateway of the binding was started, stopped or moved to another
     * port.
     */
    public void gatewayChanged() {
        if (identity != null) {
            updateRtspUrls();
        }
    }

    private void updateRtspUrls() {
        updateState(CHANNEL_LOCAL_CAMERA_RTSPS_URL,
                new StringType(RTSPS_SCHEME + config.host + ":" + RTSP_PORT + RTSP_PATH));
        String openhabHost = URI.create(openhabBaseUrl).getHost();
        int gatewayPort = context.getRtspGatewayPort();
        String gateway = gatewayPort <= 0 || openhabHost == null ? null
                : openhabHost + ":" + gatewayPort + "/" + accessToken;
        updateState(CHANNEL_LOCAL_RTSP_URL, gateway == null ? UnDefType.UNDEF : new StringType(RTSP_SCHEME + gateway));
        updateState(CHANNEL_LOCAL_RTSP_SUBSTREAM_URL,
                gateway == null ? UnDefType.UNDEF : new StringType(RTSP_SCHEME + gateway + RTSP_SUBSTREAM_QUERY));

        X509Certificate certificate = gateway == null ? null : context.getRtspGatewayCertificate();
        String secureGateway = certificate == null ? null : RTSPS_SCHEME + gateway;
        updateState(CHANNEL_LOCAL_RTSPS_URL, secureGateway == null ? UnDefType.UNDEF : new StringType(secureGateway));
        updateState(CHANNEL_LOCAL_RTSPS_SUBSTREAM_URL,
                secureGateway == null ? UnDefType.UNDEF : new StringType(secureGateway + RTSP_SUBSTREAM_QUERY));
        updateProperty(PROPERTY_RTSPS_CERTIFICATE, certificate == null ? null : pem(certificate));
        updateProperty(PROPERTY_RTSPS_CERTIFICATE_SHA256, certificate == null ? null : sha256(certificate));
    }

    private static @Nullable String pem(X509Certificate certificate) {
        try {
            return PEM_BEGIN + Base64.getMimeEncoder(PEM_LINE_LENGTH, PEM_LINE_SEPARATOR)
                    .encodeToString(certificate.getEncoded()) + PEM_END;
        } catch (CertificateEncodingException e) {
            return null;
        }
    }

    /**
     * @return the fingerprint in the form browsers and {@code openssl x509 -fingerprint -sha256} show it
     */
    private static @Nullable String sha256(X509Certificate certificate) {
        try {
            byte[] digest = MessageDigest.getInstance(SHA_256).digest(certificate.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
        } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
            return null;
        }
    }

    /**
     * A stream is only offered the way its address is linked to an item: whoever does not want the stream over plain
     * RTSP, or at all, leaves those channels unlinked. The token alone, known from the snapshot address for instance,
     * is not enough.
     *
     * @param secure whether the player came over TLS
     * @return what the RTSP gateway needs to play this camera for a player, or {@code null} if it offers no stream
     *         that way
     */
    public RtspGateway.@Nullable Target getRtspTarget(boolean secure) {
        boolean linked = secure ? isLinked(CHANNEL_LOCAL_RTSPS_URL) || isLinked(CHANNEL_LOCAL_RTSPS_SUBSTREAM_URL)
                : isLinked(CHANNEL_LOCAL_RTSP_URL) || isLinked(CHANNEL_LOCAL_RTSP_SUBSTREAM_URL);
        if (!linked || client == null || identity == null) {
            // until the camera at the host proved to be this one, its login is not sent there
            return null;
        }
        return new RtspGateway.Target(config.host, config.user, config.password, config.trustAllCertificates,
                this::isAllowedFrom);
    }

    /**
     * Like a stream, the still image is only offered while its address is linked to an item.
     */
    public boolean offersSnapshot() {
        return isLinked(CHANNEL_LOCAL_SNAPSHOT_URL);
    }

    /**
     * Finds the event the cloud keeps for one detected locally, to offer its image and, once uploaded, its clip. Only
     * done with an account and only for events, so the cloud is not polled otherwise. The channels follow the latest
     * local event only; a lookup that a newer event overtook still fires {@code clip-ready}.
     */
    private void followCloudEvent(Instant localTime, String kind) {
        if (getAccountHandler() == null) {
            return;
        }
        latestLocalEvent = localTime;
        cloudEvents.invalidate();
        scheduleCloudLookup(localTime, kind, 0, false);
    }

    private void scheduleCloudLookup(Instant localTime, String kind, int attempt, boolean imagePublished) {
        if (attempt >= CLOUD_LOOKUP_DELAYS_SECONDS.length || client == null) {
            return;
        }
        ScheduledFuture<?>[] self = new ScheduledFuture<?>[1];
        self[0] = scheduler.schedule(() -> {
            cloudLookups.remove(self[0]);
            lookUpCloudEvent(localTime, kind, attempt, imagePublished);
        }, CLOUD_LOOKUP_DELAYS_SECONDS[attempt], TimeUnit.SECONDS);
        cloudLookups.add(self[0]);
    }

    private void lookUpCloudEvent(Instant localTime, String kind, int attempt, boolean imagePublished) {
        if (client == null) {
            // disposed in the meantime
            return;
        }
        CloudEvent event;
        try {
            event = cloudEvents.matching(localTime, CLOUD_MATCH_WINDOW);
        } catch (BoschSmartCamException e) {
            logger.debug("Could not look up the event of {} in the cloud: {}", getThing().getUID(), e.getMessage());
            event = null;
        }
        String id = event == null ? null : event.id();
        if (event == null || id == null) {
            scheduleCloudLookup(localTime, kind, attempt + 1, imagePublished);
            return;
        }
        boolean latest = latestLocalEvent.equals(localTime);
        if (!imagePublished && latest && event.imageUrl() != null) {
            lastImageEventId = id;
            updateState(CHANNEL_CLOUD_LAST_CLIP_SNAPSHOT_URL, new StringType(getEventFileUrl(id, EVENT_IMAGE_FILE)));
        }
        if (event.clipState() == CloudEvent.ClipState.READY) {
            logger.debug("The clip of the {} event of {} is in the cloud", kind, getThing().getUID());
            if (latest) {
                lastClipEventId = id;
                updateState(CHANNEL_CLOUD_LAST_CLIP_URL, new StringType(getEventFileUrl(id, EVENT_CLIP_FILE)));
            }
            triggerChannel(CHANNEL_CLOUD_CLIP_READY, kind);
            return;
        }
        if (event.clipState() == CloudEvent.ClipState.PENDING) {
            scheduleCloudLookup(localTime, kind, attempt + 1, true);
        }
    }

    private List<CloudEvent> fetchCloudEvents(int page, int pageSize) throws BoschSmartCamException {
        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        String id = accountHandler == null ? null : resolveCameraId(accountHandler);
        if (accountHandler == null || id == null) {
            throw new BoschSmartCamException("The events are kept in the cloud, that needs an account");
        }
        return accountHandler.getApi().getEvents(id, page, pageSize);
    }

    /**
     * @return whether the events the cloud keeps are offered as an API: switched on and its address linked
     */
    public boolean offersEventsApi() {
        return config.publishEventsApi && isLinked(CHANNEL_CLOUD_EVENTS_API_URL);
    }

    /**
     * Shows the address of the events API while it is switched on. It carries the token, so it is a channel like the
     * other addresses, not a property, and the API only answers while it is linked.
     */
    private void updateEventsApiUrl() {
        updateState(CHANNEL_CLOUD_EVENTS_API_URL,
                config.publishEventsApi ? new StringType(getUrl(EVENTS_PATH)) : UnDefType.UNDEF);
    }

    /**
     * @return the events the cloud keeps for this camera, for the events API
     */
    public CloudEventFeed getCloudEvents() {
        return cloudEvents;
    }

    /**
     * Like the snapshot, the image or clip of an event is only offered while its address is linked to an item, and
     * then only for the event the channel shows; the events API offers all of them.
     *
     * @param clip whether the clip is asked for, otherwise the image
     */
    public boolean offersEventMedia(String eventId, boolean clip) {
        if (offersEventsApi()) {
            return true;
        }
        return clip ? isLinked(CHANNEL_CLOUD_LAST_CLIP_URL) && eventId.equals(lastClipEventId)
                : isLinked(CHANNEL_CLOUD_LAST_CLIP_SNAPSHOT_URL) && eventId.equals(lastImageEventId);
    }

    /**
     * Opens the image or clip of an event of this camera in the cloud.
     *
     * @throws BoschSmartCamException if the event is unknown, belongs to another camera or has no such file yet
     */
    public BoschSmartCamApi.Media openEventMedia(String eventId, boolean clip) throws BoschSmartCamException {
        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        if (accountHandler == null) {
            throw new BoschSmartCamException("The events are kept in the cloud, that needs an account");
        }
        CloudEvent event = cloudEvents.find(eventId, clip);
        String url = event == null ? null : clip ? event.videoClipUrl() : event.imageUrl();
        if (url == null) {
            throw new BoschSmartCamException("No " + (clip ? "clip" : "image") + " for event " + eventId,
                    HttpStatus.NOT_FOUND_404);
        }
        return accountHandler.getApi().openMedia(url);
    }

    private String getEventFileUrl(String eventId, String file) {
        return getUrl(EVENTS_PATH + "/" + eventId + "/" + file);
    }

    /**
     * @return the address openHAB serves the snapshot of this camera at
     */
    public String getSnapshotUrl() {
        return getUrl(SNAPSHOT_FILE);
    }

    private String getUrl(String file) {
        return openhabBaseUrl + SERVLET_PATH + "/" + accessToken + "/" + file;
    }

    /**
     * @return the label of the camera thing, or its id if it has none
     */
    public String getLabel() {
        String label = getThing().getLabel();
        return label == null || label.isBlank() ? getThing().getUID().getId() : label;
    }

    /**
     * The configured token, otherwise the one of a previous run so links stay valid, otherwise a new one. It is kept
     * in the storage of openHAB, which unlike a thing property also survives a restart for things defined in files;
     * it is not shown as a property, as it opens the snapshot, the streams and the events. The addresses carry it.
     */
    private String currentOrNewAccessToken() {
        String uid = getThing().getUID().getAsString();
        String token = config.accessToken.strip();
        if (token.isEmpty()) {
            token = accessTokens.get(uid);
        }
        if (token == null || token.isBlank()) {
            token = UUID.randomUUID().toString();
        }
        accessTokens.put(uid, token);
        return token;
    }

    private static void putIfPresent(Map<String, String> properties, String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            properties.put(key, value);
        }
    }

    private @Nullable BoschSmartCamAccountHandler getAccountHandler() {
        return getAccountHandler(Set.of());
    }

    /**
     * @return the id of the camera in the cloud, once an account told it
     */
    public @Nullable String getCameraId() {
        return cameraId;
    }

    /**
     * @return the MAC address of the camera, once its certificate was read
     */
    public @Nullable String getMacAddress() {
        CameraIdentity localIdentity = identity;
        return localIdentity == null ? null : localIdentity.macAddress();
    }

    /**
     * The account the camera is served by: the one used before while it is online, otherwise the first online account
     * that knows the MAC address of the camera. The cloud settings of a camera are the same through every account.
     *
     * @param refused accounts that were not allowed to do what is asked and are skipped
     * @return the account, or {@code null} if no account knows the camera
     */
    private @Nullable BoschSmartCamAccountHandler getAccountHandler(Set<BoschSmartCamAccountHandler> refused) {
        BoschSmartCamAccountHandler current = account;
        if (current != null && current.isOnline() && !refused.contains(current)) {
            return current;
        }
        CameraIdentity localIdentity = identity;
        if (localIdentity == null) {
            return null;
        }
        for (BoschSmartCamAccountHandler candidate : authService.getAccountHandlers()) {
            if (!candidate.isOnline() || refused.contains(candidate)) {
                continue;
            }
            try {
                String id = candidate.findCameraId(localIdentity.macAddress());
                String known = cameraId;
                if (id == null && known != null && candidate.knowsCamera(known)) {
                    // the camera is only shared with this account, which cannot read its MAC address
                    id = known;
                }
                if (id != null) {
                    if (refused.isEmpty()) {
                        account = candidate;
                    }
                    cameraId = id;
                    return candidate;
                }
            } catch (BoschSmartCamException e) {
                logger.debug("Could not look up {} in {}: {}", getThing().getUID(), candidate.getThing().getUID(),
                        e.getMessage());
            }
        }
        return null;
    }
}
