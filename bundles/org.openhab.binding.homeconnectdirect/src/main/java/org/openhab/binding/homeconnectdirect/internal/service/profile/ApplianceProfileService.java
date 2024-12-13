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
package org.openhab.binding.homeconnectdirect.internal.service.profile;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.endsWith;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.BINDING_ID;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.BINDING_USERDATA_PATH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FILE_NAME_DEVICE_DESCRIPTION_TEMPLATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FILE_NAME_FEATURE_MAPPING_TEMPLATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FILE_NAME_PROFILE_DESCRIPTION_TEMPLATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HC_ACCOUNT_DETAILS_URL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HC_AUTHORIZE_URL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HC_CLIENT_ID;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HC_DEVICE_INFO_URL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HC_REDIRECT_TARGET;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HC_REDIRECT_TARGET_VALUE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HC_REDIRECT_URI;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HC_SCOPES;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HC_TOKEN_URL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HC_USER_AGENT;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SINGLE_KEY_ID_AUTHORIZE_URL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SINGLE_KEY_ID_BASE_URL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SINGLE_KEY_ID_CLIENT_ID;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SINGLE_KEY_ID_SCOPES;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SINGLE_KEY_ID_STATE_TEMPLATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SINGLE_KEY_ID_STYLES_ID_VALUE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SINGLE_KEY_ID_STYLE_ID;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.BEARER_PREFIX;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.CLIENT_ID;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.CODE;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.CODE_CHALLENGE;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.CODE_CHALLENGE_METHOD;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.CODE_VERIFIER;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.GRANT_TYPE;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.LOGIN;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.NONCE;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.PROMPT;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.REDIRECT_TARGET;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.REDIRECT_URI;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.RESPONSE_TYPE;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.S256;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.SCOPE;
import static org.openhab.binding.homeconnectdirect.internal.HttpConstants.STATE;
import static org.openhab.core.auth.oauth2client.internal.Keyword.AUTHORIZATION_CODE;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.HttpCookieStore;
import org.jsoup.Jsoup;
import org.openhab.binding.homeconnectdirect.internal.service.profile.adapter.OffsetDateTimeAdapter;
import org.openhab.binding.homeconnectdirect.internal.service.profile.converter.DeviceDescriptionConverter;
import org.openhab.binding.homeconnectdirect.internal.service.profile.converter.FeatureMappingConverter;
import org.openhab.binding.homeconnectdirect.internal.service.profile.exception.ApplianceProfileFetchException;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.AccountDetailsResponse;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.ApplianceDescription;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.ApplianceProfile;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.ConnectionType;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.TokenResponse;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.DeviceDescription;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.FeatureMapping;
import org.openhab.binding.homeconnectdirect.internal.servlet.HomeConnectDirectServlet;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

/**
 *
 * Home Connect Direct appliance profile service.
 *
 * @author Jonas Brüstel - Initial Contribution
 */
@NonNullByDefault
@Component(service = ApplianceProfileService.class, scope = ServiceScope.SINGLETON, immediate = true)
public class ApplianceProfileService {

    private final Logger logger;
    private final Gson gson;
    private final HttpClientFactory httpClientFactory;
    private final SecureRandom random;
    private final XStream xstream;
    private String userDataPath;

    @Activate
    public ApplianceProfileService(@Reference HttpClientFactory httpClientFactory) {
        logger = LoggerFactory.getLogger(HomeConnectDirectServlet.class);
        random = new SecureRandom();
        this.httpClientFactory = httpClientFactory;

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeAdapter());
        gson = gsonBuilder.create();

        userDataPath = BINDING_USERDATA_PATH;
        createProfileDirectory();

