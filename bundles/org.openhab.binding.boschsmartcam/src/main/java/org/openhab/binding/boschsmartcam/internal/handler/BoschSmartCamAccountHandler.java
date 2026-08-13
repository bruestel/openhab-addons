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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.boschsmartcam.internal.BoschSmartCamAccountConfiguration;
import org.openhab.binding.boschsmartcam.internal.api.AccessTokenProvider;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamApi;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.VideoInput;
import org.openhab.binding.boschsmartcam.internal.auth.BoschSmartCamAuthService;
import org.openhab.binding.boschsmartcam.internal.auth.PkceChallenge;
import org.openhab.binding.boschsmartcam.internal.discovery.BoschSmartCamDiscoveryService;
import org.openhab.core.auth.client.oauth2.AccessTokenRefreshListener;
import org.openhab.core.auth.client.oauth2.AccessTokenResponse;
import org.openhab.core.auth.client.oauth2.OAuthClientService;
import org.openhab.core.auth.client.oauth2.OAuthException;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.auth.client.oauth2.OAuthResponseException;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BoschSmartCamAccountHandler} holds the authorization against Bosch SingleKey ID and polls the camera
 * settings of the account.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamAccountHandler extends BaseBridgeHandler
        implements AccessTokenProvider, AccessTokenRefreshListener {

    private final Logger logger = LoggerFactory.getLogger(BoschSmartCamAccountHandler.class);

    private final OAuthFactory oAuthFactory;
    private final HttpClient httpClient;
    private final BoschSmartCamAuthService authService;

    private BoschSmartCamAccountConfiguration config = new BoschSmartCamAccountConfiguration();
    private @Nullable OAuthClientService oAuthService;
    private @Nullable BoschSmartCamApi api;
    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable PkceChallenge pkceChallenge;

    private volatile List<VideoInput> cameras = List.of();

    public BoschSmartCamAccountHandler(Bridge bridge, OAuthFactory oAuthFactory, HttpClient httpClient,
            BoschSmartCamAuthService authService) {
        super(bridge);
        this.oAuthFactory = oAuthFactory;
        this.httpClient = httpClient;
        this.authService = authService;
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(BoschSmartCamDiscoveryService.class);
    }

    @Override
    public void initialize() {
        config = getConfigAs(BoschSmartCamAccountConfiguration.class);
        authService.addAccountHandler(this);

        if (getClientSecret().isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "@text/offline.conf-error.no-client-secret");
            return;
        }

        oAuthService = createOAuthService();
        api = new BoschSmartCamApi(httpClient, this);
        updateStatus(ThingStatus.UNKNOWN);

        int interval = Math.max(30, config.refreshInterval);
        pollingJob = scheduler.scheduleWithFixedDelay(this::poll, 0, interval, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        authService.removeAccountHandler(this);
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
        OAuthClientService service = oAuthService;
        if (service != null) {
            service.removeAccessTokenRefreshListener(this);
            oAuthFactory.ungetOAuthService(getHandle());
            oAuthService = null;
        }
        api = null;
        cameras = List.of();
    }

    @Override
    public void handleRemoval() {
        oAuthFactory.deleteServiceAndAccessToken(getHandle());
        super.handleRemoval();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // the account bridge has no channels
    }

    @Override
    public void onAccessTokenResponse(AccessTokenResponse tokenResponse) {
        logger.debug("Refreshed the access token of {}, it expires in {} seconds", getHandle(),
                tokenResponse.getExpiresIn());
    }

    // --- API access -------------------------------------------------------------------------------------------

    @Override
    public String getAccessToken() throws BoschSmartCamException {
        OAuthClientService service = oAuthService;
        if (service == null) {
            throw new BoschSmartCamException("Account is not initialized");
        }
        try {
            AccessTokenResponse response = service.getAccessTokenResponse();
            String accessToken = response == null ? null : response.getAccessToken();
            if (accessToken == null || accessToken.isBlank()) {
                throw new BoschSmartCamException("Account is not authorized", 401);
            }
            return accessToken;
        } catch (OAuthException | OAuthResponseException | IOException e) {
            throw new BoschSmartCamException("Could not obtain an access token: " + e.getMessage(), e);
        }
    }

    /**
     * @return the cameras of the last successful poll
     */
    public List<VideoInput> getCameras() {
        return cameras;
    }

    public BoschSmartCamApi getApi() throws BoschSmartCamException {
        BoschSmartCamApi localApi = api;
        if (localApi == null) {
            throw new BoschSmartCamException("Account is not initialized");
        }
        return localApi;
    }

    /**
     * Polls the camera settings and pushes them to the camera things.
     */
    public void poll() {
        if (!isAuthorized()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                    "@text/offline.conf-error.not-authorized [\"" + SERVLET_PATH + "\"]");
            return;
        }
        try {
            List<VideoInput> videoInputs = getApi().getVideoInputs();
            cameras = videoInputs;
            updateStatus(ThingStatus.ONLINE);
            for (Thing thing : getThing().getThings()) {
                ThingHandler handler = thing.getHandler();
                if (handler instanceof BoschSmartCamCameraHandler cameraHandler) {
                    cameraHandler.updateFromCameras(videoInputs);
                }
            }
        } catch (BoschSmartCamException e) {
            logger.debug("Polling the Bosch cloud failed", e);
            if (e.isAuthorizationFailure()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                        "@text/offline.conf-error.not-authorized [\"" + SERVLET_PATH + "\"]");
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        }
    }

    /**
     * Triggers a poll shortly after a setting was changed, giving the camera time to apply it.
     */
    public void scheduleDelayedPoll() {
        scheduler.schedule(this::poll, 5, TimeUnit.SECONDS);
    }

    // --- Authorization ----------------------------------------------------------------------------------------

    public boolean isAuthorized() {
        OAuthClientService service = oAuthService;
        if (service == null) {
            return false;
        }
        try {
            AccessTokenResponse response = service.getAccessTokenResponse();
            return response != null && response.getRefreshToken() != null;
        } catch (OAuthException | OAuthResponseException | IOException e) {
            logger.debug("Could not read the stored token of {}: {}", getHandle(), e.getMessage());
            return false;
        }
    }

    /**
     * Builds the URL the user has to open to log in with the Bosch SingleKey ID. A new PKCE challenge is created for
     * every call, the verifier is kept until the authorization code is handed back.
     */
    public String formatAuthorizationUrl() throws BoschSmartCamException {
        OAuthClientService service = oAuthService;
        if (service == null) {
            throw new BoschSmartCamException("Account is not initialized");
        }
        PkceChallenge challenge = PkceChallenge.create();
        pkceChallenge = challenge;
        try {
            // openHAB core does not support PKCE, so the challenge is appended to the generated URL
            return service.getAuthorizationUrl(OAUTH_REDIRECT_URI, null, getHandle()) + "&code_challenge="
                    + URLEncoder.encode(challenge.challenge(), StandardCharsets.UTF_8) + "&code_challenge_method=S256";
        } catch (OAuthException e) {
            throw new BoschSmartCamException("Could not create the authorization URL: " + e.getMessage(), e);
        }
    }

    /**
     * Exchanges the authorization code contained in the redirect URL for the tokens.
     *
     * @param redirectUrlWithParams the complete URL the browser was redirected to
     * @return the label of the authorized account
     */
    public String authorize(String redirectUrlWithParams) throws BoschSmartCamException {
        OAuthClientService service = oAuthService;
        if (service == null) {
            throw new BoschSmartCamException("Account is not initialized");
        }
        PkceChallenge challenge = pkceChallenge;
        if (challenge == null) {
            throw new BoschSmartCamException("No login in progress, please start the login again");
        }
        try {
            String code = service.extractAuthCodeFromAuthResponse(redirectUrlWithParams);
            service.addExtraAuthField("code_verifier", challenge.verifier());
            service.getAccessTokenResponseByAuthorizationCode(code, OAUTH_REDIRECT_URI);
        } catch (OAuthException | OAuthResponseException | IOException e) {
            throw new BoschSmartCamException("Authorization failed: " + e.getMessage(), e);
        } finally {
            pkceChallenge = null;
            // the code verifier must not be sent with the following refresh requests
            recreateOAuthService();
        }
        scheduler.execute(this::poll);
        return getLabel();
    }

    /**
     * Removes the stored tokens so the account can be authorized with a different user.
     */
    public void deauthorize() {
        OAuthClientService service = oAuthService;
        if (service != null) {
            service.removeAccessTokenRefreshListener(this);
        }
        oAuthFactory.deleteServiceAndAccessToken(getHandle());
        oAuthService = createOAuthService();
        cameras = List.of();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                "@text/offline.conf-error.not-authorized [\"" + SERVLET_PATH + "\"]");
    }

    public String getLabel() {
        String label = getThing().getLabel();
        return label == null || label.isBlank() ? getThing().getUID().getAsString() : label;
    }

    public boolean matchesState(String state) {
        return getHandle().equals(state);
    }

    private String getHandle() {
        return getThing().getUID().getAsString();
    }

    private String getClientSecret() {
        String configured = config.clientSecret;
        return configured.isBlank() ? OAUTH_CLIENT_SECRET : configured;
    }

    private OAuthClientService createOAuthService() {
        OAuthClientService service = oAuthFactory.createOAuthClientService(getHandle(), OAUTH_TOKEN_URL,
                OAUTH_AUTHORIZE_URL, OAUTH_CLIENT_ID, getClientSecret(), OAUTH_SCOPE, false);
        service.addAccessTokenRefreshListener(this);
        return service;
    }

    /**
     * Drops the in memory instance of the OAuth service and creates a new one from the persisted tokens. Used to get
     * rid of the one time PKCE code verifier that has to be added as an extra field for the code exchange.
     */
    private void recreateOAuthService() {
        OAuthClientService service = oAuthService;
        if (service != null) {
            service.removeAccessTokenRefreshListener(this);
        }
        oAuthFactory.ungetOAuthService(getHandle());
        oAuthService = createOAuthService();
    }
}
