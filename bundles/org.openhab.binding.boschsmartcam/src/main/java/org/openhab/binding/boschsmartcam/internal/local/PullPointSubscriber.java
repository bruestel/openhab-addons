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
package org.openhab.binding.boschsmartcam.internal.local;

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.HTTPS_SCHEME;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps an ONVIF PullPoint subscription at a camera open and hands every notification to a listener.
 *
 * The connection goes out from openHAB, so nothing has to be reachable from the camera. Each pull is a long poll the
 * camera answers when something happens or after 30 seconds, and every pull extends the subscription, so a new pull
 * is sent as soon as an answer arrives. The requests are asynchronous: no thread waits for the camera.
 *
 * If a request fails, the subscriber stops and tells its listener, which decides when to {@link #start()} again. A
 * subscription the camera does not hear from for 60 seconds expires on its own, so nothing is left behind even
 * without {@link #stop()}.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class PullPointSubscriber {

    /**
     * Notified about everything the subscription delivers. Called from the threads of the HTTP client, so a listener
     * must not block.
     */
    public interface Listener {

        /**
         * The camera answered a pull. Called for empty answers as well, which shows the subscription is alive.
         */
        void onNotifications(List<OnvifEvent> events);

        /**
         * The subscription broke. The subscriber has stopped, {@link PullPointSubscriber#start()} creates a new one.
         */
        void onFailure(BoschSmartCamException e);
    }

    private static final long REQUEST_TIMEOUT_SECONDS = 15;

    /**
     * The pull asks the camera to wait 30 seconds, the request has to outlast that.
     */
    private static final long PULL_TIMEOUT_SECONDS = 45;

    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;

    private final Logger logger = LoggerFactory.getLogger(PullPointSubscriber.class);

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authorization;
    private final Listener listener;

    private volatile boolean running;
    private volatile @Nullable String subscriptionPath;
    private volatile @Nullable Request pendingRequest;

    /**
     * @param authorization value of the {@code Authorization} header, HTTP Basic with the local user
     */
    public PullPointSubscriber(HttpClient httpClient, String host, String authorization, Listener listener) {
        this.httpClient = httpClient;
        this.baseUrl = HTTPS_SCHEME + host;
        this.authorization = authorization;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        subscribe();
    }

    /**
     * Ends the subscription. The camera is told so, but whether that arrives does not matter.
     */
    public synchronized void stop() {
        running = false;
        Request request = pendingRequest;
        if (request != null) {
            request.abort(new IOException("Subscription stopped"));
            pendingRequest = null;
        }
        String path = subscriptionPath;
        subscriptionPath = null;
        if (path != null) {
            // fire and forget, the subscription expires on its own anyway
            send(path, OnvifMessages.UNSUBSCRIBE, REQUEST_TIMEOUT_SECONDS, null);
        }
    }

    private void subscribe() {
        send(OnvifMessages.EVENT_SERVICE_PATH, OnvifMessages.CREATE_PULL_POINT_SUBSCRIPTION, REQUEST_TIMEOUT_SECONDS,
                (status, content) -> {
                    String address = OnvifMessages.parseSubscriptionAddress(content);
                    // the camera answers with its own idea of its address, only path and query are taken over
                    URI uri = URI.create(address);
                    String path = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
                    subscriptionPath = path;
                    logger.debug("Subscribed to the events of {} at {}", baseUrl, path);
                    pull();
                });
    }

    private void pull() {
        String path = subscriptionPath;
        if (path == null) {
            return;
        }
        send(path, OnvifMessages.PULL_MESSAGES, PULL_TIMEOUT_SECONDS, (status, content) -> {
            listener.onNotifications(OnvifMessages.parseNotifications(content));
            pull();
        });
    }

    @FunctionalInterface
    private interface AnswerHandler {
        void handle(int status, byte[] content) throws IOException;
    }

    /**
     * @param onSuccess what to do with a successful answer, {@code null} for a request whose answer does not matter
     */
    private void send(String path, String body, long timeoutSeconds, @Nullable AnswerHandler onSuccess) {
        if (!running && onSuccess != null) {
            return;
        }
        Request request = httpClient.newRequest(baseUrl + path).method(HttpMethod.POST)
                .header(HttpHeader.AUTHORIZATION, authorization)
                .content(new StringContentProvider(OnvifMessages.CONTENT_TYPE, body, StandardCharsets.UTF_8))
                .timeout(timeoutSeconds, TimeUnit.SECONDS);
        if (onSuccess != null) {
            pendingRequest = request;
        }
        request.send(new BufferingResponseListener(MAX_RESPONSE_BYTES) {
            @Override
            public void onComplete(@Nullable Result result) {
                if (result == null || onSuccess == null || !running) {
                    return;
                }
                if (result.isFailed()) {
                    Throwable failure = result.getFailure();
                    fail(new BoschSmartCamException("No answer from the camera to " + path + ": "
                            + (failure == null ? "unknown" : failure.getMessage()), failure));
                    return;
                }
                int status = result.getResponse().getStatus();
                if (status != HttpStatus.OK_200) {
                    fail(new BoschSmartCamException("The camera answered HTTP " + status + " to " + path, status));
                    return;
                }
                try {
                    onSuccess.handle(status, getContent());
                } catch (IOException e) {
                    String message = e.getMessage();
                    fail(new BoschSmartCamException(message == null ? "Unreadable answer" : message, e));
                }
            }
        });
    }

    private void fail(BoschSmartCamException e) {
        synchronized (this) {
            if (!running) {
                return;
            }
            running = false;
            subscriptionPath = null;
            pendingRequest = null;
        }
        listener.onFailure(e);
    }
}
