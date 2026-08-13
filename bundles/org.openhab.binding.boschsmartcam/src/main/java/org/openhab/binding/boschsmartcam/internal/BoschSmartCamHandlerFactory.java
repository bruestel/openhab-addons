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

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.openhab.binding.boschsmartcam.internal.auth.BoschSmartCamAuthService;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamAccountHandler;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamCameraHandler;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.net.HttpServiceUtil;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BoschSmartCamHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.boschsmartcam", service = ThingHandlerFactory.class)
public class BoschSmartCamHandlerFactory extends BaseThingHandlerFactory {

    private final Logger logger = LoggerFactory.getLogger(BoschSmartCamHandlerFactory.class);

    private final OAuthFactory oAuthFactory;
    private final HttpClient httpClient;
    private final BoschSmartCamAuthService authService;
    private final NetworkAddressService networkAddressService;

    /**
     * Talking to a camera needs its own client: the cameras carry a per device certificate from a Bosch PKI that
     * cannot be validated from here, and they are addressed by IP, so the name in the certificate never matches.
     */
    private final HttpClient cameraHttpClient;

    @Activate
    public BoschSmartCamHandlerFactory(final @Reference OAuthFactory oAuthFactory,
            final @Reference HttpClientFactory httpClientFactory, final @Reference BoschSmartCamAuthService authService,
            final @Reference NetworkAddressService networkAddressService) {
        this.oAuthFactory = oAuthFactory;
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.authService = authService;
        this.networkAddressService = networkAddressService;

        SslContextFactory.Client sslContextFactory = new SslContextFactory.Client(true);
        sslContextFactory.setEndpointIdentificationAlgorithm(null);
        cameraHttpClient = httpClientFactory.createHttpClient("boschsmartcam", sslContextFactory);
    }

    @Override
    protected void activate(ComponentContext componentContext) {
        super.activate(componentContext);
        try {
            cameraHttpClient.start();
        } catch (Exception e) {
            logger.warn("Could not start the client for the cameras, snapshots will not work: {}", e.getMessage());
        }
    }

    @Override
    protected void deactivate(ComponentContext componentContext) {
        try {
            cameraHttpClient.stop();
        } catch (Exception e) {
            logger.debug("Could not stop the client for the cameras", e);
        }
        super.deactivate(componentContext);
    }

    /**
     * @return the address openHAB can be reached at, used to build the snapshot URLs
     */
    private String getOpenhabBaseUrl() {
        String host = networkAddressService.getPrimaryIpv4HostAddress();
        int port = HttpServiceUtil.getHttpServicePort(bundleContext);
        return "http://" + (host == null || host.isBlank() ? "localhost" : host) + ":" + (port > 0 ? port : 8080);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_ACCOUNT.equals(thingTypeUID) && thing instanceof Bridge bridge) {
            return new BoschSmartCamAccountHandler(bridge, oAuthFactory, httpClient, authService);
        } else if (THING_TYPE_CAMERA.equals(thingTypeUID)) {
            return new BoschSmartCamCameraHandler(thing, authService, cameraHttpClient, getOpenhabBaseUrl());
        }

        return null;
    }
}
