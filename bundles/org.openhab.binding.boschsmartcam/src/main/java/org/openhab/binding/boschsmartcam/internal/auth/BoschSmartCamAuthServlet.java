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
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.EVENTS_PAGE_FILE;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.EVENTS_STREAM_FILE;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.INSTANCE_URL_SETTINGS;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.OAUTH_REDIRECT_URI;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.RAW_VIDEO_FILE;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.SERVLET_PATH;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.SNAPSHOT_FILE;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.events.CameraEvent;
import org.openhab.binding.boschsmartcam.internal.events.EventLog;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamAccountHandler;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamCameraHandler;
import org.openhab.binding.boschsmartcam.internal.video.AacDepacketizer.AacFrame;
import org.openhab.binding.boschsmartcam.internal.video.CameraStream;
import org.openhab.binding.boschsmartcam.internal.video.H264Depacketizer.AccessUnit;
import org.openhab.binding.boschsmartcam.internal.video.HlsStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders the page that lets the user authorize an account bridge against Bosch SingleKey ID.
 *
 * Bosch only accepts a single, fixed redirect URI for the app client, so the browser cannot be redirected back to
 * openHAB. Instead the user pastes the URL of the page they landed on, and the authorization code is taken from there.
 *
 * @author Jonas Brüstel - Initial contribution
 */
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

    private final transient Logger logger = LoggerFactory.getLogger(BoschSmartCamAuthServlet.class);

    /**
     * How many browsers may follow the events of one camera at once. Each holds a thread of the web server.
     */
    private static final int MAX_EVENT_STREAMS = 5;

    /**
     * A stream ends after this long and the browser opens a new one, so no request stays open forever.
     */
    private static final Duration EVENT_STREAM_DURATION = Duration.ofMinutes(10);
    private static final long KEEPALIVE_SECONDS = 15;

    /**
     * How long the first request of the playlist waits for the stream to have enough segments.
     */
    private static final Duration HLS_START_TIMEOUT = Duration.ofSeconds(15);

    // keys used in events.html
    private static final String KEY_LABEL = "label";
    private static final String KEY_ROWS = "rows";
    private static final String KEY_EMPTY_HIDDEN = "emptyHidden";
    private static final String KEY_CAPACITY = "capacity";
    private static final String KEY_SNAPSHOT_FILE = "snapshotFile";
    private static final String KEY_STREAM_FILE = "streamFile";

    /**
     * The HTML templates of the pages.
     */
    public record Templates(String index, String account, String events) {
    }

    private final transient BoschSmartCamAuthService authService;
    private final Templates templates;

    public BoschSmartCamAuthServlet(BoschSmartCamAuthService authService, Templates templates) {
        this.authService = authService;
        this.templates = templates;
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
     * Serves the files of a camera below {@code /<token>/}: the snapshot, the event page and its stream. The token is
     * the only thing standing between a request and the camera, so requests are additionally limited to the
     * configured networks.
     *
     * @return whether the request was for a camera and is now answered
     */
    private boolean serveCameraIfRequested(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String path = request.getPathInfo();
        if (path == null) {
            return false;
        }
        // "/<token>/<file>" splits into an empty part, the token and the file
        String[] parts = path.split("/");
        if (parts.length != 3) {
            return false;
        }
        String file = parts[2];
        boolean hlsFile = HlsStream.PLAYLIST_FILE.equals(file) || HlsStream.INIT_FILE.equals(file)
                || (file.startsWith(HlsStream.SEGMENT_PREFIX) && file.endsWith(HlsStream.SEGMENT_SUFFIX));
        if (!SNAPSHOT_FILE.equals(file) && !EVENTS_PAGE_FILE.equals(file) && !EVENTS_STREAM_FILE.equals(file)
                && !RAW_VIDEO_FILE.equals(file) && !hlsFile) {
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
            logger.warn("Refused a snapshot request from {}, it is not in the allowed networks",
                    request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }

        if (EVENTS_PAGE_FILE.equals(file)) {
            renderEvents(camera.get(), response);
            return true;
        }
        if (EVENTS_STREAM_FILE.equals(file)) {
            streamEvents(camera.get().getEventLog(), response);
            return true;
        }
        if (RAW_VIDEO_FILE.equals(file)) {
            streamRawVideo(camera.get(), response);
            return true;
        }
        if (hlsFile) {
            logger.debug("{} requested {} with {}", request.getRemoteAddr(), file, request.getHeader("User-Agent"));
            serveHls(camera.get(), file, response);
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

    private void renderEvents(BoschSmartCamCameraHandler camera, HttpServletResponse response) throws IOException {
        List<CameraEvent> events = camera.getEventLog().list();
        Map<String, String> replacements = new HashMap<>();
        replacements.put(KEY_LABEL, escape(camera.getLabel()));
        replacements.put(KEY_ROWS,
                events.stream().map(BoschSmartCamAuthServlet::formatEventRow).collect(Collectors.joining()));
        replacements.put(KEY_EMPTY_HIDDEN, events.isEmpty() ? "" : " hidden");
        replacements.put(KEY_CAPACITY, String.valueOf(EventLog.CAPACITY));
        replacements.put(KEY_SNAPSHOT_FILE, SNAPSHOT_FILE);
        replacements.put(KEY_STREAM_FILE, EVENTS_STREAM_FILE);

        response.setContentType(CONTENT_TYPE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().append(replacePlaceholders(templates.events(), replacements)).close();
    }

    /**
     * A row with the raw values only, the script of the page formats them in the time zone of the browser.
     */
    private static String formatEventRow(CameraEvent event) {
        String clipId = event.clipId();
        Instant end = event.recordingEnd();
        return "<tr data-time=\"" + event.time() + "\" data-kind=\"" + escape(event.kind()) + "\" data-clip=\""
                + (clipId == null ? "" : escape(clipId)) + "\" data-end=\"" + (end == null ? "" : end) + "\"><td>"
                + event.time() + "</td><td>" + escape(event.kind()) + "</td><td></td><td class=\"clip\">"
                + (clipId == null ? "" : escape(clipId)) + "</td></tr>";
    }

    /**
     * Sends new events as server-sent events until the browser goes away or the stream has run for
     * {@link #EVENT_STREAM_DURATION}. The browser reconnects on its own.
     */
    private void streamEvents(EventLog eventLog, HttpServletResponse response) throws IOException {
        BlockingQueue<CameraEvent> queue = new LinkedBlockingQueue<>(EventLog.CAPACITY);
        Consumer<CameraEvent> listener = queue::offer;
        if (!eventLog.addListener(listener, MAX_EVENT_STREAMS)) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        try {
            response.setContentType("text/event-stream;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            PrintWriter writer = response.getWriter();
            writer.write("retry: 5000\n\n");
            writer.flush();
            Instant end = Instant.now().plus(EVENT_STREAM_DURATION);
            while (Instant.now().isBefore(end) && !writer.checkError()) {
                CameraEvent event = queue.poll(KEEPALIVE_SECONDS, TimeUnit.SECONDS);
                // a comment keeps proxies from closing an idle stream and shows when the browser is gone
                writer.write(event == null ? ": keepalive\n\n" : "data: " + toJson(event) + "\n\n");
                writer.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            eventLog.removeListener(listener);
        }
    }

    /**
     * Serves the live stream as HLS. Fetching the playlist starts the stream; the first answer waits until enough
     * segments exist for a player to start.
     */
    private void serveHls(BoschSmartCamCameraHandler camera, String file, HttpServletResponse response)
            throws IOException {
        if (HlsStream.PLAYLIST_FILE.equals(file)) {
            HlsStream hls = camera.startHls();
            try {
                if (!hls.awaitReady(HLS_START_TIMEOUT)) {
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                return;
            }
            response.setContentType("application/vnd.apple.mpegurl;charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.getWriter().append(hls.getPlaylist()).close();
            return;
        }

        HlsStream hls = camera.getRunningHls();
        byte[] data = null;
        if (hls != null) {
            if (HlsStream.INIT_FILE.equals(file)) {
                data = hls.getInitSegment();
            } else {
                try {
                    data = hls.getSegment(Integer.parseInt(file.substring(HlsStream.SEGMENT_PREFIX.length(),
                            file.length() - HlsStream.SEGMENT_SUFFIX.length())));
                } catch (NumberFormatException e) {
                    data = null;
                }
            }
        }
        if (data == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        response.setContentType("video/mp4");
        response.setContentLength(data.length);
        response.getOutputStream().write(data);
    }

    /**
     * Sends ten seconds of the live stream as raw H.264, as a check of the stream receiver.
     */
    private void streamRawVideo(BoschSmartCamCameraHandler camera, HttpServletResponse response) throws IOException {
        BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(1000);
        AtomicReference<CameraStream.@Nullable VideoConfig> video = new AtomicReference<>();
        CameraStream stream = camera.openStream(new CameraStream.Sink() {
            @Override
            public void onStart(CameraStream.VideoConfig videoConfig, CameraStream.@Nullable AudioConfig audio) {
                video.set(videoConfig);
            }

            @Override
            public void onVideo(AccessUnit accessUnit) {
                CameraStream.VideoConfig config = video.get();
                if (config != null) {
                    queue.offer(CameraStream.annexB(config, accessUnit));
                }
            }

            @Override
            public void onAudio(AacFrame frame) {
            }

            @Override
            public void onFailure(IOException e) {
                logger.debug("Raw stream failed: {}", e.getMessage());
                // an empty chunk marks the end
                queue.offer(new byte[0]);
            }
        });
        try {
            response.setContentType("video/h264");
            OutputStream out = response.getOutputStream();
            Instant until = Instant.now().plusSeconds(10);
            while (Instant.now().isBefore(until)) {
                byte[] chunk = queue.poll(1, TimeUnit.SECONDS);
                if (chunk != null && chunk.length == 0) {
                    break;
                }
                if (chunk != null) {
                    out.write(chunk);
                }
            }
            out.flush();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stream.stop();
        }
    }

    private static String toJson(CameraEvent event) {
        String clipId = event.clipId();
        Instant end = event.recordingEnd();
        return "{\"time\":\"" + event.time() + "\",\"kind\":" + jsonString(event.kind()) + ",\"clipId\":"
                + (clipId == null ? "null" : jsonString(clipId)) + ",\"recordingEnd\":"
                + (end == null ? "null" : "\"" + end + "\"") + "}";
    }

    private static String jsonString(String value) {
        StringBuilder json = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '"' || c == '\\') {
                json.append('\\').append(c);
            } else if (c < 0x20) {
                json.append(String.format("\\u%04x", (int) c));
            } else {
                json.append(c);
            }
        }
        return json.append('"').toString();
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

    /**
     * The HTTP service derives the servlet name from the class name and requires it to be unique per context, so the
     * additional entry points cannot reuse this class. They behave identically, only the name differs.
     */
    @NonNullByDefault
    public static class Callback extends BoschSmartCamAuthServlet {

        private static final long serialVersionUID = 1L;

        public Callback(BoschSmartCamAuthService authService, Templates templates) {
            super(authService, templates);
        }
    }

    /**
     * @see Callback
     */
    @NonNullByDefault
    public static class Decline extends BoschSmartCamAuthServlet {

        private static final long serialVersionUID = 1L;

        public Decline(BoschSmartCamAuthService authService, Templates templates) {
            super(authService, templates);
        }
    }
}
