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
package org.openhab.binding.homeconnectdirect.internal.handler;

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FREEZER_DOOR_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FREEZER_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FREEZER_SUPER_MODE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FRIDGE_CHILLER_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FRIDGE_DOOR_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FRIDGE_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FRIDGE_SUPER_MODE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_FREEZER_DOOR_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_FREEZER_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_FREEZER_SUPER_MODE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_FRIDGE_CHILLER_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_FRIDGE_DOOR_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_FRIDGE_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_FRIDGE_SUPER_MODE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FREEZER_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FREEZER_SUPER_MODE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FRIDGE_CHILLER_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FRIDGE_SET_POINT_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.FRIDGE_SUPER_MODE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_OPEN;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_VALUES;
import static org.openhab.core.library.unit.SIUnits.CELSIUS;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.homeconnectdirect.internal.handler.model.Event;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Data;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.SIUnits;
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

    private final Logger logger;

    public HomeConnectDirectFridgeFreezerHandler(Thing thing, ApplianceProfileService applianceProfileService,
            HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider, String deviceId) {
        super(thing, applianceProfileService, descriptionProvider, deviceId);

        this.logger = LoggerFactory.getLogger(HomeConnectDirectFridgeFreezerHandler.class);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        super.handleCommand(channelUID, command);

        if (CHANNEL_FRIDGE_SUPER_MODE.equals(channelUID.getId()) && command instanceof OnOffType) {
            mapFeatureName(FRIDGE_SUPER_MODE).ifPresent(settingUid -> send(Action.POST, RO_VALUES,
                    List.of(new Data(settingUid, OnOffType.ON.equals(command))), null, 1));
        } else if (CHANNEL_FREEZER_SUPER_MODE.equals(channelUID.getId()) && command instanceof OnOffType) {
            mapFeatureName(FREEZER_SUPER_MODE).ifPresent(settingUid -> send(Action.POST, RO_VALUES,
                    List.of(new Data(settingUid, OnOffType.ON.equals(command))), null, 1));
        } else if (CHANNEL_FRIDGE_SET_POINT_TEMPERATURE.equals(channelUID.getId())
                && command instanceof QuantityType quantity) {
            var setpointTemperature = getTargetTemperature(quantity);
            if (setpointTemperature != null) {
                mapFeatureName(FRIDGE_SET_POINT_TEMPERATURE).ifPresent(settingUid -> send(Action.POST, RO_VALUES,
                        List.of(new Data(settingUid, setpointTemperature)), null, 1));
            }
        } else if (CHANNEL_FREEZER_SET_POINT_TEMPERATURE.equals(channelUID.getId())
                && command instanceof QuantityType quantity) {
            var setpointTemperature = getTargetTemperature(quantity);
            if (setpointTemperature != null) {
                mapFeatureName(FREEZER_SET_POINT_TEMPERATURE).ifPresent(settingUid -> send(Action.POST, RO_VALUES,
                        List.of(new Data(settingUid, setpointTemperature)), null, 1));
            }
        } else if (CHANNEL_FRIDGE_CHILLER_SET_POINT_TEMPERATURE.equals(channelUID.getId())
                && command instanceof QuantityType quantity) {
            var setpointTemperature = getTargetTemperature(quantity);
            if (setpointTemperature != null) {
                mapFeatureName(FRIDGE_CHILLER_SET_POINT_TEMPERATURE).ifPresent(settingUid -> send(Action.POST,
                        RO_VALUES, List.of(new Data(settingUid, setpointTemperature)), null, 1));
            }
        }
    }

    @Override
    protected void onApplianceEvent(Event event) {
        super.onApplianceEvent(event);

        switch (event.name()) {
            case EVENT_FRIDGE_SET_POINT_TEMPERATURE -> getLinkedChannel(CHANNEL_FRIDGE_SET_POINT_TEMPERATURE).ifPresent(
                    channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), CELSIUS)));
            case EVENT_FREEZER_SET_POINT_TEMPERATURE ->
                getLinkedChannel(CHANNEL_FREEZER_SET_POINT_TEMPERATURE).ifPresent(
                        channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), CELSIUS)));
            case EVENT_FRIDGE_CHILLER_SET_POINT_TEMPERATURE ->
                getLinkedChannel(CHANNEL_FRIDGE_CHILLER_SET_POINT_TEMPERATURE).ifPresent(
                        channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), CELSIUS)));
            case EVENT_FRIDGE_SUPER_MODE -> getLinkedChannel(CHANNEL_FRIDGE_SUPER_MODE)
                    .ifPresent(channel -> updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean())));
            case EVENT_FREEZER_SUPER_MODE -> getLinkedChannel(CHANNEL_FREEZER_SUPER_MODE)
                    .ifPresent(channel -> updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean())));
            case EVENT_FRIDGE_DOOR_STATE ->
                getLinkedChannel(CHANNEL_FRIDGE_DOOR_STATE).ifPresent(channel -> updateState(channel.getUID(),
                        STATE_OPEN.equals(event.value()) ? OpenClosedType.OPEN : OpenClosedType.CLOSED));
            case EVENT_FREEZER_DOOR_STATE ->
                getLinkedChannel(CHANNEL_FREEZER_DOOR_STATE).ifPresent(channel -> updateState(channel.getUID(),
                        STATE_OPEN.equals(event.value()) ? OpenClosedType.OPEN : OpenClosedType.CLOSED));
        }
    }

    @Nullable
    private Integer getTargetTemperature(QuantityType<?> quantityCommand) {
        Integer value;
        if (quantityCommand.getUnit().equals(SIUnits.CELSIUS)) {
            value = quantityCommand.intValue();
        } else {
            logger.debug("Converting target temperature from {}{} to °C value.", quantityCommand.intValue(),
                    quantityCommand.getUnit());
            var celsius = quantityCommand.toUnit(SIUnits.CELSIUS);
            if (celsius == null) {
                logger.warn("Converting temperature to celsius failed! quantity={}", quantityCommand);
                value = null;
            } else {
                value = celsius.intValue();
            }
            logger.debug("Converted value {}°C", value);
        }

        return value;
    }
}
