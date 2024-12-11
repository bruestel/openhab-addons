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
package org.openhab.binding.homeconnectdirect.internal.handler;

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_DOOR;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FREEZER_DOOR_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FREEZER_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FREEZER_SUPER_MODE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FRIDGE_CHILLER_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FRIDGE_DOOR_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FRIDGE_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FRIDGE_SUPER_MODE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_TYPE_DOOR;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_TYPE_FRIDGE_FREEZER_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_TYPE_FRIDGE_FREEZER_SUPER_MODE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FREEZER_DOOR_STATE_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FREEZER_SET_POINT_TEMPERATURE_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FREEZER_SUPER_MODE_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FRIDGE_CHILLER_SET_POINT_TEMPERATURE_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FRIDGE_DOOR_STATE_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FRIDGE_SET_POINT_TEMPERATURE_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FRIDGE_SUPER_MODE_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.I18N_DOOR;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.I18N_FRIDGE_FREEZER_DOOR;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.I18N_FRIDGE_FREEZER_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.I18N_FRIDGE_FREEZER_SUPER_MODE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.NUMBER_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_OPEN;
import static org.openhab.core.library.unit.ImperialUnits.FAHRENHEIT;
import static org.openhab.core.library.unit.SIUnits.CELSIUS;

import java.util.Objects;
import java.util.Set;

