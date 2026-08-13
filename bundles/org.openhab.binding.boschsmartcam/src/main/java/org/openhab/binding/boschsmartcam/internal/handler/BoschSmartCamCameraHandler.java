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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.BoschSmartCamCameraConfiguration;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.VideoInput;
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

    private final Logger logger = LoggerFactory.getLogger(BoschSmartCamCameraHandler.class);

    private String cameraId = "";

    public BoschSmartCamCameraHandler(Thing thing) {
        super(thing);
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

        updateStatus(ThingStatus.UNKNOWN);

        BoschSmartCamAccountHandler accountHandler = getAccountHandler();
        if (accountHandler != null) {
            updateFromCameras(accountHandler.getCameras());
        }
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
     * Updates the channels from the last poll of the account bridge.
     */
    public void updateFromCameras(List<VideoInput> cameras) {
        VideoInput camera = cameras.stream().filter(input -> cameraId.equals(input.id)).findFirst().orElse(null);
        if (camera == null) {
            if (!cameras.isEmpty()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.GONE, "@text/offline.camera-not-in-account");
            }
            return;
        }

        updateProperties(camera);

        if (camera.isOnline()) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.NONE, camera.status);
        }

        updateState(CHANNEL_PRIVACY_MODE, OnOffType.from(camera.isPrivacyModeOn()));
        updateState(CHANNEL_NOTIFICATIONS, OnOffType.from(camera.areNotificationsEnabled()));
        String status = camera.status;
        updateState(CHANNEL_STATUS, status == null ? UnDefType.UNDEF : new StringType(status));
    }

    private void updateProperties(VideoInput camera) {
        Map<String, String> properties = new HashMap<>(editProperties());
        properties.put(Thing.PROPERTY_VENDOR, "Bosch");
        putIfPresent(properties, Thing.PROPERTY_MODEL_ID, camera.hardwareVersion);
        putIfPresent(properties, Thing.PROPERTY_FIRMWARE_VERSION, camera.firmwareVersion);
        putIfPresent(properties, CONFIG_CAMERA_ID, camera.id);
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
