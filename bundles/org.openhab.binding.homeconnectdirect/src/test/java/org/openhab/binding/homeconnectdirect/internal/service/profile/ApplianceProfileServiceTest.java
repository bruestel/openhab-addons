package org.openhab.binding.homeconnectdirect.internal.service.profile;

import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.ApplianceProfile;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.ConnectionType;
import org.openhab.core.io.net.http.HttpClientFactory;

class ApplianceProfileServiceTest {

    @ParameterizedTest(name = "[{index}] {0} ({1})")
    @DisplayName("Test parsing of Home Connect XML files")
    @CsvSource({
            "SIEMENS WM16XE91,Washer,SIEMENS-WM16XE91-000000000000_FeatureMapping.xml,SIEMENS-WM16XE91-000000000000_DeviceDescription.xml,19,28,24,14,38,26,58,256,257",
            "SIEMENS TI9555X1DE,CoffeeMaker,SIEMENS-TI9555X1DE-000000000000_FeatureMapping.xml,SIEMENS-TI9555X1DE-000000000000_DeviceDescription.xml,23,17,57,14,12,38,28,256,257",
            "SIEMENS SN658X06TE,Dishwasher,SIEMENS-SN658X06TE-000000000000_FeatureMapping.xml,SIEMENS-SN658X06TE-000000000000_DeviceDescription.xml,10,20,17,8,10,9,18,256,257",
            "BOSCH MCC9555DWC,CookProcessor,BOSCH-MCC9555DWC-000000000000_FeatureMapping.xml,BOSCH-MCC9555DWC-000000000000_DeviceDescription.xml,3,7,8,11,33,67,11,256,257" })
    public void testGetApplianceDescription(String appliance, String type, String givenFeatureMappingFileName,
            String givenDeviceDescriptionFileName, int expectedStatusListSize, int expectedSettingListSize,
            int expectedEventListSize, int expectedCommandListSize, int expectedOptionListSize,
            int expectedProgramListSize, int expectedEnumerationTypeListSize, int expectedActiveProgramUid,
            int expectedSelectedProgramUid) throws URISyntaxException {
        // given
        var httpClientFactory = Mockito.mock(HttpClientFactory.class);
        var service = new ApplianceProfileService(httpClientFactory);
        Path path = Paths
                .get(requireNonNull(requireNonNull(getClass().getClassLoader()).getResource("userdata")).toURI());
        service.setUserDataPath(path.toAbsolutePath().toString());
        var profile = new ApplianceProfile("SIEMENS-WM16XE91-000000000000", type, "4711", ConnectionType.AES,
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "AAAAAAAAAAAAAAAAAAAAAA", givenFeatureMappingFileName,
                givenDeviceDescriptionFileName, OffsetDateTime.now());

        // when
        var result = service.getDescription(profile);

        // then
        assertNotNull(result);
        assertEquals(expectedStatusListSize, result.deviceDescription().statusList.size());
        assertEquals(expectedSettingListSize, result.deviceDescription().settingList.size());
        assertEquals(expectedEventListSize, result.deviceDescription().eventList.size());
        assertEquals(expectedCommandListSize, result.deviceDescription().commandList.size());
        assertEquals(expectedOptionListSize, result.deviceDescription().optionList.size());
        assertEquals(expectedProgramListSize, result.deviceDescription().programList.size());
        assertEquals(expectedEnumerationTypeListSize, result.deviceDescription().enumerationTypeList.size());
        assertEquals(expectedActiveProgramUid, result.deviceDescription().activeProgramUid.orElseThrow());
        assertEquals(expectedSelectedProgramUid, result.deviceDescription().selectedProgramUid.orElseThrow());
        assertFalse(result.featureMapping().featureMap.isEmpty());
        assertFalse(result.featureMapping().enumDescriptionList.isEmpty());
        assertFalse(result.featureMapping().errorMap.isEmpty());
    }
}
