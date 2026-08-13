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
package org.openhab.binding.boschsmartcam.internal.snapshot;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.DigestAuthentication;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.LocalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fetches a still image from a camera and keeps it for a while.
 *
 * The image does not come from the cloud but from the camera itself, over HTTPS with digest authentication. The
 * credentials for that are handed out by the cloud and rotate, so they are cached only briefly.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class SnapshotFetcher {

    /**
     * Bosch stops accepting a credential pair for new connections after about a minute, so it is renewed earlier.
     */
    private static final Duration CREDENTIAL_LIFETIME = Duration.ofSeconds(45);

    private static final long REQUEST_TIMEOUT_SECONDS = 15;

    /**
     * Full resolution, the same value the app asks for.
     */
    private static final String SNAPSHOT_PATH = "/snap.jpg?JpegSize=1206";

    private final Logger logger = LoggerFactory.getLogger(SnapshotFetcher.class);

    private final HttpClient httpClient;
    private final String cameraId;
    private final ConnectionSupplier connectionSupplier;

    private @Nullable LocalConnection connection;
    private Instant connectionAt = Instant.EPOCH;

    private byte @Nullable [] image;
    private Instant imageAt = Instant.EPOCH;

    public SnapshotFetcher(HttpClient httpClient, String cameraId, ConnectionSupplier connectionSupplier) {
        this.httpClient = httpClient;
        this.cameraId = cameraId;
        this.connectionSupplier = connectionSupplier;
    }

    /**
     * Returns a still image, reusing the last one while it is younger than {@code maxAge}. Several viewers therefore
     * cost no more than a single one.
     */
    public synchronized byte[] getSnapshot(Duration maxAge) throws BoschSmartCamException {
        byte[] cached = image;
        if (cached != null && Duration.between(imageAt, Instant.now()).compareTo(maxAge) < 0) {
            logger.trace("Serving the cached image of {}", cameraId);
            return cached;
        }

        byte[] fetched = fetch(currentConnection());
        image = fetched;
        imageAt = Instant.now();
        return fetched;
    }

    /**
     * Drops what is cached, e.g. when the camera thing goes away.
     */
    public synchronized void clear() {
        image = null;
        imageAt = Instant.EPOCH;
        connection = null;
        connectionAt = Instant.EPOCH;
    }

    private LocalConnection currentConnection() throws BoschSmartCamException {
        LocalConnection cached = connection;
        if (cached != null && Duration.between(connectionAt, Instant.now()).compareTo(CREDENTIAL_LIFETIME) < 0) {
            return cached;
        }
        LocalConnection opened = connectionSupplier.open();
        connection = opened;
        connectionAt = Instant.now();
        return opened;
    }

    private byte[] fetch(LocalConnection connection) throws BoschSmartCamException {
        String host = connection.host();
        String user = connection.user();
        String password = connection.password();
        if (host == null || user == null || password == null) {
            throw new BoschSmartCamException("Incomplete local credentials for " + cameraId);
        }

        String base = "https://" + host;
        String url = base + SNAPSHOT_PATH;
        try {
            // the store is shared with the other cameras, but it is keyed by URI so only the rotating credentials of
            // this very camera have to be replaced
            synchronized (httpClient) {
                AuthenticationStore store = httpClient.getAuthenticationStore();
                URI uri = URI.create(base);
                Authentication existing = store.findAuthentication("Digest", uri, Authentication.ANY_REALM);
                if (existing != null) {
                    store.removeAuthentication(existing);
                }
                store.clearAuthenticationResults();
                store.addAuthentication(new DigestAuthentication(uri, Authentication.ANY_REALM, user, password));

                ContentResponse response = httpClient.newRequest(url).timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .send();
                if (response.getStatus() != HttpStatus.OK_200) {
                    // a rejected credential is worth retrying with a fresh one on the next call
                    this.connection = null;
                    throw new BoschSmartCamException(
                            "The camera answered HTTP %d to the snapshot request".formatted(response.getStatus()),
                            response.getStatus());
                }
                return response.getContent();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BoschSmartCamException("Fetching the image of %s was interrupted".formatted(cameraId), e);
        } catch (ExecutionException | TimeoutException e) {
            this.connection = null;
            throw new BoschSmartCamException("Could not fetch the image of %s: %s".formatted(cameraId, e.getMessage()),
                    e);
        }
    }

    /**
     * Opens a connection to the camera, which is what hands out the credentials.
     */
    @FunctionalInterface
    public interface ConnectionSupplier {
        LocalConnection open() throws BoschSmartCamException;
    }
}
