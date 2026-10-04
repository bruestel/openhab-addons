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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.LightSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

/**
 * Talks to a camera directly through its local API, which the user enables per camera in the Bosch Smart Camera app
 * ("Local data
 * interface"). It is read only, everything that changes a setting has to go through the cloud.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class LocalCameraClient {

    private static final long REQUEST_TIMEOUT_SECONDS = 15;

    /**
     * Full resolution, the same value the app asks for.
     */
    private static final String SNAPSHOT_PATH = "/snap.jpg?JpegSize=1206";

    private final Logger logger = LoggerFactory.getLogger(LocalCameraClient.class);
    private final Gson gson = new Gson();

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String authorization;

    private byte @Nullable [] image;
    private Instant imageAt = Instant.EPOCH;

    /**
     * @param httpClient a client set up with {@link CameraTrust#createSslContextFactory()}
     */
    public LocalCameraClient(HttpClient httpClient, String host, String user, String password) {
        this.httpClient = httpClient;
        this.baseUrl = "https://" + host;
        this.authorization = basicAuthorization(user, password);
    }

    /**
     * @return the value of an {@code Authorization} header for HTTP Basic, which is what the local API takes
     */
    public static String basicAuthorization(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return whether the privacy mode is on, i.e. the camera does not record
     */
    public boolean isPrivacyModeOn() throws BoschSmartCamException {
        PrivacyMode privacyMode = parse(get("/sh/data/privacyMode"), PrivacyMode.class);
        return Boolean.TRUE.equals(privacyMode.enable());
    }

    /**
     * @return what the lights of an Eyes Outdoor Camera II show right now, the manual light as well as the motion or
     *         ambient light while it is on
     */
    public ManualLighting getLighting() throws BoschSmartCamException {
        return parse(get("/sh/data/lighting/manual"), ManualLighting.class);
    }

    /**
     * @return whether the lights of an Eyes Outdoor Camera II go on with motion
     */
    public boolean isMotionLightOn() throws BoschSmartCamException {
        return Boolean.TRUE.equals(parse(get("/sh/data/lighting/motion"), Enabled.class).enable());
    }

    public AlarmStatus getAlarmStatus() throws BoschSmartCamException {
        return parse(get("/sh/data/alarm/status"), AlarmStatus.class);
    }

    /**
     * @return the firmware version the way the app shows it, e.g. {@code 9.40.202}
     */
    public @Nullable String getFirmwareVersion() throws BoschSmartCamException {
        Version version = parse(get("/sh/data/version"), Version.class);
        String raw = version.firmwareVersion() != null ? version.firmwareVersion() : version.firmwareVersionCamel();
        return raw == null ? null : withoutLeadingZeros(raw);
    }

    /**
     * Returns a still image, reusing the last one while it is younger than {@code maxAge}. Several viewers therefore
     * cost no more than a single one.
     */
    public synchronized byte[] getSnapshot(Duration maxAge) throws BoschSmartCamException {
        byte[] cached = image;
        if (cached != null && Duration.between(imageAt, Instant.now()).compareTo(maxAge) < 0) {
            return cached;
        }
        byte[] fetched = get(SNAPSHOT_PATH);
        image = fetched;
        imageAt = Instant.now();
        return fetched;
    }

    /**
     * The API answers {@code 9.40.0202} where the app shows {@code 9.40.202}, the same version with each segment
     * padded.
     */
    static String withoutLeadingZeros(String version) {
        return Arrays.stream(version.split("\\.")).map(segment -> segment.replaceFirst("^0+(?=.)", ""))
                .collect(Collectors.joining("."));
    }

    private byte[] get(String pathAndQuery) throws BoschSmartCamException {
        try {
            return send(pathAndQuery);
        } catch (BoschSmartCamException e) {
            // the camera now and then rejects a request it accepts right afterwards
            if (e.getHttpStatus() != HttpStatus.UNAUTHORIZED_401) {
                throw e;
            }
            logger.debug("{} answered 401 to {}, trying once more", baseUrl, pathAndQuery);
            return send(pathAndQuery);
        }
    }

    private byte[] send(String pathAndQuery) throws BoschSmartCamException {
        try {
            ContentResponse response = httpClient.newRequest(baseUrl + pathAndQuery)
                    .header(HttpHeader.AUTHORIZATION, authorization).timeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .send();
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new BoschSmartCamException(
                        "The camera answered HTTP %d to %s".formatted(response.getStatus(), pathAndQuery),
                        response.getStatus());
            }
            return response.getContent();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BoschSmartCamException("Request to %s was interrupted".formatted(pathAndQuery), e);
        } catch (ExecutionException | TimeoutException e) {
            Throwable cause = e.getCause();
            throw new BoschSmartCamException("The camera did not answer %s: %s".formatted(pathAndQuery,
                    cause != null ? cause.getMessage() : e.getMessage()), e);
        }
    }

    private <T> T parse(byte[] content, Class<T> type) throws BoschSmartCamException {
        String json = new String(content, StandardCharsets.UTF_8);
        try {
            @Nullable
            T parsed = gson.fromJson(json, type);
            if (parsed == null) {
                throw new BoschSmartCamException("Empty answer from the camera");
            }
            return parsed;
        } catch (JsonSyntaxException e) {
            throw new BoschSmartCamException("Unexpected answer from the camera: " + json, e);
        }
    }

    public record ManualLighting(@Nullable LightSettings front, @Nullable LightSettings top,
            @Nullable LightSettings bottom) {
    }

    /**
     * @param alarmType e.g. {@code NONE}, {@code INTRUSION_ALARM} or {@code MANUAL_ALARM}
     * @param intrusionSystem e.g. {@code INACTIVE}, {@code ARMING} or {@code ARMED}
     */
    public record AlarmStatus(@Nullable String alarmType, @Nullable String intrusionSystem) {
    }

    private record Enabled(@Nullable Boolean enable) {
    }

    private record PrivacyMode(@Nullable Boolean enable, @Nullable Integer timeout) {
    }

    /**
     * The documentation names the field {@code firmware_version}, the cameras answer {@code firmwareVersion}.
     */
    private record Version(@SerializedName("firmware_version") @Nullable String firmwareVersion,
            @SerializedName("firmwareVersion") @Nullable String firmwareVersionCamel) {
    }
}
