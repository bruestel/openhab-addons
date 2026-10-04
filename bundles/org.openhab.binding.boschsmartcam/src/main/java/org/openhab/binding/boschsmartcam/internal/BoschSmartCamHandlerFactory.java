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

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Map;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.security.Password;
import org.openhab.binding.boschsmartcam.internal.auth.BoschSmartCamAuthService;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamAccountHandler;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamCameraHandler;
import org.openhab.binding.boschsmartcam.internal.handler.CameraContext;
import org.openhab.binding.boschsmartcam.internal.local.CameraTrust;
import org.openhab.binding.boschsmartcam.internal.local.RtspGateway;
import org.openhab.binding.boschsmartcam.internal.net.CidrMatcher;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.net.HttpServiceUtil;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
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
     * Networks that may fetch the snapshot, stream and event addresses, unless the binding configuration says
     * otherwise. Loopback plus the private and link-local ranges of IPv4 and IPv6, so the images do not leave the
     * local network even if a link does.
     */
    private static final String DEFAULT_ALLOWED_NETWORKS = "127.0.0.0/8, ::1/128, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 169.254.0.0/16, fc00::/7, fe80::/10";

    private static final String CONFIG_ALLOWED_NETWORKS = "allowedNetworks";

    /**
     * Where openHAB is assumed when it names neither its address nor its port, which are its defaults.
     */
    private static final String FALLBACK_HOST = "localhost";
    private static final int FALLBACK_HTTP_PORT = 8080;
    private static final String CONFIG_RTSP_PORT = "rtspPort";
    /**
     * Connecting to the camera and the TLS handshake. Short, so a player gets an answer quickly should the stream
     * service of the camera hang.
     */
    private static final int CAMERA_CONNECT_TIMEOUT_MILLIS = 5_000;

    /**
     * The system properties openHAB hands its keystore to Jetty with, see {@code runtime/etc/jetty.xml}. The defaults
     * are those of the keystore openHAB generates itself.
     */
    private static final String KEYSTORE_PATH_PROPERTY = "jetty.keystore.path";
    private static final String KEYSTORE_PASSWORD_PROPERTY = "jetty.ssl.password";
    private static final String KEY_PASSWORD_PROPERTY = "jetty.ssl.keypassword";
    private static final String DEFAULT_KEYSTORE_PASSWORD = "openhab";
    private static final String OBFUSCATED = "OBF:";

    /**
     * Talking to a camera needs its own client: the cameras carry a certificate from the Bosch device PKI and are
     * addressed by IP, while the name in the certificate is their MAC address.
     */
    private final HttpClient cameraHttpClient;
    private final CameraTrust cameraTrust = new CameraTrust();
    private final HttpClientFactory httpClientFactory;

    /**
     * Client for cameras that are configured to accept any certificate. Created on first use, so the warning Jetty
     * logs for such a client only shows up when somebody actually asked for it.
     */
    private @Nullable HttpClient trustAllHttpClient;

    /**
     * The tokens of the cameras by thing UID. Kept here rather than in a thing property, which openHAB does not keep
     * for things defined in files.
     */
    private final Storage<String> accessTokens;

    private volatile CidrMatcher allowedNetworks = new CidrMatcher(DEFAULT_ALLOWED_NETWORKS);

    /**
     * Offers the streams of all cameras on one port, plain and over TLS, see {@link RtspGateway}.
     */
    private volatile @Nullable RtspGateway rtspGateway;
    private volatile int rtspGatewayPort;
    /**
     * The certificate players over TLS see, {@code null} while the gateway offers plain RTSP only.
     */
    private volatile @Nullable X509Certificate rtspGatewayCertificate;

    @Activate
    public BoschSmartCamHandlerFactory(final @Reference OAuthFactory oAuthFactory,
            final @Reference HttpClientFactory httpClientFactory, final @Reference BoschSmartCamAuthService authService,
            final @Reference NetworkAddressService networkAddressService,
            final @Reference StorageService storageService) {
        this.oAuthFactory = oAuthFactory;
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.authService = authService;
        this.networkAddressService = networkAddressService;
        this.httpClientFactory = httpClientFactory;
        this.accessTokens = storageService.getStorage(BINDING_ID + ".accessTokens");

        cameraHttpClient = httpClientFactory.createHttpClient(CAMERA_HTTP_CLIENT_NAME,
                cameraTrust.createSslContextFactory());
    }

    @Override
    protected void activate(ComponentContext componentContext) {
        super.activate(componentContext);
        applyConfiguration(componentContext.getProperties().get(CONFIG_ALLOWED_NETWORKS),
                componentContext.getProperties().get(CONFIG_RTSP_PORT));
        try {
            cameraHttpClient.start();
        } catch (Exception e) {
            logger.warn("Could not start the client for the cameras, snapshots will not work: {}", e.getMessage());
        }
    }

    @Override
    protected void deactivate(ComponentContext componentContext) {
        stopRtspGateway();
        stop(cameraHttpClient);
        HttpClient trustAll = trustAllHttpClient;
        if (trustAll != null) {
            stop(trustAll);
            trustAllHttpClient = null;
        }
        super.deactivate(componentContext);
    }

    private void stop(HttpClient client) {
        try {
            client.stop();
        } catch (Exception e) {
            logger.debug("Could not stop the client for the cameras", e);
        }
    }

    private final CameraContext cameraContext = new CameraContext() {
        @Override
        public HttpClient getTrustAllHttpClient() {
            return BoschSmartCamHandlerFactory.this.getTrustAllHttpClient();
        }

        @Override
        public CidrMatcher getAllowedNetworks() {
            return allowedNetworks;
        }

        @Override
        public int getRtspGatewayPort() {
            return rtspGatewayPort;
        }

        @Override
        public @Nullable X509Certificate getRtspGatewayCertificate() {
            return rtspGatewayCertificate;
        }
    };

    private synchronized HttpClient getTrustAllHttpClient() {
        HttpClient client = trustAllHttpClient;
        if (client == null) {
            client = httpClientFactory.createHttpClient(TRUST_ALL_HTTP_CLIENT_NAME,
                    cameraTrust.createTrustAllSslContextFactory());
            try {
                client.start();
            } catch (Exception e) {
                logger.warn("Could not start the client for the cameras that trust any certificate: {}",
                        e.getMessage());
            }
            trustAllHttpClient = client;
        }
        return client;
    }

    @Modified
    protected void modified(Map<String, Object> configuration) {
        applyConfiguration(configuration.get(CONFIG_ALLOWED_NETWORKS), configuration.get(CONFIG_RTSP_PORT));
    }

    private void applyConfiguration(@Nullable Object networks, @Nullable Object rtspPort) {
        allowedNetworks = new CidrMatcher(
                networks instanceof String value && !value.isBlank() ? value : DEFAULT_ALLOWED_NETWORKS);
        int port = 0;
        if (rtspPort instanceof Number number) {
            port = number.intValue();
        } else if (rtspPort instanceof String text && !text.isBlank()) {
            try {
                port = Integer.parseInt(text.strip());
            } catch (NumberFormatException e) {
                logger.warn("Ignoring the RTSP port '{}', it is no number", text);
            }
        }
        if (port != rtspGatewayPort || (port > 0 && rtspGateway == null)) {
            startRtspGateway(port);
            // the cameras show the addresses of the gateway, which have just changed
            authService.getCameraHandlers().forEach(BoschSmartCamCameraHandler::gatewayChanged);
        }
    }

    private synchronized void startRtspGateway(int port) {
        stopRtspGateway();
        rtspGatewayPort = 0;
        rtspGatewayCertificate = null;
        if (port <= 0) {
            return;
        }
        OpenhabTls tls = loadOpenhabTls();
        RtspGateway gateway = new RtspGateway(port,
                (token, secure) -> authService.getCamera(token).map(camera -> camera.getRtspTarget(secure))
                        .orElse(null),
                target -> cameraTrust.openSocket(target.host(), RTSP_PORT, target.trustAll(),
                        CAMERA_CONNECT_TIMEOUT_MILLIS),
                tls == null ? null : tls.context());
        try {
            gateway.start();
            rtspGateway = gateway;
            rtspGatewayPort = port;
            rtspGatewayCertificate = tls == null ? null : tls.certificate();
        } catch (IOException e) {
            logger.warn("Could not offer the camera streams on port {}: {}", port, e.getMessage());
        }
    }

    /**
     * Loads the certificate openHAB serves HTTPS with, so players over TLS get the same one. Whoever replaced it with
     * one of their own gets that one here too.
     *
     * @return the context and its certificate, or {@code null} if the keystore cannot be read; the gateway then offers
     *         plain RTSP only
     */
    private @Nullable OpenhabTls loadOpenhabTls() {
        String path = System.getProperty(KEYSTORE_PATH_PROPERTY);
        if (path == null || path.isBlank()) {
            logger.debug("openHAB names no keystore, the camera streams are offered without TLS");
            return null;
        }
        String storePasswordProperty = System.getProperty(KEYSTORE_PASSWORD_PROPERTY);
        char[] storePassword = password(
                storePasswordProperty == null ? DEFAULT_KEYSTORE_PASSWORD : storePasswordProperty);
        String keyPasswordProperty = System.getProperty(KEY_PASSWORD_PROPERTY);
        char[] keyPassword = keyPasswordProperty == null ? storePassword : password(keyPasswordProperty);
        try {
            // detects JKS, which openHAB generates, as well as PKCS12, which a replaced keystore may be
            KeyStore keyStore = KeyStore.getInstance(new File(path), storePassword);
            X509Certificate certificate = serverCertificate(keyStore);
            if (certificate == null) {
                logger.warn("The keystore of openHAB holds no certificate, the camera streams are offered without TLS");
                return null;
            }
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, keyPassword);
            SSLContext context = SSLContext.getInstance(TLS);
            context.init(keyManagers.getKeyManagers(), null, null);
            return new OpenhabTls(context, certificate);
        } catch (IOException | GeneralSecurityException e) {
            logger.warn("Could not read the keystore of openHAB, the camera streams are offered without TLS: {}",
                    e.getMessage());
            return null;
        }
    }

    /**
     * @return the password, resolved if it is obfuscated the way Jetty writes it ({@code OBF:...}), as openHAB passes
     *         it on to Jetty unchanged
     */
    private static char[] password(String value) {
        return (value.startsWith(OBFUSCATED) ? Password.deobfuscate(value) : value).toCharArray();
    }

    private static @Nullable X509Certificate serverCertificate(KeyStore keyStore) throws KeyStoreException {
        for (String alias : Collections.list(keyStore.aliases())) {
            if (keyStore.isKeyEntry(alias) && keyStore.getCertificate(alias) instanceof X509Certificate certificate) {
                return certificate;
            }
        }
        return null;
    }

    private record OpenhabTls(SSLContext context, X509Certificate certificate) {
    }

    private synchronized void stopRtspGateway() {
        RtspGateway gateway = rtspGateway;
        if (gateway != null) {
            gateway.stop();
            rtspGateway = null;
        }
    }

    /**
     * @return the address openHAB can be reached at, used to build the snapshot URLs
     */
    private String getOpenhabBaseUrl() {
        String host = networkAddressService.getPrimaryIpv4HostAddress();
        int port = HttpServiceUtil.getHttpServicePort(bundleContext);
        return HTTP_SCHEME + (host == null || host.isBlank() ? FALLBACK_HOST : host) + ":"
                + (port > 0 ? port : FALLBACK_HTTP_PORT);
    }

    /**
     * Removing a camera revokes its addresses: added again, it gets a new token. openHAB calls this when a thing is
     * removed for good, from the UI as well as from a file, but not when it shuts down.
     */
    @Override
    public void removeThing(ThingUID thingUID) {
        // only cameras have a token, for any other thing this does nothing
        accessTokens.remove(thingUID.getAsString());
        super.removeThing(thingUID);
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_ACCOUNT.equals(thingTypeUID)) {
            return new BoschSmartCamAccountHandler(thing, oAuthFactory, httpClient, authService);
        } else if (THING_TYPE_CAMERA.equals(thingTypeUID)) {
            return new BoschSmartCamCameraHandler(thing, authService, cameraHttpClient, cameraContext, cameraTrust,
                    accessTokens, getOpenhabBaseUrl());
        }

        return null;
    }
}
