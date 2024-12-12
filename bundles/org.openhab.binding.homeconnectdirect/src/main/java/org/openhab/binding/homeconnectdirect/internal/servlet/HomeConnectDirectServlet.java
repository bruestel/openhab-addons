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
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.BINDING_ID;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CONFIGURATION_PID;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SERVLET_ASSETS_PATH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SERVLET_BASE_PATH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SERVLET_WEB_SOCKET_PATH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SUPPORTED_THING_TYPES;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.ApplianceProfile;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Serial;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

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
    private static final String PATH_DOWNLOAD_PROFILE = "/download-profile";
    private static final String PATH_UPLOAD_PROFILE = "/upload-profile";
    private static final String PATH_DELETE_PROFILE = "/delete-profile";
    private static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";
    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final String ASSET_CLASSPATH = "assets";
    private static final String CSRF_TOKEN = "CSRF_TOKEN";
    private static final String DOWNLOAD_FILENAME_TEMPLATE = BINDING_ID + "-%s.zip";
    private static final String MULTIPART_KEY = "org.eclipse.jetty.multipartConfig";

    private final Logger logger;
    private final HttpService httpService;
    private final TemplateEngine templateEngine;
    private final ThingRegistry thingRegistry;
    private final ApplianceProfileService applianceProfileService;
    private final ServletUtils utils;
    private final ConfigurationAdmin configurationAdmin;
    private final MultipartConfigElement multipartConfig;

    // TODO protect whole page via basic auth (optional)
    // TODO change menu - separator between profiles and things
    // TODO show Programs and features

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

        // multipart config
        multipartConfig = new MultipartConfigElement("", 1024 * 1024 * 5, // 5 MB
                1024 * 1024 * 10, // 10 MB
                1024 * 1024 // 1 MB
        );

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
        var path = request.getPathInfo();

        if (StringUtils.startsWith(path, PATH_DOWNLOAD_PROFILE)) {
            sendProfile(request, response);
        } else {
            var writer = response.getWriter();
            var templateContext = prepareContext(request, response);
            prepareResponse(response);
            var csrfToken = setCsrfToken(response);
            templateContext.setVariable(CSRF_TOKEN, csrfToken);

            if (StringUtils.startsWith(path, PATH_APPLIANCE)) {
                var thingUid = StringUtils.substringAfter(path, PATH_APPLIANCE);
                getAppliance(thingUid).ifPresentOrElse(thing -> renderAppliancePage(templateContext, writer, thing),
                        () -> renderNotFound(response));

            } else if (StringUtils.startsWith(path, PATH_UPDATE_PROFILES)) {
                renderUpdateProfilesPage(templateContext, writer);
            } else if (StringUtils.startsWith(path, PATH_UPLOAD_PROFILE)) {
                renderUploadProfilePage(templateContext, writer);
            } else {
                renderProfilePage(templateContext, writer);
            }
        }
    }

    @Override
    protected void doPost(@NonNullByDefault({}) HttpServletRequest request,
            @NonNullByDefault({}) HttpServletResponse response) throws ServletException, IOException {
        var writer = response.getWriter();
        var templateContext = prepareContext(request, response);
        prepareResponse(response);

        var path = request.getPathInfo();
        if (StringUtils.startsWith(path, PATH_UPDATE_PROFILES) && getSingleKeyIdCredentials().isPresent()) {
            if (isCsrfTokenValid(request)) {
                var credentials = getSingleKeyIdCredentials().get();
                var profiles = applianceProfileService.fetchData(credentials.getKey(), credentials.getValue());

                templateContext.setVariable("profiles", profiles);
                templateContext.setVariable("done", true);

                renderUpdateProfilesPage(templateContext, writer);
            } else {
                renderForbiddenError(response, "Invalid CSRF token!");
            }
        } else if (StringUtils.startsWith(path, PATH_DELETE_PROFILE) && getSingleKeyIdCredentials().isPresent()) {
            var haId = request.getParameter("haId");
            if (isCsrfTokenValid(request) && haId != null) {
                applianceProfileService.deleteProfile(haId);
                response.sendRedirect(SERVLET_BASE_PATH);
            } else {
                renderForbiddenError(response, "Invalid CSRF token!");
            }
        } else if (StringUtils.startsWith(path, PATH_UPLOAD_PROFILE)) {
            request.setAttribute(MULTIPART_KEY, multipartConfig);

            if (isCsrfTokenValid(request)) {
                Part filePart = request.getPart("zipFile");
                if (filePart == null || filePart.getSubmittedFileName() == null) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    writer.write("No file submitted!");
                    return;
                }

                String fileName = filePart.getSubmittedFileName();
                if (!fileName.endsWith(".zip")) {
                    response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
                    writer.write("Only zip files are supported!");
                    return;
                }

                Optional<ApplianceProfile> profile;
                try (InputStream inputStream = filePart.getInputStream()) {
                    profile = applianceProfileService.uploadProfileZip(inputStream);
                }

                if (profile.isPresent()) {
                    templateContext.setVariable("done", true);
                    templateContext.setVariable("profile", profile.get());
                    renderUploadProfilePage(templateContext, writer);
                } else {
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } else {
                renderForbiddenError(response, "Invalid CSRF token!");
            }
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
        templateContext.setVariable("profileDeletePath", SERVLET_BASE_PATH + PATH_DELETE_PROFILE);
        templateContext.setVariable("profileDownloadPath", SERVLET_BASE_PATH + PATH_DOWNLOAD_PROFILE);
        templateContext.setVariable("profileUploadPath", SERVLET_BASE_PATH + PATH_UPLOAD_PROFILE);
        templateContext.setVariable("configurationPid", CONFIGURATION_PID);

        return templateContext;
    }

    private void prepareResponse(HttpServletResponse response) {
        response.setContentType(DEFAULT_CONTENT_TYPE);
        response.setCharacterEncoding(UTF_8.name());
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Embedder-Policy", "require-corp");
        response.setHeader("Cross-Origin-Resource-Policy", "same-site");
    }

    private String setCsrfToken(HttpServletResponse response) {
        var token = UUID.randomUUID().toString();

        Cookie csrfCookie = new Cookie(CSRF_TOKEN, token);
        csrfCookie.setHttpOnly(true);
        csrfCookie.setPath("/");
        csrfCookie.setMaxAge(-1);
        response.addCookie(csrfCookie);

        return token;
    }

    private boolean isCsrfTokenValid(HttpServletRequest request) {
        var requestToken = request.getParameter(CSRF_TOKEN);
        if (requestToken == null) {
            try {
                var part = request.getPart(CSRF_TOKEN);
                if (part != null) {
                    requestToken = new String(part.getInputStream().readAllBytes());
                }
            } catch (IOException | ServletException ignored) {
            }
        }
        String cookieToken = null;
        var cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (CSRF_TOKEN.equals(cookie.getName())) {
                    cookieToken = cookie.getValue();
                    break;
                }
            }
        }

        return cookieToken != null && requestToken != null && Objects.equals(cookieToken, requestToken);
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

    private void renderUploadProfilePage(WebContext context, PrintWriter writer) {
        context.setVariable("selectedMenuEntry", "profile");
        templateEngine.process("upload-profile", context, writer);
    }

    private void sendProfile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var haId = request.getParameter("haId");
        boolean success = false;

        if (haId != null) {
            var filename = String.format(DOWNLOAD_FILENAME_TEMPLATE, StringUtils.toRootLowerCase(haId));
            response.setContentType(ZIP_CONTENT_TYPE);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            try {
                success = applianceProfileService.downloadProfileZip(haId, response.getOutputStream());
            } catch (IOException ignored) {
            }
        }

        if (!success) {
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }
    }

    private void renderAppliancePage(WebContext context, PrintWriter writer, Thing thing) {
        String encodedThingUid = URLEncoder.encode(thing.getUID().toString(), StandardCharsets.UTF_8);

        context.setVariable("selectedMenuEntry", thing.getUID());
        context.setVariable("name", thing.getLabel());
        context.setVariable("uid", thing.getUID().toString());
        context.setVariable("webSocketUrl", SERVLET_WEB_SOCKET_PATH + encodedThingUid);
        context.setVariable("status", thing.getStatus());

        templateEngine.process("appliance", context, writer);
    }

    private void renderNotFound(HttpServletResponse response) {
        try {
            response.sendError(HttpStatus.NOT_FOUND_404, "Not found!");
        } catch (IOException e) {
            logger.error("Could not send 404 error! error={}", e.getMessage());
        }
    }

    private void renderForbiddenError(HttpServletResponse response, String message) {
        try {
            response.sendError(HttpStatus.FORBIDDEN_403, message);
        } catch (IOException e) {
            logger.error("Could not send 403 error! error={}", e.getMessage());
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
