/**
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.BINDING_ID;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CONFIGURATION_PID;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SERVLET_ASSETS_PATH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SERVLET_BASE_PATH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SERVLET_WEB_SOCKET_PATH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SUPPORTED_THING_TYPES;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serial;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.homeconnectdirect.internal.handler.BaseHomeConnectDirectHandler;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.ApplianceProfile;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.serializer.ResourceSerializer;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.serializer.ZonedDateTimeSerializer;
import org.openhab.binding.homeconnectdirect.internal.servlet.model.Program;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
    private static final String PATH_DOWNLOAD_LOG = "/download-debug-log";
    private static final String PATH_DOWNLOAD_PROFILE = "/download-profile";
    private static final String PATH_UPLOAD_PROFILE = "/upload-profile";
    private static final String PATH_DELETE_PROFILE = "/delete-profile";
    private static final String DEFAULT_CONTENT_TYPE = "text/html; charset=UTF-8";
    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final String ASSET_CLASSPATH = "assets";
    private static final String CSRF_TOKEN = "CSRF_TOKEN";
    private static final String PROFILE_DOWNLOAD_FILENAME_TEMPLATE = BINDING_ID + "-%s-%s-%s-%s_%s.zip";
    private static final String LOG_DOWNLOAD_FILENAME_TEMPLATE = BINDING_ID + "-%s-%s-%s_%s.log";
    private static final String MULTIPART_KEY = "org.eclipse.jetty.multipartConfig";

    private final Logger logger;
    private final HttpService httpService;
    private final TemplateEngine templateEngine;
    private final ThingRegistry thingRegistry;
    private final ApplianceProfileService applianceProfileService;
    private final ServletUtils utils;
    private final ConfigurationAdmin configurationAdmin;
    private final MultipartConfigElement multipartConfig;
    private final Gson gson;
    private final DateTimeFormatter fileNameDateFormatter;

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

        this.gson = new GsonBuilder().setPrettyPrinting().registerTypeAdapter(Resource.class, new ResourceSerializer())
                .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeSerializer()).create();
        this.fileNameDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

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

        // basic auth
        var configuration = utils.getConfiguration(configurationAdmin);
        if (configuration.basicAuthEnabled) {
            utils.checkAuthorization(request, response, configuration.basicAuthUsername,
                    configuration.basicAuthPassword);
        }

        var path = request.getPathInfo();
        if (StringUtils.startsWith(path, PATH_DOWNLOAD_PROFILE)) {
            sendProfile(request, response);
        } else if (StringUtils.startsWith(path, PATH_DOWNLOAD_LOG)) {
            sendLog(request, response);
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

        // basic auth
        var configuration = utils.getConfiguration(configurationAdmin);
        if (configuration.basicAuthEnabled) {
            utils.checkAuthorization(request, response, configuration.basicAuthUsername,
                    configuration.basicAuthPassword);
        }

        var path = request.getPathInfo();
        if (StringUtils.startsWith(path, PATH_DELETE_PROFILE)) {
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
        templateContext.setVariable("profileDeletePath", SERVLET_BASE_PATH + PATH_DELETE_PROFILE);
        templateContext.setVariable("profileDownloadPath", SERVLET_BASE_PATH + PATH_DOWNLOAD_PROFILE);
        templateContext.setVariable("profileUploadPath", SERVLET_BASE_PATH + PATH_UPLOAD_PROFILE);
        templateContext.setVariable("downloadDebugLogPath", SERVLET_BASE_PATH + PATH_DOWNLOAD_LOG);
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
        var profiles = applianceProfileService.getProfiles();

        if (profiles.isEmpty()) {
            renderUploadProfilePage(context, writer);
        } else {
            // load programs
            HashMap<String, String> programs = new HashMap<>();
            profiles.forEach(profile -> {
                var haId = profile.haId();
                getProgramInformation(profile);
                programs.put(haId, gson.toJson(getProgramInformation(profile)));
            });

            context.setVariable("programMap", programs);
            context.setVariable("profiles", profiles);
            context.setVariable("selectedMenuEntry", "profile");
            templateEngine.process("profiles", context, writer);
        }
    }

    private void renderUploadProfilePage(WebContext context, PrintWriter writer) {
        context.setVariable("selectedMenuEntry", "profile");
        templateEngine.process("upload-profile", context, writer);
    }

    private void sendProfile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var haId = request.getParameter("haId");
        boolean success = false;

        if (haId != null) {
            var profile = applianceProfileService.getProfile(haId);
            if (profile.isPresent()) {
                var type = StringUtils.isNotBlank(profile.get().type()) ? convertToKebabCase(profile.get().type()) : "";
                var brand = StringUtils.isNotBlank(profile.get().brand()) ? profile.get().brand().toLowerCase() : "";
                var vib = StringUtils.isNotBlank(profile.get().vib()) ? profile.get().vib().toLowerCase() : "";
                var mac = StringUtils.isNotBlank(profile.get().mac())
                        ? profile.get().mac().replaceAll("-", "").toLowerCase()
                        : "";
                var filename = String.format(PROFILE_DOWNLOAD_FILENAME_TEMPLATE, type, brand, vib, mac,
                        LocalDateTime.now().format(fileNameDateFormatter));
                response.setContentType(ZIP_CONTENT_TYPE);
                response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

                try {
                    success = applianceProfileService.downloadProfileZip(haId, response.getOutputStream());
                } catch (IOException ignored) {
                }
            }
        }

        if (!success) {
            response.sendError(HttpStatus.INTERNAL_SERVER_ERROR_500);
        }
    }

    private void sendLog(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var thingUid = request.getParameter("uid");
        boolean success = false;

        if (thingUid != null) {
            var thing = getAppliance(thingUid);
            if (thing.isPresent()) {
                var haId = thing.get().getConfiguration().get("haId") + "";
                var profile = applianceProfileService.getProfile(haId);

                if (profile.isPresent()) {
                    var type = StringUtils.isNotBlank(profile.get().type()) ? convertToKebabCase(profile.get().type())
                            : "";
                    var brand = StringUtils.isNotBlank(profile.get().brand()) ? profile.get().brand().toLowerCase()
                            : "";
                    var vib = StringUtils.isNotBlank(profile.get().vib()) ? profile.get().vib().toLowerCase() : "";
                    var filename = String.format(LOG_DOWNLOAD_FILENAME_TEMPLATE, type, brand, vib,
                            LocalDateTime.now().format(fileNameDateFormatter));
                    response.setContentType(ZIP_CONTENT_TYPE);
                    response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

                    try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream());
                            OutputStreamWriter writer = new OutputStreamWriter(zos)) {

                        if (thing.get().getHandler() instanceof BaseHomeConnectDirectHandler handler) {
                            // events
                            ZipEntry zipEntry = new ZipEntry("websocket-messages-" + haId + ".json");
                            zos.putNextEntry(zipEntry);
                            writer.write(gson.toJson(handler.getApplianceMessages()));
                            writer.flush();
                            zos.closeEntry();
                        }

                        // original XMLs
                        for (Path path : List.of(
                                Paths.get(applianceProfileService.getUserDataPath() + File.separator
                                        + profile.get().deviceDescriptionFileName()),
                                Paths.get(applianceProfileService.getUserDataPath() + File.separator
                                        + profile.get().featureMappingFileName()))) {
                            if (Files.exists(path)) {
                                ZipEntry fileEntry = new ZipEntry(path.getFileName().toString());
                                zos.putNextEntry(fileEntry);
                                Files.copy(path, zos);
                                zos.closeEntry();
                            } else {
                                logger.warn("Profile file {} does not exist!",
                                        profile.get().deviceDescriptionFileName());
                            }
                        }
                        success = true;
                    } catch (IOException ignored) {
                    }
                }
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

    private List<Program> getProgramInformation(ApplianceProfile profile) {
        var description = applianceProfileService.getDescription(profile);
        return description.deviceDescription().programList.stream().map(program -> {
            var programUid = program.uid;
            var programKey = description.featureMapping().featureMap.get(programUid);

            return new Program(programUid, programKey);
        }).toList();
    }

    @Deactivate
    protected void dispose() {
        httpService.unregister(SERVLET_BASE_PATH);
        httpService.unregister(SERVLET_ASSETS_PATH);
    }

    private String convertToKebabCase(String input) {
        Pattern pattern = Pattern.compile("([a-z])([A-Z])");
        Matcher matcher = pattern.matcher(input);
        return matcher.replaceAll("$1-$2").toLowerCase();
    }
}
