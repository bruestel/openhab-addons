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
package org.openhab.binding.homeconnectdirect.internal.handler;

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_DRYER_DRYING_TARGET;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_I_DOS_1_ACTIVE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_I_DOS_1_FILL_LEVEL_POOR;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_I_DOS_2_ACTIVE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_I_DOS_2_FILL_LEVEL_POOR;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_LAUNDRY_DRUM_CLEAN_REMINDER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_LAUNDRY_LOAD_INFORMATION;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_LAUNDRY_LOAD_RECOMMENDATION;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_LAUNDRY_PROCESS_PHASE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_WASHER_SPIN_SPEED;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_WASHER_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.DRYER_DRYING_TARGET;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.DRYER_DRYING_TARGET_ENUM_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_DRYER_DRYING_TARGET;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_LAUNDRY_DRUM_CLEAN_REMINDER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_LAUNDRY_LOAD_INFORMATION;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_LAUNDRY_LOAD_RECOMMENDATION;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_LAUNDRY_PROCESS_PHASE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_WASHER_I_DOS_1_ACTIVE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_WASHER_I_DOS_1_FILL_LEVEL_POOR;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_WASHER_I_DOS_2_ACTIVE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_WASHER_I_DOS_2_FILL_LEVEL_POOR;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_WASHER_SPIN_SPEED;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_WASHER_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_PRESENT;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.WASHER_I_DOS_1_ACTIVE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.WASHER_I_DOS_2_ACTIVE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.WASHER_SPIN_SPEED;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.WASHER_SPIN_SPEED_ENUM_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.WASHER_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.WASHER_TEMPERATURE_ENUM_KEY;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_VALUES;
import static org.openhab.core.library.unit.SIUnits.GRAM;

import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.homeconnectdirect.internal.handler.model.DescriptionChangeEvent;
import org.openhab.binding.homeconnectdirect.internal.handler.model.Event;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Data;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;

