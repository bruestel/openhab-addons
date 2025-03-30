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
package org.openhab.binding.homeconnectdirect.internal.service.profile;

import static org.apache.commons.lang3.StringUtils.endsWith;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.BINDING_USERDATA_PATH;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.homeconnectdirect.internal.service.profile.adapter.OffsetDateTimeAdapter;
import org.openhab.binding.homeconnectdirect.internal.service.profile.converter.DeviceDescriptionConverter;
import org.openhab.binding.homeconnectdirect.internal.service.profile.converter.FeatureMappingConverter;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.ApplianceDescription;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.ApplianceProfile;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.DeviceDescription;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.xml.FeatureMapping;
import org.openhab.binding.homeconnectdirect.internal.servlet.HomeConnectDirectServlet;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
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
    private final XStream xstream;
    private String userDataPath;

    @Activate
    public ApplianceProfileService() {
        logger = LoggerFactory.getLogger(HomeConnectDirectServlet.class);

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
        } catch (IOException e) {
            logger.debug("Could not save profile! error={}", e.getMessage());
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
}