import javax.measure.Unit;
import javax.measure.quantity.Temperature;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.homeconnectdirect.internal.configuration.HomeConnectDirectConfiguration;
import org.openhab.binding.homeconnectdirect.internal.handler.model.Value;
import org.openhab.binding.homeconnectdirect.internal.i18n.HomeConnectDirectTranslationProvider;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicCommandDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.description.model.ContentType;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.core.library.CoreItemFactory;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HomeConnectDirectFridgeFreezerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class HomeConnectDirectFridgeFreezerHandler extends BaseHomeConnectDirectHandler {

    private static final String FREEZER = "Freezer";
    private static final String FRIDGE = "Fridge";
    private static final String CHILLER = "Chiller";

    private final Logger logger;

    public HomeConnectDirectFridgeFreezerHandler(Thing thing, ApplianceProfileService applianceProfileService,
            HomeConnectDirectDynamicCommandDescriptionProvider commandDescriptionProvider,
            HomeConnectDirectDynamicStateDescriptionProvider stateDescriptionProvider, String deviceId,
            HomeConnectDirectConfiguration configuration, HomeConnectDirectTranslationProvider translationProvider) {
        super(thing, applianceProfileService, commandDescriptionProvider, stateDescriptionProvider, deviceId,
                configuration, translationProvider);

        this.logger = LoggerFactory.getLogger(HomeConnectDirectFridgeFreezerHandler.class);
    }

    @Override
    protected void initializeStarted() {
        addDynamicChannels();
    }

    @Override
    protected void initializeFinished() {
        initializeAllStates();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        super.handleCommand(channelUID, command);

        if (CHANNEL_FRIDGE_SUPER_MODE.equals(channelUID.getId()) && command instanceof OnOffType) {
            sendBooleanSettingIfAllowed(command, FRIDGE_SUPER_MODE_KEY);
        } else if (CHANNEL_FREEZER_SUPER_MODE.equals(channelUID.getId()) && command instanceof OnOffType) {
            sendBooleanSettingIfAllowed(command, FREEZER_SUPER_MODE_KEY);
        } else if (CHANNEL_FRIDGE_SET_POINT_TEMPERATURE.equals(channelUID.getId())
                && command instanceof QuantityType<?> quantity) {
            sendTemperatureSetting(FRIDGE_SET_POINT_TEMPERATURE_KEY, quantity);
        } else if (CHANNEL_FREEZER_SET_POINT_TEMPERATURE.equals(channelUID.getId())
                && command instanceof QuantityType<?> quantity) {
            sendTemperatureSetting(FREEZER_SET_POINT_TEMPERATURE_KEY, quantity);
        } else if (CHANNEL_FRIDGE_CHILLER_SET_POINT_TEMPERATURE.equals(channelUID.getId())
                && command instanceof QuantityType<?> quantity) {
            sendTemperatureSetting(FRIDGE_CHILLER_SET_POINT_TEMPERATURE_KEY, quantity);
        }
    }

    @Override
    protected void onApplianceValueEvent(Value value) {
        super.onApplianceValueEvent(value);

        switch (value.key()) {
            case FRIDGE_SET_POINT_TEMPERATURE_KEY -> updateStateIfLinked(CHANNEL_FRIDGE_SET_POINT_TEMPERATURE,
                    () -> new QuantityType<>(value.getValueAsInt(), getTemperatureUnitOfSetting(value.key())));
            case FREEZER_SET_POINT_TEMPERATURE_KEY -> updateStateIfLinked(CHANNEL_FREEZER_SET_POINT_TEMPERATURE,
                    () -> new QuantityType<>(value.getValueAsInt(), getTemperatureUnitOfSetting(value.key())));
            case FRIDGE_CHILLER_SET_POINT_TEMPERATURE_KEY ->
                updateStateIfLinked(CHANNEL_FRIDGE_CHILLER_SET_POINT_TEMPERATURE,
                        () -> new QuantityType<>(value.getValueAsInt(), getTemperatureUnitOfSetting(value.key())));
            case FRIDGE_SUPER_MODE_KEY ->
                updateStateIfLinked(CHANNEL_FRIDGE_SUPER_MODE, OnOffType.from(value.getValueAsBoolean()));
            case FREEZER_SUPER_MODE_KEY ->
                updateStateIfLinked(CHANNEL_FREEZER_SUPER_MODE, OnOffType.from(value.getValueAsBoolean()));
            case FRIDGE_DOOR_STATE_KEY -> updateStateIfLinked(CHANNEL_FRIDGE_DOOR_STATE,
                    () -> STATE_OPEN.equals(value.value()) ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
            case FREEZER_DOOR_STATE_KEY -> updateStateIfLinked(CHANNEL_FREEZER_DOOR_STATE,
                    () -> STATE_OPEN.equals(value.value()) ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
        }
    }

    @Override
    public void channelLinked(ChannelUID channelUID) {
        super.channelLinked(channelUID);
        initializeState(channelUID.getId());
    }

    private void initializeAllStates() {
        Set.of(CHANNEL_FRIDGE_SUPER_MODE, CHANNEL_FREEZER_SUPER_MODE, CHANNEL_FRIDGE_DOOR_STATE,
                CHANNEL_FREEZER_DOOR_STATE).forEach(this::initializeState);
    }

    private void initializeState(String channelId) {
        switch (channelId) {
            case CHANNEL_FRIDGE_SUPER_MODE, CHANNEL_FREEZER_SUPER_MODE -> updateState(channelId, OnOffType.OFF);
            case CHANNEL_FRIDGE_DOOR_STATE, CHANNEL_FREEZER_DOOR_STATE -> updateState(channelId, OpenClosedType.CLOSED);
        }
    }

    private void addDynamicChannels() {
        getDeviceDescriptionServiceOptional().ifPresent(deviceDescriptionService -> {
            var thingBuilder = editThing();
            boolean channelsChanged = false;

            // setpoint temperatures
            var refrigeratorSetpointSetting = deviceDescriptionService.getSetting(FRIDGE_SET_POINT_TEMPERATURE_KEY,
                    false, false, false);
            if (refrigeratorSetpointSetting != null) {
                channelsChanged |= addChannelIfNotExist(thingBuilder, CHANNEL_FRIDGE_SET_POINT_TEMPERATURE,
                        CHANNEL_TYPE_FRIDGE_FREEZER_SET_POINT_TEMPERATURE, NUMBER_TEMPERATURE,
                        getTranslationProvider().getText(I18N_FRIDGE_FREEZER_SET_POINT_TEMPERATURE,
                                getTranslationProvider().getText(FRIDGE)));
            }

            var freezerSetpointSetting = deviceDescriptionService.getSetting(FREEZER_SET_POINT_TEMPERATURE_KEY, false,
                    false, false);
            if (freezerSetpointSetting != null) {
                channelsChanged |= addChannelIfNotExist(thingBuilder, CHANNEL_FREEZER_SET_POINT_TEMPERATURE,
                        CHANNEL_TYPE_FRIDGE_FREEZER_SET_POINT_TEMPERATURE, NUMBER_TEMPERATURE,
                        getTranslationProvider().getText(I18N_FRIDGE_FREEZER_SET_POINT_TEMPERATURE,
                                getTranslationProvider().getText(FREEZER)));
            }

            var chillerSetpointSetting = deviceDescriptionService.getSetting(FRIDGE_CHILLER_SET_POINT_TEMPERATURE_KEY,
                    false, false, false);
            if (chillerSetpointSetting != null) {
                channelsChanged |= addChannelIfNotExist(thingBuilder, CHANNEL_FRIDGE_CHILLER_SET_POINT_TEMPERATURE,
                        CHANNEL_TYPE_FRIDGE_FREEZER_SET_POINT_TEMPERATURE, NUMBER_TEMPERATURE,
                        getTranslationProvider().getText(I18N_FRIDGE_FREEZER_SET_POINT_TEMPERATURE,
                                getTranslationProvider().getText(CHILLER)));
            }

            // door states
            var doorAdded = false;
            var fridgeDoorStatus = deviceDescriptionService.getStatus(FRIDGE_DOOR_STATE_KEY, false, false, false);
            if (fridgeDoorStatus != null) {
                channelsChanged |= addChannelIfNotExist(thingBuilder, CHANNEL_FRIDGE_DOOR_STATE, CHANNEL_TYPE_DOOR,
                        CoreItemFactory.CONTACT, getTranslationProvider().getText(I18N_FRIDGE_FREEZER_DOOR,
                                getTranslationProvider().getText(FRIDGE)));
                doorAdded = true;
            }
            var freezerDoorStatus = deviceDescriptionService.getStatus(FREEZER_DOOR_STATE_KEY, false, false, false);
            if (freezerDoorStatus != null) {
                channelsChanged |= addChannelIfNotExist(thingBuilder, CHANNEL_FREEZER_DOOR_STATE, CHANNEL_TYPE_DOOR,
                        CoreItemFactory.CONTACT, getTranslationProvider().getText(I18N_FRIDGE_FREEZER_DOOR,
                                getTranslationProvider().getText(FREEZER)));
                doorAdded = true;
            }

            if (!doorAdded) {
                channelsChanged |= addChannelIfNotExist(thingBuilder, CHANNEL_DOOR, CHANNEL_TYPE_DOOR,
                        CoreItemFactory.CONTACT, getTranslationProvider().getText(I18N_DOOR));
            }

            // super modes
            var fridgeSuperModeSetting = deviceDescriptionService.getSetting(FRIDGE_SUPER_MODE_KEY, false, false,
                    false);
            if (fridgeSuperModeSetting != null) {
                channelsChanged |= addChannelIfNotExist(thingBuilder, CHANNEL_FRIDGE_SUPER_MODE,
                        CHANNEL_TYPE_FRIDGE_FREEZER_SUPER_MODE, CoreItemFactory.SWITCH, getTranslationProvider()
                                .getText(I18N_FRIDGE_FREEZER_SUPER_MODE, getTranslationProvider().getText(FRIDGE)));
            }

            var freezerSuperModeSetting = deviceDescriptionService.getSetting(FREEZER_SUPER_MODE_KEY, false, false,
                    false);
            if (freezerSuperModeSetting != null) {
                channelsChanged |= addChannelIfNotExist(thingBuilder, CHANNEL_FREEZER_SUPER_MODE,
                        CHANNEL_TYPE_FRIDGE_FREEZER_SUPER_MODE, CoreItemFactory.SWITCH, getTranslationProvider()
                                .getText(I18N_FRIDGE_FREEZER_SUPER_MODE, getTranslationProvider().getText(FREEZER)));
            }

            // update channels
            if (channelsChanged) {
                updateThing(thingBuilder.build());
            }
        });
    }

    private Unit<Temperature> getTemperatureUnitOfSetting(String settingKey) {
        var unit = getDeviceDescriptionServiceOptional().map(deviceDescriptionService -> {
            var setting = deviceDescriptionService.findSettingByKey(settingKey);
            if (setting != null) {
                return ContentType.TEMPERATURE_CELSIUS.equals(setting.contentType()) ? CELSIUS : FAHRENHEIT;
            }
            return CELSIUS;
        }).orElse(null);
        return Objects.requireNonNullElse(unit, CELSIUS);
    }

    private void sendTemperatureSetting(String settingKey, QuantityType<?> quantity) {
        var unit = getTemperatureUnitOfSetting(settingKey);
        var temperatureQuantityType = quantity.toUnit(unit);
        if (temperatureQuantityType != null) {
            sendIntegerSettingIfAllowed(temperatureQuantityType, settingKey);
        } else {
            logger.warn("Could not set temperature! uid={}", getThing().getUID());
        }
    }
}
