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
package org.openhab.binding.boschsmartcam.internal.auth;

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.CALLBACK_PATH;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.DECLINE_PATH;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.EVENTS_PATH;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.EVENT_CLIP_FILE;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.EVENT_IMAGE_FILE;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.INSTANCE_URL_SETTINGS;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.OAUTH_REDIRECT_URI;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.SERVLET_PATH;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.SNAPSHOT_FILE;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamApi;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.api.dto.CloudEvent;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamAccountHandler;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamCameraHandler;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Renders the page that lets the user authorize an account bridge against Bosch SingleKey ID.
 *
 * Bosch only accepts a single, fixed redirect URI for the app client, so the browser cannot be redirected back to
 * openHAB. Instead the user pastes the URL of the page they landed on, and the authorization code is taken from there.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@Component(service = Servlet.class, property = {
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME + "=boschsmartcam",
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=" + SERVLET_PATH + "/*",
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=" + CALLBACK_PATH,
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=" + DECLINE_PATH })
@NonNullByDefault
public class BoschSmartCamAuthServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String CONTENT_TYPE = "text/html;charset=UTF-8";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private static final String PARAM_ACTION = "action";
    private static final String PARAM_THING_UID = "thingUid";
    private static final String PARAM_REDIRECT_URL = "redirectUrl";
    private static final String ACTION_DEAUTHORIZE = "deauthorize";
    private static final String PARAM_RESULT = "result";
    private static final String PARAM_DETAIL = "detail";

    private static final String RESULT_AUTHORIZED = "authorized";
    private static final String RESULT_REMOVED = "removed";
    private static final String RESULT_DECLINED = "declined";
    private static final String RESULT_FAILED = "failed";

    // keys used in index.html
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_REDIRECT_URI = "redirectUri";
    private static final String KEY_CALLBACK_URL = "callbackUrl";
    private static final String KEY_OPENHAB_URL = "openhabUrl";
    private static final String KEY_INSTANCE_SETTINGS = "instanceSettingsUrl";
    private static final String KEY_SERVLET_PATH = "servletPath";
    // keys used in account.html
    private static final String KEY_ACCOUNT_LABEL = "account.label";
    private static final String KEY_ACCOUNT_UID = "account.uid";
    private static final String KEY_ACCOUNT_STATE_CLASS = "account.stateClass";
    private static final String KEY_ACCOUNT_STATE_TEXT = "account.stateText";
    private static final String KEY_ACCOUNT_AUTH_URL = "account.authorizationUrl";

    private static final String PARAM_LIMIT = "limit";
    private static final String PARAM_BEFORE = "before";
    private static final String PARAM_SINCE = "since";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile("[0-9A-Fa-f-]{36}");
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    private final transient Logger logger = LoggerFactory.getLogger(BoschSmartCamAuthServlet.class);
    private final transient Gson gson = new Gson();

    private static final String TEMPLATE_INDEX = "templates/index.html";
    private static final String TEMPLATE_ACCOUNT = "templates/account.html";

    /**
     * The HTML templates of the pages.
     */
    record Templates(String index, String account) {
    }

    private final transient BoschSmartCamAuthService authService;
    private final Templates templates;

    /**
     * openHAB serves the servlet at the paths in its component properties as soon as it is registered, and drops it
     * with it. The two paths besides {@link BoschSmartCamBindingConstants#SERVLET_PATH} are where a login comes back
     * through my.home-assistant.io; without them the code can still be pasted into the page.
     */
    @Activate
    public BoschSmartCamAuthServlet(@Reference BoschSmartCamAuthService authService, BundleContext bundleContext)
            throws IOException {
        this(authService, new Templates(readTemplate(bundleContext, TEMPLATE_INDEX),
                readTemplate(bundleContext, TEMPLATE_ACCOUNT)));
    }

    BoschSmartCamAuthServlet(BoschSmartCamAuthService authService, Templates templates) {
        this.authService = authService;
        this.templates = templates;
    }

    private static String readTemplate(BundleContext bundleContext, String templateName) throws IOException {
        URL template = bundleContext.getBundle().getEntry(templateName);
        if (template == null) {
            throw new FileNotFoundException("Cannot find " + templateName);
        }
        try (InputStream inputStream = template.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    protected void doGet(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response)
            throws IOException {
        if (request == null || response == null) {
            return;
        }
        if (serveCameraIfRequested(request, response)) {
            return;
        }

        String code = request.getParameter("code");
        String error = request.getParameter("error");
        if (code == null && error == null) {
            render(request, response, messageFromResult(request));
            return;
        }

        // the login was forwarded back to openHAB, possibly onto one of the callback paths - handle the outcome and
        // send the browser on to the page itself, so the user does not end up on a callback URL and a reload does not
        // try to redeem the code a second time
        String outcome;
        if (error != null) {
            // among others the "Decline" button of the forwarding page ends up here
            String description = request.getParameter("error_description");
            logger.debug("Login was not completed: {} {}", error, description);
            outcome = outcome(RESULT_DECLINED, description == null ? error : error + ": " + description);
        } else {
            outcome = authorize(request.getParameter("state"),
                    request.getRequestURL() + "?" + request.getQueryString());
        }
        response.sendRedirect(SERVLET_PATH + "?" + outcome);
    }

    @Override
    protected void doPost(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response)
            throws IOException {
        if (request == null || response == null) {
            return;
        }
        String thingUid = request.getParameter(PARAM_THING_UID);
        String outcome;
        if (ACTION_DEAUTHORIZE.equals(request.getParameter(PARAM_ACTION))) {
            outcome = deauthorize(thingUid);
        } else {
            String redirectUrl = request.getParameter(PARAM_REDIRECT_URL);
            outcome = redirectUrl == null || redirectUrl.isBlank()
                    ? outcome(RESULT_FAILED, "Please paste the address you were redirected to.")
                    : authorize(thingUid, redirectUrl.trim());
        }
        response.sendRedirect(SERVLET_PATH + "?" + outcome);
    }

    /**
     * Serves what belongs to a camera below {@code /<token>/}: the snapshot, the events the cloud keeps and their
     * images and clips. The token is the only thing standing between a request and the camera, so requests are
     * additionally limited to the configured networks.
     *
     * @return whether the request was for a camera and is now answered
     */
    private boolean serveCameraIfRequested(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String path = request.getPathInfo();
        if (path == null) {
            return false;
        }
        // "/<token>/snapshot.jpg", "/<token>/events" and "/<token>/events/<id>/<file>" start with an empty part
        String[] parts = path.split("/");
        boolean snapshot = parts.length == 3 && SNAPSHOT_FILE.equals(parts[2]);
        boolean eventList = parts.length == 3 && EVENTS_PATH.equals(parts[2]);
        boolean eventMedia = parts.length == 5 && EVENTS_PATH.equals(parts[2])
                && (EVENT_IMAGE_FILE.equals(parts[4]) || EVENT_CLIP_FILE.equals(parts[4]));
        if (!snapshot && !eventList && !eventMedia) {
            return false;
        }

        Optional<BoschSmartCamCameraHandler> camera = authService.getCamera(parts[1]);
        if (camera.isEmpty()) {
            // the same answer as for a forbidden network, so an unknown token cannot be told apart from a known one
            logger.debug("Snapshot requested with an unknown token from {}", request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }
        if (!camera.get().isAllowedFrom(request.getRemoteAddr())) {
            // debug only: anyone outside could otherwise fill the log
            logger.debug("Refused a snapshot request from {}, it is not in the allowed networks",
                    request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }

        if (eventList) {
            serveEvents(camera.get(), request, response);
            return true;
        }
        if (eventMedia) {
            serveEventMedia(camera.get(), parts[3], EVENT_CLIP_FILE.equals(parts[4]), response);
            return true;
        }
        if (!camera.get().offersSnapshot()) {
            logger.debug("Refused a snapshot request from {}, the snapshot URL is not linked", request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }
        try {
            byte[] image = camera.get().getSnapshot();
            response.setContentType("image/jpeg");
            response.setContentLength(image.length);
            // the binding caches, the browser should not
            response.setHeader("Cache-Control", "no-store");
            response.getOutputStream().write(image);
        } catch (BoschSmartCamException e) {
            logger.debug("Could not serve a snapshot", e);
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        return true;
    }

    /**
     * Lists the events the cloud keeps for a camera, newest first, as JSON. The links to image and clip are relative to
     * the list, so a client only needs to know its address.
     */
    private void serveEvents(BoschSmartCamCameraHandler camera, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if (!camera.offersEventsApi()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String before = request.getParameter(PARAM_BEFORE);
        String since = request.getParameter(PARAM_SINCE);
        if ((before != null && !EVENT_ID_PATTERN.matcher(before).matches())
                || (since != null && !EVENT_ID_PATTERN.matcher(since).matches())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        int limit = DEFAULT_LIMIT;
        String limitParameter = request.getParameter(PARAM_LIMIT);
        if (limitParameter != null) {
            try {
                limit = Math.clamp(Integer.parseInt(limitParameter), 1, MAX_LIMIT);
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        }
        List<CloudEvent> events;
        try {
            events = camera.getCloudEvents().list(limit, before, since);
        } catch (BoschSmartCamException e) {
            logger.debug("Could not list the events of {}: {}", camera.getLabel(), e.getMessage());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        List<Map<String, @Nullable Object>> entries = events.stream().map(BoschSmartCamAuthServlet::toEntry).toList();
        response.setContentType(JSON_CONTENT_TYPE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().append(gson.toJson(Map.of("events", entries))).close();
    }

    private static Map<String, @Nullable Object> toEntry(CloudEvent event) {
        Map<String, @Nullable Object> entry = new LinkedHashMap<>();
        String id = event.id();
        entry.put("id", id);
        ZonedDateTime time = event.time();
        entry.put("time", time == null ? null : time.toOffsetDateTime().toString());
        entry.put("kind", event.kind());
        entry.put("eventType", event.eventType());
        entry.put("tags", event.eventTags());
        entry.put("read", event.isRead());
        CloudEvent.ClipState clip = event.clipState();
        entry.put("clip", clip.name());
        if (event.imageUrl() != null) {
            entry.put("imageUrl", EVENTS_PATH + "/" + id + "/" + EVENT_IMAGE_FILE);
        }
        if (clip == CloudEvent.ClipState.READY) {
            entry.put("clipUrl", EVENTS_PATH + "/" + id + "/" + EVENT_CLIP_FILE);
        }
        return entry;
    }

    /**
     * Passes the image or clip of an event on from the cloud as it arrives, without keeping it.
     */
    private void serveEventMedia(BoschSmartCamCameraHandler camera, String eventId, boolean clip,
            HttpServletResponse response) throws IOException {
        if (!EVENT_ID_PATTERN.matcher(eventId).matches() || !camera.offersEventMedia(eventId, clip)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try (BoschSmartCamApi.Media media = camera.openEventMedia(eventId, clip)) {
            String contentType = media.contentType();
            response.setContentType(contentType != null ? contentType : clip ? "video/mp4" : "image/jpeg");
            // what an event recorded does not change any more
            response.setHeader("Cache-Control", "private, max-age=86400");
            OutputStream out = response.getOutputStream();
            media.content().transferTo(out);
            out.flush();
        } catch (BoschSmartCamException e) {
            logger.debug("Could not serve the {} of event {}: {}", clip ? "clip" : "image", eventId, e.getMessage());
            if (!response.isCommitted()) {
                response.sendError(e.getHttpStatus() == 404 ? HttpServletResponse.SC_NOT_FOUND
                        : HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }
        }
    }

    private String authorize(@Nullable String thingUid, String redirectUrl) {
        Optional<BoschSmartCamAccountHandler> handler = authService.getAccountHandler(thingUid);
        if (handler.isEmpty()) {
            return outcome(RESULT_FAILED, "The account this login belongs to no longer exists. Start it again.");
        }
        try {
            return outcome(RESULT_AUTHORIZED, handler.get().authorize(redirectUrl));
        } catch (BoschSmartCamException e) {
            logger.debug("Authorization of {} failed", thingUid, e);
            return outcome(RESULT_FAILED, e.getMessage());
        }
    }

    private String deauthorize(@Nullable String thingUid) {
        Optional<BoschSmartCamAccountHandler> handler = authService.getAccountHandler(thingUid);
        if (handler.isEmpty()) {
            return outcome(RESULT_FAILED, "Unknown account.");
        }
        handler.get().deauthorize();
        return outcome(RESULT_REMOVED, handler.get().getLabel());
    }

    /**
     * Builds the query string that carries the outcome of an action over the redirect to the page.
     */
    private static String outcome(String result, @Nullable String detail) {
        return PARAM_RESULT + "=" + result
                + (detail == null ? "" : "&" + PARAM_DETAIL + "=" + URLEncoder.encode(detail, StandardCharsets.UTF_8));
    }

    private String messageFromResult(HttpServletRequest request) {
        String result = request.getParameter(PARAM_RESULT);
        if (result == null) {
            return "";
        }
        String detail = request.getParameter(PARAM_DETAIL);
        return switch (result) {
            case RESULT_AUTHORIZED -> success("Account " + detail + " is now authorized.");
            case RESULT_REMOVED -> success("The stored tokens of " + detail + " were removed.");
            case RESULT_DECLINED -> error(
                    "The login was not completed (" + detail + "). Start it again and confirm with \"Link account\".");
            default -> error(detail == null ? "Authorization failed." : detail);
        };
    }

    private void render(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        String openhabUrl = getOpenhabUrl(request);
        Map<String, String> replacements = new HashMap<>();
        replacements.put(KEY_MESSAGE, message);
        replacements.put(KEY_REDIRECT_URI, escape(OAUTH_REDIRECT_URI));
        replacements.put(KEY_OPENHAB_URL, escape(openhabUrl));
        replacements.put(KEY_CALLBACK_URL, escape(openhabUrl + CALLBACK_PATH));
        replacements.put(KEY_INSTANCE_SETTINGS, escape(INSTANCE_URL_SETTINGS));
        replacements.put(KEY_SERVLET_PATH, escape(SERVLET_PATH));
        replacements.put(KEY_ACCOUNTS, formatAccounts());

        response.setContentType(CONTENT_TYPE);
        response.getWriter().append(replacePlaceholders(templates.index(), replacements));
        response.getWriter().close();
    }

    /**
     * @return the base URL openHAB was reached with, which is the value the instance URL of my.home-assistant.io has
     *         to be set to
     */
    private static String getOpenhabUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }

    private String formatAccounts() {
        List<BoschSmartCamAccountHandler> handlers = authService.getAccountHandlers();
        if (handlers.isEmpty()) {
            return "<p class=\"empty\">No account found. Add a <em>Bosch Smart Camera Account</em> thing first, then come back to this page.</p>";
        }
        return handlers.stream().map(this::formatAccount).collect(Collectors.joining());
    }

    private String formatAccount(BoschSmartCamAccountHandler handler) {
        boolean authorized = handler.isAuthorized();
        Map<String, String> replacements = new HashMap<>();
        replacements.put(KEY_SERVLET_PATH, escape(SERVLET_PATH));
        replacements.put(KEY_ACCOUNT_LABEL, escape(handler.getLabel()));
        replacements.put(KEY_ACCOUNT_UID, escape(handler.getThing().getUID().getAsString()));
        replacements.put(KEY_ACCOUNT_STATE_CLASS, authorized ? "ok" : "pending");
        replacements.put(KEY_ACCOUNT_STATE_TEXT, authorized ? "authorized" : "not authorized");

        String authorizationUrl;
        try {
            authorizationUrl = escape(handler.formatAuthorizationUrl());
        } catch (BoschSmartCamException e) {
            logger.debug("Could not build the authorization URL for {}", handler.getThing().getUID(), e);
            authorizationUrl = "";
        }
        replacements.put(KEY_ACCOUNT_AUTH_URL, authorizationUrl);
        return replacePlaceholders(templates.account(), replacements);
    }

    private static String success(String message) {
        return "<p class=\"message ok\">" + escape(message) + "</p>";
    }

    private static String error(@Nullable String message) {
        return "<p class=\"message error\">" + escape(message == null ? "Unknown error" : message) + "</p>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String replacePlaceholders(String template, Map<String, String> replacements) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(replacements.getOrDefault(key, "${" + key + "}")));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
