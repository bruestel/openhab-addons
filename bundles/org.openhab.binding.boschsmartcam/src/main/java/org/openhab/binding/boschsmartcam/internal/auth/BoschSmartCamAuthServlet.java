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

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.OAUTH_REDIRECT_URI;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.api.BoschSmartCamException;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamAccountHandler;
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

    // keys used in index.html
    private static final String KEY_MESSAGE = "message";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_REDIRECT_URI = "redirectUri";
    // keys used in account.html
    private static final String KEY_ACCOUNT_LABEL = "account.label";
    private static final String KEY_ACCOUNT_UID = "account.uid";
    private static final String KEY_ACCOUNT_STATE_CLASS = "account.stateClass";
    private static final String KEY_ACCOUNT_STATE_TEXT = "account.stateText";
    private static final String KEY_ACCOUNT_AUTH_URL = "account.authorizationUrl";

    private final transient Logger logger = LoggerFactory.getLogger(BoschSmartCamAuthServlet.class);

    private final transient BoschSmartCamAuthService authService;
    private final String indexTemplate;
    private final String accountTemplate;

    public BoschSmartCamAuthServlet(BoschSmartCamAuthService authService, String indexTemplate,
            String accountTemplate) {
        this.authService = authService;
        this.indexTemplate = indexTemplate;
        this.accountTemplate = accountTemplate;
    }

    @Override
    protected void doGet(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response)
            throws IOException {
        if (request == null || response == null) {
            return;
        }
        // in case the browser was able to reach openHAB with the authorization code, accept it here as well
        String code = request.getParameter("code");
        String state = request.getParameter("state");
        String message = "";
        if (code != null && state != null) {
            message = authorize(state, request.getRequestURL() + "?" + request.getQueryString());
        }
        render(response, message);
    }

    @Override
    protected void doPost(@Nullable HttpServletRequest request, @Nullable HttpServletResponse response)
            throws IOException {
        if (request == null || response == null) {
            return;
        }
        String thingUid = request.getParameter(PARAM_THING_UID);
        String message;
        if (ACTION_DEAUTHORIZE.equals(request.getParameter(PARAM_ACTION))) {
            message = deauthorize(thingUid);
        } else {
            String redirectUrl = request.getParameter(PARAM_REDIRECT_URL);
            message = redirectUrl == null || redirectUrl.isBlank()
                    ? error("Please paste the URL of the page you were redirected to.")
                    : authorize(thingUid, redirectUrl.trim());
        }
        render(response, message);
    }

    private String authorize(@Nullable String thingUid, String redirectUrl) {
        Optional<BoschSmartCamAccountHandler> handler = authService.getAccountHandler(thingUid);
        if (handler.isEmpty()) {
            return error("The account this login belongs to no longer exists. Please start the login again.");
        }
        try {
            return success("Account " + handler.get().authorize(redirectUrl) + " is now authorized.");
        } catch (BoschSmartCamException e) {
            logger.debug("Authorization of {} failed", thingUid, e);
            return error(e.getMessage());
        }
    }

    private String deauthorize(@Nullable String thingUid) {
        Optional<BoschSmartCamAccountHandler> handler = authService.getAccountHandler(thingUid);
        if (handler.isEmpty()) {
            return error("Unknown account.");
        }
        handler.get().deauthorize();
        return success("The stored tokens of " + handler.get().getLabel() + " were removed.");
    }

    private void render(HttpServletResponse response, String message) throws IOException {
        Map<String, String> replacements = new HashMap<>();
        replacements.put(KEY_MESSAGE, message);
        replacements.put(KEY_REDIRECT_URI, escape(OAUTH_REDIRECT_URI));
        replacements.put(KEY_ACCOUNTS, formatAccounts());

        response.setContentType(CONTENT_TYPE);
        response.getWriter().append(replacePlaceholders(indexTemplate, replacements));
        response.getWriter().close();
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
        return replacePlaceholders(accountTemplate, replacements);
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
