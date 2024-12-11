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
package org.openhab.binding.homeconnectdirect.internal;

import java.io.File;
import java.time.Duration;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.OpenHAB;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.type.ChannelTypeUID;

/**
 * The {@link HomeConnectDirectBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class HomeConnectDirectBindingConstants {

    public static final String BINDING_ID = "homeconnectdirect";
    public static final String HOME_APPLIANCE_ID = "haId";
    public static final String CONFIGURATION_PID = "binding.homeconnectdirect";

    // List of all appliances
    public static final String APPLIANCE_TYPE_GENERIC = "generic";
    public static final String APPLIANCE_TYPE_WASHER = "washer";
    public static final String APPLIANCE_TYPE_WASHER_AND_DRYER = "washerdryer";
    public static final String APPLIANCE_TYPE_DRYER = "dryer";
    public static final String APPLIANCE_TYPE_DISHWASHER = "dishwasher";
    public static final String APPLIANCE_TYPE_COOK_PROCESSOR = "cookprocessor";
    public static final String APPLIANCE_TYPE_COFFEE_MAKER = "coffeemaker";
    public static final String APPLIANCE_TYPE_OVEN = "oven";

    // List of all thing type UIDs
    public static final ThingTypeUID THING_TYPE_GENERIC = new ThingTypeUID(BINDING_ID, APPLIANCE_TYPE_GENERIC);
    public static final ThingTypeUID THING_TYPE_DISHWASHER = new ThingTypeUID(BINDING_ID, APPLIANCE_TYPE_DISHWASHER);
    public static final ThingTypeUID THING_TYPE_WASHER = new ThingTypeUID(BINDING_ID, APPLIANCE_TYPE_WASHER);
    public static final ThingTypeUID THING_TYPE_WASHER_DRYER = new ThingTypeUID(BINDING_ID,
            APPLIANCE_TYPE_WASHER_AND_DRYER);
    public static final ThingTypeUID THING_TYPE_DRYER = new ThingTypeUID(BINDING_ID, APPLIANCE_TYPE_DRYER);
    public static final ThingTypeUID THING_TYPE_COFFEE_MAKER = new ThingTypeUID(BINDING_ID,
            APPLIANCE_TYPE_COFFEE_MAKER);
    public static final ThingTypeUID THING_TYPE_COOK_PROCESSOR = new ThingTypeUID(BINDING_ID,
            APPLIANCE_TYPE_COOK_PROCESSOR);
    public static final ThingTypeUID THING_TYPE_OVEN = new ThingTypeUID(BINDING_ID, APPLIANCE_TYPE_OVEN);

    // List of all channel type UIDs
    public static final ChannelTypeUID CHANNEL_TYPE_SWITCH = new ChannelTypeUID(BINDING_ID, "switch");
    public static final ChannelTypeUID CHANNEL_TYPE_STRING = new ChannelTypeUID(BINDING_ID, "string");
    public static final ChannelTypeUID CHANNEL_TYPE_NUMBER = new ChannelTypeUID(BINDING_ID, "number");

    // Supported thing types
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_GENERIC, THING_TYPE_DISHWASHER,
            THING_TYPE_WASHER, THING_TYPE_WASHER_DRYER, THING_TYPE_DRYER, THING_TYPE_COFFEE_MAKER,
            THING_TYPE_COOK_PROCESSOR, THING_TYPE_OVEN);

    // Configuration properties
    public static final String PROPERTY_HOME_APPLIANCE_ID = HOME_APPLIANCE_ID;
    public static final String PROPERTY_ADDRESS = "address";
    public static final String PROPERTY_CONNECTION_TYPE = "connectionType";

    // Configuration property values
    public static final String CONNECTION_TYPE_AES = "AES_HMAC_SHA256";
    public static final String CONNECTION_TYPE_TLS = "TLS";
    public static final int CONNECTION_TYPE_AES_PORT = 80;
    public static final int CONNECTION_TYPE_TLS_PORT = 443;

    // Misc
    public static final String BINDING_USERDATA_PATH = OpenHAB.getUserDataFolder() + File.separator + BINDING_ID;
    public static final String CONSCRYPT_PROVIDER = "Conscrypt";
    public static final String CONFIGURATION_EVENT_KEY = "eventKey";

    // Home Connect Http Client
    public static final String HC_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36";
    public static final String HC_BASE_URL = "https://api.home-connect.com/security/oauth";
    public static final String HC_AUTHORIZE_URL = HC_BASE_URL + "/authorize";
    public static final String HC_TOKEN_URL = HC_BASE_URL + "/token";
    public static final String HC_REDIRECT_TARGET = HC_BASE_URL + "/redirect_target";
    public static final String HC_ASSET_URL = "https://prod.reu.rest.homeconnectegw.com";
    public static final String HC_ACCOUNT_DETAILS_URL = HC_ASSET_URL + "/account/details";
    public static final String HC_DEVICE_INFO_URL = HC_ASSET_URL + "/api/iddf/v1/iddf/";
    public static final String HC_CLIENT_ID = "9B75AC9EC512F36C84256AC47D813E2C1DD0D6520DF774B020E1E6E2EB29B1F3";
    public static final Set<String> HC_SCOPES = Set.of("ReadOrigApi");
    public static final String HC_REDIRECT_URI = "hcauth://auth/prod";
    public static final String HC_REDIRECT_TARGET_VALUE = "icore";
    public static final String SINGLE_KEY_ID_BASE_URL = "https://singlekey-id.com";
    public static final String SINGLE_KEY_ID_AUTHORIZE_URL = SINGLE_KEY_ID_BASE_URL + "/auth/connect/authorize";
    public static final String SINGLE_KEY_ID_CLIENT_ID = "11F75C04-21C2-4DA9-A623-228B54E9A256";
    public static final Set<String> SINGLE_KEY_ID_SCOPES = Set.of("openid", "email", "profile", "offline_access",
            "homeconnect.general");
    public static final String SINGLE_KEY_ID_STYLE_ID = "style_id";
    public static final String SINGLE_KEY_ID_STYLES_ID_VALUE = "bsh_hc_01";
    public static final String SINGLE_KEY_ID_STATE_TEMPLATE = "{\"session_id\":\"%s\"}";
    public static final String FILE_NAME_FEATURE_MAPPING_TEMPLATE = "%s_FeatureMapping.xml";
    public static final String FILE_NAME_DEVICE_DESCRIPTION_TEMPLATE = "%s_DeviceDescription.xml";
    public static final String FILE_NAME_PROFILE_DESCRIPTION_TEMPLATE = "%s.json";

    // Home Connect WebSocket Client
    public static final String WS_DEVICE_TYPE = "Application";
    public static final String WS_DEVICE_NAME = "HC Direct";
    public static final String WS_DEVICE_ID = "cafebabe";
    public static final String WS_AES_URI_TEMPLATE = "ws://%s:80/homeconnect";
    public static final String WS_TLS_URI_TEMPLATE = "wss://%s:443/homeconnect";
    public static final Duration WS_INACTIVITY_TIMEOUT = Duration.ofSeconds(60);
    public static final Duration WS_INACTIVITY_CHECK_INTERVAL = Duration.ofSeconds(10);
    public static final Duration WS_INACTIVITY_CHECK_INITIAL_DELAY = Duration.ofSeconds(5);
    public static final Duration WS_PING_INTERVAL = Duration.ofSeconds(30);
    public static final Duration WS_PING_INITIAL_DELAY = Duration.ofSeconds(5);

    // Servlet
    public static final String SERVLET_BASE_PATH = "/" + BINDING_ID;
    public static final String SERVLET_ASSETS_PATH = SERVLET_BASE_PATH + "/assets";
    public static final String SERVLET_WEB_SOCKET_PATH = SERVLET_BASE_PATH + "/ws/";
    public static final String SERVLET_WEB_SOCKET_PATTERN = SERVLET_WEB_SOCKET_PATH + "*";

    // Misc Appliance Keys
    public static final String POWER_STATE = "BSH.Common.Setting.PowerState";
    public static final String POWER_STATE_ENUM_KEY = "BSH.Common.EnumType.PowerState";
    public static final String ACTIVE_PROGRAM = "BSH.Common.Root.ActiveProgram";
    public static final String SELECTED_PROGRAM = "BSH.Common.Root.SelectedProgram";
    public static final String ABORT_PROGRAM = "BSH.Common.Command.AbortProgram";
    public static final String PAUSE_PROGRAM = "BSH.Common.Command.PauseProgram";
    public static final String RESUME_PROGRAM = "BSH.Common.Command.ResumeProgram";
    public static final String WASHER_I_DOS_1_ACTIVE = "LaundryCare.Washer.Option.IDos1.Active";
    public static final String WASHER_I_DOS_2_ACTIVE = "LaundryCare.Washer.Option.IDos2.Active";
    public static final String WASHER_TEMPERATURE = "LaundryCare.Washer.Option.Temperature";
    public static final String WASHER_TEMPERATURE_ENUM_KEY = "LaundryCare.Washer.EnumType.Temperature";
    public static final String WASHER_SPIN_SPEED = "LaundryCare.Washer.Option.SpinSpeed";
    public static final String WASHER_SPIN_SPEED_ENUM_KEY = "LaundryCare.Washer.EnumType.SpinSpeed";
    public static final String DRYER_DRYING_TARGET = "LaundryCare.Dryer.Option.DryingTarget";
    public static final String DRYER_DRYING_TARGET_ENUM_KEY = "LaundryCare.Dryer.EnumType.DryingTarget";
    public static final String OVEN_DURATION = "BSH.Common.Option.Duration";

    // Appliance Event Keys
    public static final String EVENT_POWER_STATE = POWER_STATE;
    public static final String EVENT_DOOR_STATE = "BSH.Common.Status.DoorState";
    public static final String EVENT_OPERATION_STATE = "BSH.Common.Status.OperationState";
    public static final String EVENT_REMOTE_CONTROL_START_ALLOWED = "BSH.Common.Status.RemoteControlStartAllowed";
    public static final String EVENT_REMOTE_CONTROL_ACTIVE = "BSH.Common.Status.RemoteControlActive";
    public static final String EVENT_LOCAL_CONTROL_ACTIVE = "BSH.Common.Status.LocalControlActive";
    public static final String EVENT_ACTIVE_PROGRAM = ACTIVE_PROGRAM;
    public static final String EVENT_SELECTED_PROGRAM = SELECTED_PROGRAM;
    public static final String EVENT_REMAINING_PROGRAM_TIME = "BSH.Common.Option.RemainingProgramTime";
    public static final String EVENT_PROGRAM_PROGRESS = "BSH.Common.Option.ProgramProgress";
    public static final String EVENT_WASHER_I_DOS_1_FILL_LEVEL_POOR = "LaundryCare.Washer.Event.IDos1FillLevelPoor";
    public static final String EVENT_WASHER_I_DOS_2_FILL_LEVEL_POOR = "LaundryCare.Washer.Event.IDos2FillLevelPoor";
    public static final String EVENT_WASHER_I_DOS_1_ACTIVE = WASHER_I_DOS_1_ACTIVE;
    public static final String EVENT_WASHER_I_DOS_2_ACTIVE = WASHER_I_DOS_2_ACTIVE;
    public static final String EVENT_WASHER_TEMPERATURE = WASHER_TEMPERATURE;
    public static final String EVENT_WASHER_SPIN_SPEED = WASHER_SPIN_SPEED;
    public static final String EVENT_DRYER_DRYING_TARGET = DRYER_DRYING_TARGET;
    public static final String EVENT_LAUNDRY_LOAD_INFORMATION = "LaundryCare.Common.Status.LoadInformation";
    public static final String EVENT_LAUNDRY_LOAD_RECOMMENDATION = "LaundryCare.Common.Option.LoadRecommendation";
    public static final String EVENT_LAUNDRY_PROCESS_PHASE = "LaundryCare.Common.Option.ProcessPhase";
    public static final String EVENT_LAUNDRY_DRUM_CLEAN_REMINDER = "LaundryCare.Washer.Event.DrumCleanReminder";
    public static final String EVENT_DISHWASHER_PROGRAM_PHASE = "Dishcare.Dishwasher.Status.ProgramPhase";
    public static final String EVENT_DISHWASHER_SALT_LACK = "Dishcare.Dishwasher.Event.SaltLack";
    public static final String EVENT_DISHWASHER_RINSE_AID_LACK = "Dishcare.Dishwasher.Event.RinseAidLack";
    public static final String EVENT_DISHWASHER_SALT_NEARLY_EMPTY = "Dishcare.Dishwasher.Event.SaltNearlyEmpty";
    public static final String EVENT_DISHWASHER_RINSE_AID_NEARLY_EMPTY = "Dishcare.Dishwasher.Event.RinseAidNearlyEmpty";
    public static final String EVENT_DISHWASHER_MACHINE_CARE_REMINDER = "Dishcare.Dishwasher.Event.MachineCareReminder";
    public static final String EVENT_COFFEE_MAKER_PROCESS_PHASE = "ConsumerProducts.CoffeeMaker.Status.ProcessPhase";
    public static final String EVENT_COFFEE_MAKER_COUNTDOWN_CLEANING = "ConsumerProducts.CoffeeMaker.Status.BeverageCountdownCleaning";
    public static final String EVENT_COFFEE_MAKER_COUNTDOWN_CALC_AND_CLEAN = "ConsumerProducts.CoffeeMaker.Status.BeverageCountdownCalcNClean";
    public static final String EVENT_COFFEE_MAKER_COUNTDOWN_DESCALING = "ConsumerProducts.CoffeeMaker.Status.BeverageCountdownDescaling";
    public static final String EVENT_COFFEE_MAKER_COUNTDOWN_WATER_FILTER = "ConsumerProducts.CoffeeMaker.Status.BeverageCountdownWaterfilter";
    public static final String EVENT_COFFEE_MAKER_WATER_TANK_EMPTY = "ConsumerProducts.CoffeeMaker.Event.WaterTankEmpty";
    public static final String EVENT_COFFEE_MAKER_WATER_TANK_NEARLY_EMPTY = "ConsumerProducts.CoffeeMaker.Event.WaterTankNearlyEmpty";
    public static final String EVENT_COFFEE_MAKER_DRIP_TRAY_FULL = "ConsumerProducts.CoffeeMaker.Event.DripTrayFull";
    public static final String EVENT_COFFEE_MAKER_EMPTY_MILK_TANK = "ConsumerProducts.CoffeeMaker.Event.EmptyMilkTank";
    public static final String EVENT_COFFEE_MAKER_BEAN_CONTAINER_EMPTY = "ConsumerProducts.CoffeeMaker.Event.BeanContainerEmpty";
    public static final String EVENT_OVEN_DURATION = OVEN_DURATION;

    // Channels
    public static final String CHANNEL_POWER_STATE = "power_state";
    public static final String CHANNEL_DOOR_STATE = "door_state";
    public static final String CHANNEL_OPERATION_STATE = "operation_state";
    public static final String CHANNEL_REMOTE_START_ALLOWANCE = "remote_start_allowance";
    public static final String CHANNEL_REMOTE_CONTROL_ACTIVE = "remote_control_active";
    public static final String CHANNEL_LOCAL_CONTROL_ACTIVE = "local_control_active";
    public static final String CHANNEL_ACTIVE_PROGRAM = "active_program";
    public static final String CHANNEL_SELECTED_PROGRAM = "selected_program";
    public static final String CHANNEL_REMAINING_PROGRAM_TIME = "remaining_program_time";
    public static final String CHANNEL_PROGRAM_PROGRESS = "program_progress";
    public static final String CHANNEL_PROGRAM_COMMAND = "program_command";
    public static final String CHANNEL_I_DOS_1_FILL_LEVEL_POOR = "idos1_fill_level_poor";
    public static final String CHANNEL_I_DOS_2_FILL_LEVEL_POOR = "idos2_fill_level_poor";
    public static final String CHANNEL_I_DOS_1_ACTIVE = "idos1_active";
    public static final String CHANNEL_I_DOS_2_ACTIVE = "idos2_active";
    public static final String CHANNEL_WASHER_TEMPERATURE = "washer_temperature";
    public static final String CHANNEL_WASHER_SPIN_SPEED = "washer_spin_speed";
    public static final String CHANNEL_DRYER_DRYING_TARGET = "drying_target";
    public static final String CHANNEL_LAUNDRY_LOAD_INFORMATION = "laundry_load_information";
    public static final String CHANNEL_LAUNDRY_LOAD_RECOMMENDATION = "laundry_load_recommendation";
    public static final String CHANNEL_LAUNDRY_PROCESS_PHASE = "laundry_process_phase";
    public static final String CHANNEL_LAUNDRY_DRUM_CLEAN_REMINDER = "drum_clean_reminder";
    public static final String CHANNEL_PROGRAM_PHASE = "program_phase";
    public static final String CHANNEL_DISHWASHER_SALT_LACK = "salt_lack";
    public static final String CHANNEL_DISHWASHER_RINSE_AID_LACK = "rinse_aid_lack";
    public static final String CHANNEL_DISHWASHER_SALT_NEARLY_EMPTY = "salt_nearly_empty";
    public static final String CHANNEL_DISHWASHER_RINSE_AID_NEARLY_EMPTY = "rinse_aid_nearly_empty";
    public static final String CHANNEL_DISHWASHER_MACHINE_CARE_REMINDER = "machine_care_reminder";
    public static final String CHANNEL_PROCESS_PHASE = "process_phase";
    public static final String CHANNEL_COFFEE_MAKER_COUNTDOWN_CLEANING = "countdown_cleaning";
    public static final String CHANNEL_COFFEE_MAKER_COUNTDOWN_CALC_AND_CLEAN = "countdown_calc_and_clean";
    public static final String CHANNEL_COFFEE_MAKER_COUNTDOWN_DESCALING = "countdown_descaling";
    public static final String CHANNEL_COFFEE_MAKER_COUNTDOWN_WATER_FILTER = "countdown_water_filter";
    public static final String CHANNEL_COFFEE_MAKER_WATER_TANK_EMPTY = "water_tank_empty";
    public static final String CHANNEL_COFFEE_MAKER_WATER_TANK_NEARLY_EMPTY = "water_tank_nearly_empty";
    public static final String CHANNEL_COFFEE_MAKER_DRIP_TRAY_FULL = "drip_tray_full";
    public static final String CHANNEL_COFFEE_MAKER_EMPTY_MILK_TANK = "empty_milk_tank";
    public static final String CHANNEL_COFFEE_MAKER_BEAN_CONTAINER_EMPTY = "bean_container_empty";
    public static final String CHANNEL_OVEN_DURATION = "oven_duration";

    // State values
    public static final String STATE_OPEN = "Open";
    public static final String STATE_ON = "On";
    public static final String STATE_OFF = "Off";
    public static final String STATE_STANDBY = "Standby";
    public static final String STATE_NO_PROGRAM = "0";
    public static final String STATE_PRESENT = "Present";

    // Commands
    public static final String COMMAND_START = "start";
    public static final String COMMAND_PAUSE = "pause";
    public static final String COMMAND_RESUME = "resume";
    public static final String COMMAND_STOP = "stop";
}
