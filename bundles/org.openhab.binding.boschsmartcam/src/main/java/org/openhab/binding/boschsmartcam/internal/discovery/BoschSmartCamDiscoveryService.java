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

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.*;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLHandshakeException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.CameraModel;
import org.openhab.binding.boschsmartcam.internal.api.dto.VideoInput;
import org.openhab.binding.boschsmartcam.internal.api.dto.WifiInfo;
import org.openhab.binding.boschsmartcam.internal.auth.BoschSmartCamAuthService;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamAccountHandler;
import org.openhab.binding.boschsmartcam.internal.local.CameraIdentity;
import org.openhab.binding.boschsmartcam.internal.local.CameraTrust;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.i18n.LocaleProvider;
import org.openhab.core.i18n.TranslationProvider;
import org.openhab.core.net.CidrAddress;
import org.openhab.core.net.NetUtil;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds cameras in two steps.
 *
 * First in the networks openHAB is directly attached to. The cameras answer neither WS-Discovery nor mDNS, so every
 * address is probed: a camera has the port of the local API and the one of the RTSP tunnel open, and presents a
 * certificate below the Bosch device root. The tunnel only opens once the local API is enabled in the app, so cameras
 * that could not be used anyway are left out.
 *
 * Then every online account is asked for the address of each of its cameras that was not found, which covers cameras
 * in networks openHAB is not attached to. Such an address is only a hint: it is probed the same way, and the camera
 * found there has to have the MAC address the cloud names.
 *
 * If an online account knows the camera, the result is put under that account and takes over the name from the app.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
@Component(service = DiscoveryService.class, configurationPid = "discovery.boschsmartcam")
public class BoschSmartCamDiscoveryService extends AbstractDiscoveryService {

    private static final int SCAN_TIMEOUT_SECONDS = 60;

    /**
     * Larger networks are skipped, probing them would take too long. A /22 still has 1022 hosts.
     */
    private static final int MAX_PREFIX_LENGTH = 22;

    /**
     * A scan probes every address of the network and asks the online accounts for the cameras it did not find, so
     * in the background it is off unless enabled, and then only runs now and then.
     */
    private static final long BACKGROUND_INTERVAL_MINUTES = 30;
    private static final long BACKGROUND_DELAY_MINUTES = 1;

