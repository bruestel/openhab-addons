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
package org.openhab.binding.boschsmartcam.internal.api;

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.API_BASE_URL;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.boschsmartcam.internal.api.dto.CameraStatus;
import org.openhab.binding.boschsmartcam.internal.api.dto.Commissioned;
import org.openhab.binding.boschsmartcam.internal.api.dto.LocalConnection;
import org.openhab.binding.boschsmartcam.internal.api.dto.NotificationsRequest;
import org.openhab.binding.boschsmartcam.internal.api.dto.PrivacyModeRequest;
import org.openhab.binding.boschsmartcam.internal.api.dto.VideoInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * Minimal client for the Bosch Smart Camera cloud API. It only covers the camera settings, video and images are
 * handled by the camera itself and are not part of this binding.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamApi {

    private static final String CONTENT_TYPE_JSON = "application/json";

    /**
     * Non standard status Bosch answers with when too many live sessions are open across all clients of the account.
     */
    private static final int HTTP_SESSION_LIMIT = 444;

    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    private final Logger logger = LoggerFactory.getLogger(BoschSmartCamApi.class);
    private final Gson gson = new Gson();

    private final HttpClient httpClient;
    private final AccessTokenProvider tokenProvider;

    public BoschSmartCamApi(HttpClient httpClient, AccessTokenProvider tokenProvider) {
        this.httpClient = httpClient;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Returns all cameras of the account.
     */
    public List<VideoInput> getVideoInputs() throws BoschSmartCamException {
        String content = execute(HttpMethod.GET, "/v11/video_inputs", null);
        try {
            List<VideoInput> videoInputs = gson.fromJson(content, new TypeToken<List<VideoInput>>() {
            }.getType());
            return videoInputs == null ? List.of() : videoInputs;
        } catch (JsonSyntaxException e) {
            throw new BoschSmartCamException("Unexpected response for video inputs", e);
        }
    }

    /**
     * Determines whether a camera is reachable. The camera list does not carry a reliable state for this, so the
     * dedicated {@code /ping} endpoint is asked, falling back to {@code /commissioned} when it does not answer.
     */
    public CameraStatus getCameraStatus(String cameraId) throws BoschSmartCamException {
        try {
            String ping = execute(HttpMethod.GET, "/v11/video_inputs/" + cameraId + "/ping", null).trim().replace("\"",
                    "");
            if (ping.startsWith("UPDATING")) {
                return CameraStatus.UPDATING;
            }
            if (CameraStatus.ONLINE.name().equalsIgnoreCase(ping)) {
                return CameraStatus.ONLINE;
            }
            if (CameraStatus.OFFLINE.name().equalsIgnoreCase(ping) || "UNREACHABLE".equalsIgnoreCase(ping)) {
                return CameraStatus.OFFLINE;
            }
            logger.debug("Unexpected answer of the ping endpoint: {}", ping);
        } catch (BoschSmartCamException e) {
            if (e.getHttpStatus() == HTTP_SESSION_LIMIT) {
                logger.debug("Bosch refused the ping for {}, too many live sessions are open at once", cameraId);
                return CameraStatus.SESSION_LIMIT;
            }
            logger.debug("Ping for {} failed, falling back to the commissioning state: {}", cameraId, e.getMessage());
        }
        return getCommissionedStatus(cameraId);
    }

    private CameraStatus getCommissionedStatus(String cameraId) {
        try {
            String content = execute(HttpMethod.GET, "/v11/video_inputs/" + cameraId + "/commissioned", null);
            Commissioned commissioned = gson.fromJson(content, Commissioned.class);
            if (commissioned == null) {
                return CameraStatus.UNKNOWN;
            }
            if (commissioned.isReachable()) {
                return CameraStatus.ONLINE;
            }
            return commissioned.isConfigured() ? CameraStatus.OFFLINE : CameraStatus.UNKNOWN;
        } catch (BoschSmartCamException | JsonSyntaxException e) {
            logger.debug("Could not read the commissioning state of {}: {}", cameraId, e.getMessage());
            return CameraStatus.UNKNOWN;
        }
    }

    /**
     * Switches a camera off (privacy mode on) or on again.
     *
     * @param cameraId id of the camera
     * @param privacyModeOn {@code true} switches the camera off, {@code false} switches it on
     * @param durationInSeconds optional time after which the camera switches itself on again
     */
    public void setPrivacyMode(String cameraId, boolean privacyModeOn, @Nullable Integer durationInSeconds)
            throws BoschSmartCamException {
        execute(HttpMethod.PUT, "/v11/video_inputs/" + cameraId + "/privacy",
                gson.toJson(PrivacyModeRequest.of(privacyModeOn, durationInSeconds)));
    }

    /**
     * Reads whatever the cloud knows about the ONVIF user of a camera. Diagnostic only - the endpoint exists but the
     * app barely uses it, so neither its answer nor what it expects on a write is documented anywhere.
     */
    public String getOnvifUser(String cameraId) throws BoschSmartCamException {
        return execute(HttpMethod.GET, "/v11/video_inputs/" + cameraId + "/onvif_user", null);
    }

    /**
     * Asks the cloud for the credentials to talk to the camera directly in the local network.
     */
    public LocalConnection openLocalConnection(String cameraId) throws BoschSmartCamException {
        String content = execute(HttpMethod.PUT, "/v11/video_inputs/" + cameraId + "/connection",
                "{\"type\":\"LOCAL\",\"highQualityVideo\":true}");
        try {
            LocalConnection connection = gson.fromJson(content, LocalConnection.class);
            if (connection == null || !connection.isUsable()) {
                throw new BoschSmartCamException("The cloud did not hand out local credentials for " + cameraId);
            }
            return connection;
        } catch (JsonSyntaxException e) {
            throw new BoschSmartCamException("Unexpected answer when opening a local connection", e);
        }
    }

    /**
     * Enables or disables the push notifications of a camera.
     */
    public void setNotifications(String cameraId, boolean enabled) throws BoschSmartCamException {
        execute(HttpMethod.PUT, "/v11/video_inputs/" + cameraId + "/enable_notifications",
                gson.toJson(NotificationsRequest.of(enabled)));
    }

    private String execute(HttpMethod method, String path, @Nullable String body) throws BoschSmartCamException {
        String url = API_BASE_URL + path;
        Request request = httpClient.newRequest(url).method(method)
                .header(HttpHeader.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
                .header(HttpHeader.ACCEPT, CONTENT_TYPE_JSON).timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (body != null) {
            request.content(new StringContentProvider(CONTENT_TYPE_JSON, body, StandardCharsets.UTF_8));
        }

        logger.trace("Sending {} {}", method, url);
        try {
            ContentResponse response = request.send();
            int status = response.getStatus();
            String content = response.getContentAsString();
            if (status != HttpStatus.OK_200 && status != HttpStatus.NO_CONTENT_204
                    && status != HttpStatus.ACCEPTED_202) {
                throw new BoschSmartCamException(
                        "%s %s failed with HTTP %d: %s".formatted(method, path, status, content), status);
            }
            logger.trace("Received {} for {} {}", status, method, url);
            return content;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BoschSmartCamException("Request to %s was interrupted".formatted(path), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new BoschSmartCamException("Request to %s failed: %s".formatted(path, e.getMessage()), e);
        }
    }
}
