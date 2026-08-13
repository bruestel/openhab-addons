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
package org.openhab.binding.boschsmartcam.internal.discovery;

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.CONFIG_CAMERA_ID;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.THING_TYPE_CAMERA;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.boschsmartcam.internal.api.dto.VideoInput;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamAccountHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * Discovers the cameras of an authorized account.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = BoschSmartCamDiscoveryService.class)
@NonNullByDefault
public class BoschSmartCamDiscoveryService extends AbstractThingHandlerDiscoveryService<BoschSmartCamAccountHandler> {

    private static final int DISCOVERY_TIMEOUT_SECONDS = 10;

    public BoschSmartCamDiscoveryService() {
        super(BoschSmartCamAccountHandler.class, Set.of(THING_TYPE_CAMERA), DISCOVERY_TIMEOUT_SECONDS, false);
    }

    @Override
    protected void startScan() {
        BoschSmartCamAccountHandler accountHandler = thingHandler;
        accountHandler.poll();

        ThingUID bridgeUid = accountHandler.getThing().getUID();
        for (VideoInput camera : accountHandler.getCameras()) {
            String cameraId = camera.id;
            if (cameraId == null || cameraId.isBlank()) {
                continue;
            }

            Map<String, Object> properties = new HashMap<>();
            properties.put(CONFIG_CAMERA_ID, cameraId);
            properties.put(Thing.PROPERTY_VENDOR, "Bosch");
            String hardwareVersion = camera.hardwareVersion;
            if (hardwareVersion != null) {
                properties.put(Thing.PROPERTY_MODEL_ID, hardwareVersion);
            }

            String title = camera.title;
            ThingUID thingUid = new ThingUID(THING_TYPE_CAMERA, bridgeUid, cameraId.replaceAll("[^a-zA-Z0-9_]", ""));
            thingDiscovered(DiscoveryResultBuilder.create(thingUid).withBridge(bridgeUid).withProperties(properties)
                    .withRepresentationProperty(CONFIG_CAMERA_ID)
                    .withLabel(title == null || title.isBlank() ? "Bosch Smart Home Camera" : title).build());
        }
    }
}
