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
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.handler.BoschSmartCamAccountHandler;
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
    private final List<String> extraAliases = new CopyOnWriteArrayList<>();

    @Activate
    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        bundleContext = componentContext.getBundleContext();
        try {
            HttpServlet servlet = createServlet();
            httpService.registerServlet(SERVLET_PATH, servlet, null, httpService.createDefaultHttpContext());
            logger.debug("Registered the Bosch Smart Camera authorization servlet at {}", SERVLET_PATH);
            // both are conveniences: the code can always be pasted into the page instead
            registerAlias(CALLBACK_PATH, servlet);
            registerAlias(DECLINE_PATH, servlet);
        } catch (NamespaceException | ServletException | IOException e) {
            logger.warn("Could not register the Bosch Smart Camera authorization servlet: {}", e.getMessage());
        }
    }

    @Deactivate
    protected void deactivate(ComponentContext componentContext) {
        httpService.unregister(SERVLET_PATH);
        for (String alias : extraAliases) {
            httpService.unregister(alias);
        }
        extraAliases.clear();
    }

    private void registerAlias(String alias, HttpServlet servlet) throws ServletException {
        try {
            httpService.registerServlet(alias, servlet, null, httpService.createDefaultHttpContext());
            extraAliases.add(alias);
            logger.debug("Registered the authorization servlet at {} as well", alias);
        } catch (NamespaceException e) {
            logger.debug("{} is already in use, the authorization code has to be pasted in", alias);
        }
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

    private HttpServlet createServlet() throws IOException {
        return new BoschSmartCamAuthServlet(this, readTemplate(TEMPLATE_INDEX), readTemplate(TEMPLATE_ACCOUNT));
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