/**
 * The {@link HomeConnectDirectWasherDryerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class HomeConnectDirectWasherDryerHandler extends BaseHomeConnectDirectHandler {

    public HomeConnectDirectWasherDryerHandler(Thing thing, ApplianceProfileService applianceProfileService,
            HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider, String deviceId) {
        super(thing, applianceProfileService, descriptionProvider, deviceId);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        super.handleCommand(channelUID, command);

        if (CHANNEL_I_DOS_1_ACTIVE.equals(channelUID.getId()) && command instanceof OnOffType) {
            mapFeatureName(WASHER_I_DOS_1_ACTIVE).ifPresent(optionUid -> send(Action.POST, RO_VALUES,
                    List.of(new Data(optionUid, OnOffType.ON.equals(command))), null, 1));
        } else if (CHANNEL_I_DOS_2_ACTIVE.equals(channelUID.getId()) && command instanceof OnOffType) {
            mapFeatureName(WASHER_I_DOS_2_ACTIVE).ifPresent(optionUid -> send(Action.POST, RO_VALUES,
                    List.of(new Data(optionUid, OnOffType.ON.equals(command))), null, 1));
        } else if (CHANNEL_WASHER_TEMPERATURE.equals(channelUID.getId()) && command instanceof StringType) {
            mapFeatureName(WASHER_TEMPERATURE).ifPresent(
                    optionUid -> mapEnumerationValueString(WASHER_TEMPERATURE_ENUM_KEY, command.toFullString())
                            .ifPresent(enumValue -> send(Action.POST, RO_VALUES,
                                    List.of(new Data(optionUid, enumValue)), null, 1)));
        } else if (CHANNEL_WASHER_SPIN_SPEED.equals(channelUID.getId()) && command instanceof StringType) {
            mapFeatureName(WASHER_SPIN_SPEED).ifPresent(
                    optionUid -> mapEnumerationValueString(WASHER_SPIN_SPEED_ENUM_KEY, command.toFullString())
                            .ifPresent(enumValue -> send(Action.POST, RO_VALUES,
                                    List.of(new Data(optionUid, enumValue)), null, 1)));
        } else if (CHANNEL_DRYER_DRYING_TARGET.equals(channelUID.getId()) && command instanceof StringType) {
            mapFeatureName(DRYER_DRYING_TARGET).ifPresent(
                    optionUid -> mapEnumerationValueString(DRYER_DRYING_TARGET_ENUM_KEY, command.toFullString())
                            .ifPresent(enumValue -> send(Action.POST, RO_VALUES,
                                    List.of(new Data(optionUid, enumValue)), null, 1)));
        }
    }

    @Override
    protected void onApplianceDescriptionEvent(final DescriptionChangeEvent event) {
        super.onApplianceDescriptionEvent(event);
        if (event.enumType() != null) {
            switch (event.name()) {
                case WASHER_SPIN_SPEED -> getLinkedChannel(CHANNEL_WASHER_SPIN_SPEED).ifPresent(channel -> {
                    var spinSpeedStateOptions = getEnumerationValues(event.enumType()).stream()
                            .filter(integer -> integer >= event.min() && integer <= event.max())
                            .map(integer -> mapEnumerationValue(WASHER_SPIN_SPEED_ENUM_KEY, integer))
                            .filter(Optional::isPresent).map(Optional::get)
                            .map(value -> new StateOption(value, createWasherSpinSpeedLabel(value))).toList();

                    descriptionProvider.setStateOptions(channel.getUID(), spinSpeedStateOptions);
                });
                case WASHER_TEMPERATURE -> getLinkedChannel(CHANNEL_WASHER_TEMPERATURE).ifPresent(channel -> {
                    var temperatureStateOptions = getEnumerationValues(event.enumType()).stream()
                            .filter(integer -> integer >= event.min() && integer <= event.max())
                            .map(integer -> mapEnumerationValue(WASHER_TEMPERATURE_ENUM_KEY, integer))
                            .filter(Optional::isPresent).map(Optional::get)
                            .map(value -> new StateOption(value, createWasherTemperatureLabel(value))).toList();

                    descriptionProvider.setStateOptions(channel.getUID(), temperatureStateOptions);
                });
                case DRYER_DRYING_TARGET -> getLinkedChannel(CHANNEL_DRYER_DRYING_TARGET).ifPresent(channel -> {
                    var dryingTargetStateOptions = getEnumerationValues(event.enumType()).stream()
                            .filter(integer -> integer >= event.min() && integer <= event.max())
                            .map(integer -> mapEnumerationValue(DRYER_DRYING_TARGET_ENUM_KEY, integer))
                            .filter(Optional::isPresent).map(Optional::get)
                            .map(value -> new StateOption(value, mapStringType(value))).toList();

                    descriptionProvider.setStateOptions(channel.getUID(), dryingTargetStateOptions);
                });
            }
        }
    }

    @Override
    protected void onApplianceEvent(Event event) {
        super.onApplianceEvent(event);

        switch (event.name()) {
            case EVENT_WASHER_I_DOS_1_FILL_LEVEL_POOR -> getLinkedChannel(CHANNEL_I_DOS_1_FILL_LEVEL_POOR).ifPresent(
                    channel -> updateState(channel.getUID(), OnOffType.from(STATE_PRESENT.equals(event.value()))));
            case EVENT_WASHER_I_DOS_2_FILL_LEVEL_POOR -> getLinkedChannel(CHANNEL_I_DOS_2_FILL_LEVEL_POOR).ifPresent(
                    channel -> updateState(channel.getUID(), OnOffType.from(STATE_PRESENT.equals(event.value()))));
            case EVENT_WASHER_I_DOS_1_ACTIVE -> getLinkedChannel(CHANNEL_I_DOS_1_ACTIVE)
                    .ifPresent(channel -> updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean())));
            case EVENT_WASHER_I_DOS_2_ACTIVE -> getLinkedChannel(CHANNEL_I_DOS_2_ACTIVE)
                    .ifPresent(channel -> updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean())));
            case EVENT_WASHER_TEMPERATURE ->
                getLinkedChannel(CHANNEL_WASHER_TEMPERATURE).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null ? UnDefType.UNDEF : new StringType(event.getValueAsString())));
            case EVENT_WASHER_SPIN_SPEED ->
                getLinkedChannel(CHANNEL_WASHER_SPIN_SPEED).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null ? UnDefType.UNDEF : new StringType(event.getValueAsString())));
            case EVENT_LAUNDRY_LOAD_INFORMATION -> getLinkedChannel(CHANNEL_LAUNDRY_LOAD_INFORMATION).ifPresent(
                    channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), GRAM)));
            case EVENT_LAUNDRY_LOAD_RECOMMENDATION -> getLinkedChannel(CHANNEL_LAUNDRY_LOAD_RECOMMENDATION).ifPresent(
                    channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), GRAM)));
            case EVENT_LAUNDRY_PROCESS_PHASE ->
                getLinkedChannel(CHANNEL_LAUNDRY_PROCESS_PHASE).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null ? UnDefType.UNDEF : new StringType(event.getValueAsString())));
            case EVENT_LAUNDRY_DRUM_CLEAN_REMINDER ->
                getLinkedChannel(CHANNEL_LAUNDRY_DRUM_CLEAN_REMINDER).ifPresent(channel -> updateState(channel.getUID(),
                        OnOffType.from(STATE_PRESENT.equalsIgnoreCase(event.getValueAsString()))));
            case EVENT_DRYER_DRYING_TARGET ->
                getLinkedChannel(CHANNEL_DRYER_DRYING_TARGET).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null ? UnDefType.UNDEF : new StringType(event.getValueAsString())));
        }
    }

    private String createWasherTemperatureLabel(String value) {
        if (value.startsWith("GC")) {
            return value.replace("GC", "") + " °C";
        }

        if (value.startsWith("Ul")) {
            return mapStringType(value.replace("Ul", ""));
        }

        return mapStringType(value);
    }

    private String createWasherSpinSpeedLabel(String value) {
        if (value.startsWith("RPM")) {
            return value.replace("RPM", "") + " RPM";
        }

        if (value.startsWith("Ul")) {
            return value.replace("Ul", "");
        }

        return mapStringType(value);
    }
}
