/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.homeconnectdirect.internal.servlet;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CONFIGURATION_PID;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SERVLET_ASSETS_PATH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SERVLET_BASE_PATH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SERVLET_WEB_SOCKET_PATH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SUPPORTED_THING_TYPES;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serial;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JavaxServletWebApplication;

/**
 *
 * Home Connect Direct servlet.
 *
 * @author Jonas Brüstel - Initial Contribution
 */
@NonNullByDefault
@Component(service = HomeConnectDirectServlet.class, scope = ServiceScope.SINGLETON, immediate = true)
public class HomeConnectDirectServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = -3227785548304622034L;
    private static final String PATH_APPLIANCE = "/appliance/";
    private static final String PATH_UPDATE_PROFILES = "/update-profiles";
    private static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";
    private static final String ASSET_CLASSPATH = "assets";

    private final Logger logger;
    private final HttpService httpService;
    private final TemplateEngine templateEngine;
    private final ThingRegistry thingRegistry;
    private final ApplianceProfileService applianceProfileService;
    private final ServletUtils utils;
    private final ConfigurationAdmin configurationAdmin;

    // TODO impl CSRF to protect endpoints

    @Activate
    public HomeConnectDirectServlet(@Reference HttpService httpService, @Reference ThingRegistry thingRegistry,
            @Reference ApplianceProfileService applianceProfileService,
            @Reference ConfigurationAdmin configurationAdmin) {
        logger = LoggerFactory.getLogger(HomeConnectDirectServlet.class);
        utils = new ServletUtils();
        this.httpService = httpService;
        this.thingRegistry = thingRegistry;
        this.applianceProfileService = applianceProfileService;
        this.configurationAdmin = configurationAdmin;

        // register servlet
        try {
            logger.debug("Initialize Home Connect Direct servlet ({})", SERVLET_BASE_PATH);
            httpService.registerServlet(SERVLET_BASE_PATH, this, null, httpService.createDefaultHttpContext());
            httpService.registerResources(SERVLET_ASSETS_PATH, ASSET_CLASSPATH, null);
        } catch (ServletException | NamespaceException e) {
            logger.warn("Could not register Home Connect servlet! ({})", SERVLET_BASE_PATH, e);
        }

        // setup template engine
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateResolver.setPrefix("/templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setCacheable(true);
        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(templateResolver);
    }

    @Override
    protected void doGet(@NonNullByDefault({}) HttpServletRequest request,
            @NonNullByDefault({}) HttpServletResponse response) throws IOException {
        var writer = response.getWriter();
        var templateContext = prepareContext(request, response);
        prepareResponse(response);

        var path = request.getPathInfo();
        if (StringUtils.startsWith(path, PATH_APPLIANCE)) {
            var thingUid = StringUtils.substringAfter(path, PATH_APPLIANCE);
            getAppliance(thingUid).ifPresentOrElse(thing -> renderAppliancePage(templateContext, writer, thing),
                    () -> renderNotFound(response));

        } else if (StringUtils.startsWith(path, PATH_UPDATE_PROFILES)) {
            renderUpdateProfilesPage(templateContext, writer);
        } else {
            renderProfilePage(templateContext, writer);
        }
    }

    @Override
    protected void doPost(@NonNullByDefault({}) HttpServletRequest request,
            @NonNullByDefault({}) HttpServletResponse response) throws ServletException, IOException {
        var writer = response.getWriter();
        var templateContext = prepareContext(request, response);

        var path = request.getPathInfo();
        if (StringUtils.startsWith(path, PATH_UPDATE_PROFILES) && getSingleKeyIdCredentials().isPresent()) {
            var credentials = getSingleKeyIdCredentials().get();
            var profiles = applianceProfileService.fetchData(credentials.getKey(), credentials.getValue());

            templateContext.setVariable("profiles", profiles);
            templateContext.setVariable("done", true);

            renderUpdateProfilesPage(templateContext, writer);
        } else {
            super.doPost(request, response);
        }

        prepareResponse(response);
    }

    private WebContext prepareContext(HttpServletRequest request, HttpServletResponse response) {
        var application = JavaxServletWebApplication.buildApplication(request.getServletContext());
        var exchange = application.buildExchange(request, response);
        var templateContext = new WebContext(exchange);

        templateContext.setVariable("appliances", getAppliances());
        templateContext.setVariable("utils", utils);
        templateContext.setVariable("basePath", SERVLET_BASE_PATH);
        templateContext.setVariable("assetPath", SERVLET_ASSETS_PATH);
        templateContext.setVariable("appliancePath", SERVLET_BASE_PATH + PATH_APPLIANCE);
        templateContext.setVariable("profileUpdatePath", SERVLET_BASE_PATH + PATH_UPDATE_PROFILES);
        templateContext.setVariable("configurationPid", CONFIGURATION_PID);

        return templateContext;
    }

    private void prepareResponse(HttpServletResponse response) {
        response.setContentType(DEFAULT_CONTENT_TYPE);
        response.setCharacterEncoding(UTF_8.name());
    }

    private void renderProfilePage(WebContext context, PrintWriter writer) {
        context.setVariable("profilePath", HomeConnectDirectBindingConstants.BINDING_USERDATA_PATH);
        context.setVariable("profiles", applianceProfileService.getProfiles());
        context.setVariable("selectedMenuEntry", "profile");
        templateEngine.process("profiles", context, writer);
    }

    private void renderUpdateProfilesPage(WebContext context, PrintWriter writer) {
        var singleKeyIdCredentials = getSingleKeyIdCredentials();
        var username = singleKeyIdCredentials.isPresent() ? singleKeyIdCredentials.get().getKey() : "";

        context.setVariable("bindingConfigurationPresent", singleKeyIdCredentials.isPresent());
        context.setVariable("username", username);
        context.setVariable("selectedMenuEntry", "profile");
        templateEngine.process("update-profiles", context, writer);
    }

    private void renderAppliancePage(WebContext context, PrintWriter writer, Thing thing) {
        String encodedThingUid = URLEncoder.encode(thing.getUID().toString(), StandardCharsets.UTF_8);

        context.setVariable("selectedMenuEntry", thing.getUID());
        context.setVariable("name", thing.getLabel());
        context.setVariable("uid", thing.getUID().toString());
        context.setVariable("webSocketUrl", SERVLET_WEB_SOCKET_PATH + encodedThingUid);
        context.setVariable("status", thing.getStatus());
        // TODO show Programs and features
        // TODO impl. missing actions in web ui

        templateEngine.process("appliance", context, writer);
    }

    private void renderNotFound(HttpServletResponse response) {
        try {
            response.sendError(HttpStatus.NOT_FOUND_404, "Not found!");
        } catch (IOException e) {
            logger.error("Could not send 404 error! error={}", e.getMessage());
        }
    }

    private List<Thing> getAppliances() {
        return thingRegistry.stream().filter(thing -> SUPPORTED_THING_TYPES.contains(thing.getThingTypeUID())).toList();
    }

    private Optional<Thing> getAppliance(String thingUid) {
        return thingRegistry.stream().filter(thing -> SUPPORTED_THING_TYPES.contains(thing.getThingTypeUID()))
                .filter(thing -> thing.getUID().toString().equals(thingUid)).findFirst();
    }

    private Optional<Pair<String, String>> getSingleKeyIdCredentials() {
        try {
            var config = configurationAdmin.getConfiguration(CONFIGURATION_PID);
            var properties = config.getProperties();
            if (properties != null) {
                var usernameObject = properties.get("singleKeyIdUsername");
                var passwordObject = properties.get("singleKeyIdPassword");
                if (usernameObject instanceof String username && passwordObject instanceof String password
                        && isNotBlank(username) && isNotBlank(password)) {
                    return Optional.of(Pair.of(username, password));
                }
            }
        } catch (IOException e) {
            logger.error("Could not read binding configuration! error={}", e.getMessage());
        }
        return Optional.empty();
    }

    @Deactivate
    protected void dispose() {
        httpService.unregister(SERVLET_BASE_PATH);
        httpService.unregister(SERVLET_ASSETS_PATH);
    }
}