    /**
     * Every address probed in the own network gets an entry in the ARP table of the system, used or not. That table
     * holds 1024 entries by default, and probing faster than the kernel cleans up overflows it - which breaks every
     * connection of the system, not only the scan. About 128 new addresses per second has proven safe.
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 500;
    private static final int PARALLEL_PROBES = 64;

    private final Logger logger = LoggerFactory.getLogger(BoschSmartCamDiscoveryService.class);

    private final BoschSmartCamAuthService authService;
    private final CameraTrust cameraTrust = new CameraTrust();

    private @Nullable ScheduledFuture<?> backgroundJob;

    @Activate
    public BoschSmartCamDiscoveryService(final @Reference BoschSmartCamAuthService authService,
            final @Reference LocaleProvider localeProvider, final @Reference TranslationProvider i18nProvider) {
        super(Set.of(THING_TYPE_CAMERA), SCAN_TIMEOUT_SECONDS, false);
        this.authService = authService;
        // lets the base class translate the @text labels of the results
        this.localeProvider = localeProvider;
        this.i18nProvider = i18nProvider;
    }

    @Override
    protected void startBackgroundDiscovery() {
        ScheduledFuture<?> job = backgroundJob;
        if (job == null || job.isCancelled()) {
            backgroundJob = scheduler.scheduleWithFixedDelay(this::startScan, BACKGROUND_DELAY_MINUTES,
                    BACKGROUND_INTERVAL_MINUTES, TimeUnit.MINUTES);
        }
    }

    @Override
    protected void stopBackgroundDiscovery() {
        ScheduledFuture<?> job = backgroundJob;
        if (job != null) {
            job.cancel(true);
            backgroundJob = null;
        }
    }

    @Override
    protected void startScan() {
        List<InetAddress> addresses = new ArrayList<>();
        for (CidrAddress network : NetUtil.getAllInterfaceAddresses()) {
            if (!(network.getAddress() instanceof Inet4Address) || network.getAddress().isLoopbackAddress()) {
                continue;
            }
            if (network.getPrefix() < MAX_PREFIX_LENGTH) {
                logger.debug("Not scanning {}, the network is larger than /{}", network, MAX_PREFIX_LENGTH);
                continue;
            }
            List<InetAddress> hosts = hostsOf(network.getAddress().getAddress(), network.getPrefix());
            logger.debug("Scanning {} with {} addresses", network, hosts.size());
            addresses.addAll(hosts);
        }
        logger.debug("Probing {} addresses for cameras", addresses.size());
        long started = System.nanoTime();
        Set<String> found = ConcurrentHashMap.newKeySet();

        // most probes wait for a timeout, so they are cheap on virtual threads, where the shared schedulers of openHAB
        // would be blocked by them; the semaphore sets the pace
        Semaphore permits = new Semaphore(PARALLEL_PROBES);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (InetAddress address : addresses) {
                executor.execute(() -> {
                    try {
                        permits.acquire();
                        try {
                            String host = address.getHostAddress();
                            CameraIdentity identity = identify(host);
                            if (identity != null) {
                                found.add(identity.macAddress());
                                publish(host, identity);
                            }
                        } finally {
                            permits.release();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
        }
        logger.debug("Probed {} addresses in {} ms", addresses.size(), (System.nanoTime() - started) / 1_000_000);

        scanThroughAccounts(found);
    }

    /**
     * Probes the cameras of the online accounts that the local scan did not find, at the address the cloud names.
     *
     * @param found MAC addresses of the cameras found already
     */
    private void scanThroughAccounts(Set<String> found) {
        for (BoschSmartCamAccountHandler accountHandler : authService.getAccountHandlers()) {
            if (accountHandler.getThing().getStatus() != ThingStatus.ONLINE) {
                continue;
            }
            for (VideoInput camera : accountHandler.getCameras()) {
                String cameraId = camera.id();
                if (cameraId == null) {
                    continue;
                }
                String known = accountHandler.getKnownMacAddress(cameraId);
                if (known != null && found.contains(known)) {
                    continue;
                }
                WifiInfo wifiInfo;
                try {
                    wifiInfo = accountHandler.readWifiInfo(cameraId);
                } catch (BoschSmartCamException e) {
                    logger.debug("Could not read the address of {}: {}", camera.title(), e.getMessage());
                    continue;
                }
                String macAddress = wifiInfo.normalizedMacAddress();
                String host = wifiInfo.ipAddress();
                if (macAddress == null || host == null || host.isBlank() || found.contains(macAddress)) {
                    continue;
                }
                CameraIdentity identity = identify(host);
                if (identity == null) {
                    logger.debug("{} is at {} according to the cloud, but cannot be reached there", camera.title(),
                            host);
                } else if (!identity.macAddress().equals(macAddress)) {
                    logger.debug("The cloud places {} ({}) at {}, but {} answers there", camera.title(), macAddress,
                            host, identity.macAddress());
                } else {
                    found.add(macAddress);
                    publish(host, identity);
                }
            }
        }
    }

    /**
     * @return who answers at the host if it is a usable camera, {@code null} otherwise
     */
    private @Nullable CameraIdentity identify(String host) {
        // the tunnel port first: plenty of devices serve HTTPS, hardly any has 9554 open
        if (!isOpen(host, RTSP_PORT) || !isOpen(host, HTTPS_PORT)) {
            return null;
        }
        CameraIdentity identity;
        try {
            identity = cameraTrust.readIdentity(host, HTTPS_PORT, false);
        } catch (SSLHandshakeException e) {
            logger.debug("{} has the ports of a camera, but no certificate of the Bosch device root: {}", host,
                    e.getMessage());
            return null;
        } catch (IOException e) {
            logger.debug("Could not read the certificate of {}: {}", host, e.getMessage());
            return null;
        }
        logger.debug("Found camera {} at {}", identity.macAddress(), host);
        return identity;
    }