        xstream = new XStream(new StaxDriver());
        xstream.allowTypesByWildcard(new String[] { ApplianceProfileService.class.getPackageName() + ".**" });
        xstream.setClassLoader(getClass().getClassLoader());
        xstream.processAnnotations(DeviceDescription.class);
        xstream.ignoreUnknownElements();
        xstream.alias("device", DeviceDescription.class);
        xstream.alias("featureMappingFile", FeatureMapping.class);
        xstream.registerConverter(new DeviceDescriptionConverter());
        xstream.registerConverter(new FeatureMappingConverter());
    }

    protected void setUserDataPath(String userDataPath) {
        this.userDataPath = userDataPath;
    }

    public String getUserDataPath() {
        return userDataPath;
    }

    public List<ApplianceProfile> getProfiles() {
        var profiles = new ArrayList<ApplianceProfile>();
        try {
            File directory = new File(userDataPath);
            File[] jsonFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

            if (jsonFiles != null) {
                for (File jsonFile : jsonFiles) {
                    profiles.add(gson.fromJson(new FileReader(jsonFile), ApplianceProfile.class));
                }
            }

        } catch (SecurityException | FileNotFoundException | JsonParseException e) {
            logger.error("Could not read profile files! error={}", e.getMessage());
        }

        return profiles;
    }

    public Optional<ApplianceProfile> getProfile(String haId) {
        return getProfiles().stream().filter(applianceProfile -> Objects.equals(applianceProfile.haId(), haId))
                .findFirst();
    }

    public void deleteProfile(String haId) {
        try {
            File directory = new File(userDataPath);
            File[] jsonFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

            if (jsonFiles != null) {
                for (File jsonFile : jsonFiles) {
                    var profile = gson.fromJson(new FileReader(jsonFile), ApplianceProfile.class);
                    if (Objects.equals(profile.haId(), haId)) {
                        Files.delete(Path.of(userDataPath + File.separator + profile.featureMappingFileName()));
                        Files.delete(Path.of(userDataPath + File.separator + profile.deviceDescriptionFileName()));
                        Files.delete(Path.of(userDataPath + File.separator + jsonFile.getName()));
                    }
                }
            }

        } catch (SecurityException | IOException | JsonParseException e) {
            logger.error("Could not delete profile files! error={}", e.getMessage());
        }
    }

    public Optional<ApplianceProfile> uploadProfileZip(InputStream inputStream) {
        Path profilePath = null;

        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && (endsWith(entry.getName(), ".json") || endsWith(entry.getName(), ".xml"))) {
                    var path = Path.of(userDataPath + File.separator + entry.getName());
                    Files.copy(zipInputStream, path, StandardCopyOption.REPLACE_EXISTING);

                    if (endsWith(entry.getName(), ".json")) {
                        profilePath = path;
                    }
                }
            }

            if (profilePath != null) {
                try (FileReader reader = new FileReader(profilePath.toFile())) {
                    return Optional.of(gson.fromJson(reader, ApplianceProfile.class));
                }
            }
        } catch (IOException ignored) {
        }

        return Optional.empty();
    }

    public boolean downloadProfileZip(String haId, OutputStream outputStream) {
        var profile = getProfile(haId);
        if (profile.isEmpty()) {
            return false;
        }

        var profileJsonContent = gson.toJson(profile.get());

        try (ZipOutputStream zos = new ZipOutputStream(outputStream);
                OutputStreamWriter writer = new OutputStreamWriter(zos)) {

            // json
            ZipEntry zipEntry = new ZipEntry(haId + ".json");
            zos.putNextEntry(zipEntry);
            writer.write(profileJsonContent);
            writer.flush();
            zos.closeEntry();

            // original XMLs
            for (Path path : List.of(
                    Paths.get(userDataPath + File.separator + profile.get().deviceDescriptionFileName()),
                    Paths.get(userDataPath + File.separator + profile.get().featureMappingFileName()))) {
                if (Files.exists(path)) {
                    ZipEntry fileEntry = new ZipEntry(path.getFileName().toString());
                    zos.putNextEntry(fileEntry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } else {
                    logger.warn("Profile file {} does not exist!", profile.get().deviceDescriptionFileName());
                }
            }
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    public ApplianceDescription getDescription(ApplianceProfile profile) {
        var deviceDescription = (DeviceDescription) xstream
                .fromXML(new File(userDataPath + File.separator + profile.deviceDescriptionFileName()));
        var featureMapping = (FeatureMapping) xstream
                .fromXML(new File(userDataPath + File.separator + profile.featureMappingFileName()));

        return new ApplianceDescription(deviceDescription, featureMapping);
    }

    public List<ApplianceProfile> fetchData(String email, String password) {
        var httpClient = httpClientFactory.createHttpClient(BINDING_ID);
        httpClient.setUserAgentField(new HttpField(HttpHeader.USER_AGENT, HC_USER_AGENT));
        httpClient.setFollowRedirects(false);
        var cookieStore = new HttpCookieStore();
        cookieStore.removeAll();
        httpClient.setCookieStore(cookieStore);

        try {
            var codeVerifier = generateNonce(32);
            var codeChallenge = generateCodeChallenge(codeVerifier);
            var nonce = generateNonce(16);
            var state = generateNonce(16);

            httpClient.start();

            // step 1 - Start by fetching the old login page, which gives us the verifier and the
            // challenge to get the token, even after the single key detour.
            var queryParameters = Map.of(RESPONSE_TYPE, CODE, PROMPT, LOGIN, CODE_CHALLENGE, codeChallenge,
                    CODE_CHALLENGE_METHOD, S256, CLIENT_ID, HC_CLIENT_ID, SCOPE, String.join(SPACE, HC_SCOPES), NONCE,
                    nonce, STATE, state, REDIRECT_URI, HC_REDIRECT_URI, REDIRECT_TARGET, HC_REDIRECT_TARGET_VALUE);
            var url = createUrl(HC_AUTHORIZE_URL, queryParameters);

            ContentResponse response = httpClient.GET(url);
            logRequest(response);
            if (response.getStatus() != HttpStatus.OK_200) {
                throw new ApplianceProfileFetchException(
                        "Failed to fetch old login page HTTP error code=" + response.getStatus());
            }

            var document = Jsoup.parse(response.getContentAsString());
            var sessionIdElement = document.selectFirst("input[name=sessionId]");
            var sessionDataElement = document.selectFirst("input[name=sessionData]");
            var sessionId = sessionIdElement != null ? sessionIdElement.val() : null;
            var sessionData = sessionDataElement != null ? sessionDataElement.val() : null;

            if (sessionId == null || sessionData == null) {
                throw new ApplianceProfileFetchException("'sessionId' and 'sessionData' attribute not found!");
            }
            logger.debug("Fetched Home Connect session data. sessionId={}, sessionData={}", sessionId, sessionData);

            // step 2 - single key login flow
            // fetch login page
            var preAuthQueryParameters = Map.of(CLIENT_ID, SINGLE_KEY_ID_CLIENT_ID, REDIRECT_URI, HC_REDIRECT_TARGET,
                    RESPONSE_TYPE, CODE, SCOPE, String.join(SPACE, SINGLE_KEY_ID_SCOPES), PROMPT, LOGIN,
                    SINGLE_KEY_ID_STYLE_ID, SINGLE_KEY_ID_STYLES_ID_VALUE, STATE,
                    String.format(SINGLE_KEY_ID_STATE_TEMPLATE, sessionId));
            var preAuthUrl = createUrl(SINGLE_KEY_ID_AUTHORIZE_URL, preAuthQueryParameters);
            logger.debug("Open SingleKey ID URL. url={}", preAuthUrl);

            ContentResponse preAuthResponse = followRedirects(httpClient.newRequest(preAuthUrl), httpClient);
            logRequest(preAuthResponse);
            var emailFormDocument = Jsoup.parse(preAuthResponse.getContentAsString());
            var requestVerificationTokenElement = emailFormDocument
                    .selectFirst("input[name=\"__RequestVerificationToken\"]");
            if (requestVerificationTokenElement == null) {
                throw new ApplianceProfileFetchException(
                        "Request verification token attribute not found (GET login page)!");
            }
            var requestVerificationToken = requestVerificationTokenElement.val();
            logger.debug("Fetched SingleKey ID verification token. requestVerificationToken={}",
                    requestVerificationToken);

            // enter email
            logger.debug("Send email login form. url={}", preAuthResponse.getRequest().getURI());
            Fields fields = new Fields();
            fields.put("UserIdentifierInput.EmailInput.StringValue", email);
            fields.put("__RequestVerificationToken", requestVerificationToken);
            var emailResponse = followRedirects(
                    httpClient.POST(preAuthResponse.getRequest().getURI()).content(new FormContentProvider(fields)),
                    httpClient);

            var passwordFormDocument = Jsoup.parse(emailResponse.getContentAsString());
            requestVerificationTokenElement = passwordFormDocument
                    .selectFirst("input[name=\"__RequestVerificationToken\"]");
            if (requestVerificationTokenElement == null) {
                throw new ApplianceProfileFetchException(
                        "Request verification token attribute not found (POST email login page)!");
            }
            requestVerificationToken = requestVerificationTokenElement.val();
            logger.debug("Fetched SingleKey ID verification token. requestVerificationToken={}",
                    requestVerificationToken);

            // enter password
            fields = new Fields();
            fields.put("Password", password);
            fields.put("__RequestVerificationToken", requestVerificationToken);
            logger.debug("Send password login form. preAuthUrl={}", emailResponse.getRequest().getURI());
            var loginResponse = followRedirects(httpClient.POST(emailResponse.getRequest().getURI())
                    .content(new FormContentProvider(fields)).followRedirects(false), httpClient);
            logRequest(loginResponse);
            var locationQueryParameter = getQueryParameters(loginResponse.getHeaders().get(HttpHeader.LOCATION));
            var code = locationQueryParameter.get(CODE);
            if (code == null) {
                throw new ApplianceProfileFetchException("SingleKey ID OAauth2 authorization code missing!");
            }
            logger.debug("Fetched SingleKey ID OAuth2 authorization code. code={}", code);

            // step 3 - get access token
            fields = new Fields();
            fields.put(GRANT_TYPE, AUTHORIZATION_CODE);
            fields.put(CLIENT_ID, HC_CLIENT_ID);
            fields.put(CODE_VERIFIER, codeVerifier);
            fields.put(CODE, code);
            fields.put(REDIRECT_URI, HC_REDIRECT_URI);
            var tokenResponse = httpClient.POST(HC_TOKEN_URL).content(new FormContentProvider(fields)).send();
            logRequest(tokenResponse);
            if (tokenResponse.getStatus() != HttpStatus.OK_200) {
                throw new ApplianceProfileFetchException("Could not fetch access token!");
            }
            var token = Objects.requireNonNull(gson.fromJson(tokenResponse.getContentAsString(), TokenResponse.class));
            logger.debug("Received access token. accessToken={}", token.accessToken());

            // step 4 - get appliance info
            var accountDetailsResponse = httpClient.newRequest(HC_ACCOUNT_DETAILS_URL)
                    .header(HttpHeader.AUTHORIZATION, BEARER_PREFIX + token.accessToken()).send();
            logRequest(accountDetailsResponse);
            AccountDetailsResponse accountDetails = Objects.requireNonNull(
                    gson.fromJson(accountDetailsResponse.getContentAsString(), AccountDetailsResponse.class));

            var created = OffsetDateTime.now();
            var profiles = accountDetails.data().homeAppliances().stream().map(homeAppliance -> new ApplianceProfile(
                    homeAppliance.identifier(), homeAppliance.type(), homeAppliance.serialNumber(),
                    homeAppliance.aes() != null && isNotBlank(homeAppliance.aes().key()) ? ConnectionType.AES
                            : ConnectionType.TLS,
                    homeAppliance.aes() != null && isNotBlank(homeAppliance.aes().key()) ? homeAppliance.aes().key()
                            : homeAppliance.tls().key(),
                    homeAppliance.aes() != null && isNotBlank(homeAppliance.aes().key()) ? homeAppliance.aes().iv()
                            : null,
                    String.format(FILE_NAME_FEATURE_MAPPING_TEMPLATE, homeAppliance.identifier()),
                    String.format(FILE_NAME_DEVICE_DESCRIPTION_TEMPLATE, homeAppliance.identifier()), created))
                    .toList();

            // step 5 - get and save device description and feature mapping
            for (ApplianceProfile profile : profiles) {
                var zipFileResponse = httpClient.newRequest(HC_DEVICE_INFO_URL + profile.haId())
                        .header(HttpHeader.AUTHORIZATION, BEARER_PREFIX + token.accessToken()).send();
                if (zipFileResponse.getStatus() != HttpStatus.OK_200) {
                    throw new ApplianceProfileFetchException("Could not fetch device info!");
                }
                try (ZipInputStream zipInputStream = new ZipInputStream(
                        new ByteArrayInputStream(zipFileResponse.getContent()))) {
                    ZipEntry entry;
                    while ((entry = zipInputStream.getNextEntry()) != null) {
                        if (entry.getName().equalsIgnoreCase(profile.featureMappingFileName())) {
                            Files.copy(zipInputStream,
                                    Path.of(userDataPath + File.separator + profile.featureMappingFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        } else if (entry.getName().equalsIgnoreCase(profile.deviceDescriptionFileName())) {
                            Files.copy(zipInputStream,
                                    Path.of(userDataPath + File.separator + profile.deviceDescriptionFileName()),
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }

            // step 6 - save profile
            for (ApplianceProfile profile : profiles) {
                Files.writeString(
                        Path.of(userDataPath + File.separator
                                + String.format(FILE_NAME_PROFILE_DESCRIPTION_TEMPLATE, profile.haId())),
                        gson.toJson(profile), TRUNCATE_EXISTING, CREATE);
            }

            return profiles;

        } catch (Exception e) {
            logger.error("Could not fetch profile data! error={}", e.getMessage());
        } finally {
            try {
                httpClient.stop();
            } catch (Exception ignored) {
            }
        }
        return List.of();
    }

    private void createProfileDirectory() {
        File directory = new File(userDataPath);
        boolean success = false;
        try {
            if (!directory.exists()) {
                success = directory.mkdirs();
            } else {
                success = true;
            }
        } catch (SecurityException ignore) {
        }

        if (!success) {
            logger.error("Could not create profile directory! directory={}", userDataPath);
        }
    }

    private String generateNonce(int num) {
        byte[] codeVerifier = new byte[num];
        random.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }

    private String generateCodeChallenge(String codeVerifier) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private String createUrl(String urlBase, Map<String, String> queryParameters) {
        var urlStringBuilder = new StringBuilder();

        urlStringBuilder.append(urlBase);

        final AtomicInteger queryParameter = new AtomicInteger();
        queryParameters.forEach((key, value) -> {
            if (queryParameter.getAndIncrement() == 0) {
                urlStringBuilder.append("?");
            } else {
                urlStringBuilder.append("&");
            }
            urlStringBuilder.append(key).append("=").append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });

        return urlStringBuilder.toString();
    }

    private ContentResponse followRedirects(Request request, HttpClient httpClient)
            throws ExecutionException, InterruptedException, TimeoutException {
        var response = request.followRedirects(false).send();
        logRequest(response);

        if (response.getStatus() > HttpStatus.MULTIPLE_CHOICES_300
                && response.getStatus() < HttpStatus.BAD_REQUEST_400) {
            var location = Objects.requireNonNull(response.getHeaders().get(HttpHeader.LOCATION));

            if (location.startsWith("hcauth:")) {
                return response;
            } else {
                if (location.startsWith("/")) {
                    location = SINGLE_KEY_ID_BASE_URL + location;
                }
                var redirect = httpClient.newRequest(location);
                return followRedirects(redirect, httpClient);
            }
        } else {
            return response;
        }
    }

    private Map<String, String> getQueryParameters(@Nullable String url) throws URISyntaxException {
        if (url == null) {
            return new LinkedHashMap<>();
        }

        URI uri = new URI(url);
        String query = uri.getQuery();
        Map<String, String> queryParameters = new LinkedHashMap<>();

        if (query != null) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                String key = idx > 0 ? pair.substring(0, idx) : pair;
                String value = idx > 0 && pair.length() > idx + 1 ? pair.substring(idx + 1) : null;
                if (value != null) {
                    queryParameters.put(key, value);
                }
            }
        }

        return queryParameters;
    }

    private void logRequest(ContentResponse response) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} {} -> Status: {}", response.getRequest().getMethod(), response.getRequest().getURI(),
                    response.getStatus());
            response.getRequest().getHeaders()
                    .forEach(header -> logger.debug("> {}: {}", header.getName(), header.getValues()));
            response.getHeaders().forEach(header -> logger.debug("< {}: {}", header.getName(), header.getValues()));
            logger.debug("{}", response.getContentAsString());
        }
    }
}
