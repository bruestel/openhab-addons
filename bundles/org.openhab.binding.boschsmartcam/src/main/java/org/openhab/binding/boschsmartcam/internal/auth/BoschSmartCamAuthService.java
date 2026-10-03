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
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.SERVLET_PATH;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamAccountHandler;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamCameraHandler;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the servlet that guides the user through the Bosch SingleKey ID login and keeps track of the account
 * bridges that can be authorized.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@Component(service = BoschSmartCamAuthService.class, configurationPid = "binding.boschsmartcam.authService")
@NonNullByDefault
public class BoschSmartCamAuthService {

    private static final String TEMPLATE_INDEX = "templates/index.html";
    private static final String TEMPLATE_ACCOUNT = "templates/account.html";

    private final Logger logger = LoggerFactory.getLogger(BoschSmartCamAuthService.class);

    private final List<BoschSmartCamAccountHandler> handlers = new CopyOnWriteArrayList<>();

    private @NonNullByDefault({}) HttpService httpService;
    private @NonNullByDefault({}) BundleContext bundleContext;
    private final List<String> aliases = new CopyOnWriteArrayList<>();
    private final Map<String, BoschSmartCamCameraHandler> camerasByToken = new ConcurrentHashMap<>();

    @Activate
    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        bundleContext = componentContext.getBundleContext();
        BoschSmartCamAuthServlet.Templates templates;
        try {
            templates = new BoschSmartCamAuthServlet.Templates(readTemplate(TEMPLATE_INDEX),
                    readTemplate(TEMPLATE_ACCOUNT));
        } catch (IOException e) {
            logger.warn("Could not read the templates of the authorization page: {}", e.getMessage());
            return;
        }

        if (!register(SERVLET_PATH, new BoschSmartCamAuthServlet(this, templates))) {
            return;
        }
        // both are conveniences: the code can always be pasted into the page instead
        register(CALLBACK_PATH, new BoschSmartCamAuthServlet.Callback(this, templates));
        register(DECLINE_PATH, new BoschSmartCamAuthServlet.Decline(this, templates));
    }

    @Deactivate
    protected void deactivate(ComponentContext componentContext) {
        for (String alias : aliases) {
            httpService.unregister(alias);
        }
        aliases.clear();
    }

    /**
     * Registers one entry point of the authorization page. Each alias needs its own servlet class, see
     * {@link BoschSmartCamAuthServlet.Callback}.
     *
     * @return whether the alias is now served
     */
    private boolean register(String alias, HttpServlet servlet) {
        try {
            httpService.registerServlet(alias, servlet, null, httpService.createDefaultHttpContext());
            aliases.add(alias);
            logger.debug("Registered the authorization servlet at {}", alias);
            return true;
        } catch (NamespaceException | ServletException e) {
            if (SERVLET_PATH.equals(alias)) {
                logger.warn("Could not register the Bosch Smart Camera authorization page at {}: {}", alias,
                        e.getMessage());
            } else {
                logger.info("Could not serve {}, the authorization code has to be pasted in: {}", alias,
                        e.getMessage());
            }
            return false;
        }
    }

    /**
     * Registers a camera under the token that guards its URLs.
     */
    public void addCamera(String token, BoschSmartCamCameraHandler handler) {
        camerasByToken.put(token, handler);
    }

    public void removeCamera(String token) {
        camerasByToken.remove(token);
    }

    /**
     * @param token the token from the requested URL
     * @return the camera behind it, if the token belongs to one
     */
    public Optional<BoschSmartCamCameraHandler> getCamera(@Nullable String token) {
        return token == null ? Optional.empty() : Optional.ofNullable(camerasByToken.get(token));
    }

    public void addAccountHandler(BoschSmartCamAccountHandler handler) {
        if (!handlers.contains(handler)) {
            handlers.add(handler);
        }
    }

    public void removeAccountHandler(BoschSmartCamAccountHandler handler) {
        handlers.remove(handler);
    }

    public List<BoschSmartCamAccountHandler> getAccountHandlers() {
        return List.copyOf(handlers);
    }

    /**
     * @param state the state that was passed to the authorization server, which is the UID of the account thing
     * @return the handler that started this login, if it still exists
     */
    public Optional<BoschSmartCamAccountHandler> getAccountHandler(@Nullable String state) {
        return handlers.stream().filter(handler -> state != null && handler.matchesState(state)).findFirst();
    }

    private String readTemplate(String templateName) throws IOException {
        URL template = bundleContext.getBundle().getEntry(templateName);
        if (template == null) {
            throw new FileNotFoundException("Cannot find " + templateName);
        }
        try (InputStream inputStream = template.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Reference
    protected void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    protected void unsetHttpService(HttpService httpService) {
        this.httpService = null;
    }
}