    private void publish(String host, CameraIdentity identity) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_HOST, host);
        properties.put(Thing.PROPERTY_MAC_ADDRESS, identity.macAddress());
        properties.put(Thing.PROPERTY_VENDOR, "Bosch");
        String serialNumber = identity.serialNumber();
        if (serialNumber != null) {
            properties.put(Thing.PROPERTY_SERIAL_NUMBER, serialNumber);
        }

        // the id does not name the account, so the thing keeps it when it is moved under one later
        DiscoveryResultBuilder result = DiscoveryResultBuilder
                .create(new ThingUID(THING_TYPE_CAMERA, identity.thingId())).withProperties(properties)
                .withRepresentationProperty(Thing.PROPERTY_MAC_ADDRESS)
                .withLabel("@text/discovery.camera.label [\"" + identity.macAddress() + "\"]");
        addCloudDetails(result, properties, identity.macAddress());
        thingDiscovered(result.build());
    }

    /**
     * Names the result like the camera is named in the app, as an online account that knows it tells.
     */
    private void addCloudDetails(DiscoveryResultBuilder result, Map<String, Object> properties, String macAddress) {
        for (BoschSmartCamAccountHandler accountHandler : authService.getAccountHandlers()) {
            if (accountHandler.getThing().getStatus() != ThingStatus.ONLINE) {
                continue;
            }
            VideoInput camera;
            try {
                camera = accountHandler.findCamera(macAddress);
            } catch (BoschSmartCamException e) {
                logger.debug("Could not look up {} in {}: {}", macAddress, accountHandler.getThing().getUID(),
                        e.getMessage());
                continue;
            }
            if (camera == null) {
                continue;
            }
            String cameraId = camera.id();
            if (cameraId != null) {
                properties.put(PROPERTY_CAMERA_ID, cameraId);
            }
            CameraModel model = camera.model();
            if (model != null) {
                properties.put(PROPERTY_PRODUCT_NAME, model.getProductName());
            }
            result.withProperties(properties).withLabel(buildLabel(camera.title(), model));
            return;
        }
    }

    /**
     * Lists every host address of an IPv4 network, without its network and broadcast address.
     * {@code NetUtil.getAddressesRangeByCidrAddress} is not used, it returns far too many addresses for a /22.
     */
    static List<InetAddress> hostsOf(byte[] address, int prefix) {
        int value = ByteBuffer.wrap(address).getInt();
        int mask = prefix == 0 ? 0 : -1 << (32 - prefix);
        int network = value & mask;
        int broadcast = network | ~mask;
        List<InetAddress> hosts = new ArrayList<>();
        for (int host = network + 1; host < broadcast; host++) {
            try {
                hosts.add(InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(host).array()));
            } catch (UnknownHostException e) {
                // cannot happen for an address of four bytes
            }
        }
        return hosts;
    }

    private static boolean isOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * @return the name the camera has in the Bosch Smart Camera app, followed by the product name in brackets so it is
     *         clear which
     *         camera is which even when the names are similar
     */
    private static String buildLabel(@Nullable String title, @Nullable CameraModel model) {
        String productName = model == null ? null : model.getProductName();
        if (title == null || title.isBlank()) {
            return productName == null ? "@text/discovery.camera.default-label"
                    : "@text/discovery.camera.product-label [\"" + productName + "\"]";
        }
        // the name given in the app and the product name are names, nothing to translate
        return productName == null ? title : title + " (" + productName + ")";
    }
}
