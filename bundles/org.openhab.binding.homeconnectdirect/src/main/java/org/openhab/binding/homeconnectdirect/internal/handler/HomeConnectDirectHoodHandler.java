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

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_BUTTON_TONES;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FUNCTIONAL_LIGHT;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_FUNCTIONAL_LIGHT_BRIGHTNESS;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_HOOD_INTENSIVE_LEVEL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_HOOD_VENTING_LEVEL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_SELECTED_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.COOKING_BUTTON_TONES;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.COOKING_LIGHTING;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.COOKING_LIGHTING_BRIGHTNESS;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_ACTIVE_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COOKING_BUTTON_TONES;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COOKING_LIGHTING;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COOKING_LIGHTING_BRIGHTNESS;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_HOOD_INTENSIVE_LEVEL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_HOOD_VENTING_LEVEL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_POWER_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HOOD_INTENSIVE_LEVEL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HOOD_INTENSIVE_LEVEL_ENUM_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HOOD_VENTING_LEVEL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.HOOD_VENTING_LEVEL_ENUM_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_FAN_OFF;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_FAN_STAGE_1;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_FAN_STAGE_2;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_FAN_STAGE_3;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_FAN_STAGE_4;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_FAN_STAGE_5;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_HOOD_VENTING;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_INTENSIVE_STAGE_1;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_INTENSIVE_STAGE_2;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_INTENSIVE_STAGE_OFF;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_NO_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_OFF;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_ACTIVE_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_VALUES;
import static org.openhab.core.library.unit.Units.PERCENT;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.homeconnectdirect.internal.handler.model.DescriptionChangeEvent;
import org.openhab.binding.homeconnectdirect.internal.handler.model.Event;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Data;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.ProgramData;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HomeConnectDirectHoodHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class HomeConnectDirectHoodHandler extends BaseHomeConnectDirectHandler {

    private final Logger logger;

    public HomeConnectDirectHoodHandler(Thing thing, ApplianceProfileService applianceProfileService,
            HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider, String deviceId) {
        super(thing, applianceProfileService, descriptionProvider, deviceId);

        this.logger = LoggerFactory.getLogger(HomeConnectDirectHoodHandler.class);
    }

    @Override
    public void onWebSocketConnect() {
        super.onWebSocketConnect();
        updateLevelStateDescriptions();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (!CHANNEL_SELECTED_PROGRAM.equals(channelUID.getId())) {
            super.handleCommand(channelUID, command);
        }

        if (CHANNEL_FUNCTIONAL_LIGHT.equals(channelUID.getId()) && command instanceof OnOffType) {
            mapFeatureName(COOKING_LIGHTING).ifPresent(optionUid -> send(Action.POST, RO_VALUES,
                    List.of(new Data(optionUid, OnOffType.ON.equals(command))), null, 1));
        } else if (CHANNEL_FUNCTIONAL_LIGHT_BRIGHTNESS.equals(channelUID.getId())
                && command instanceof Number quantity) {
            mapFeatureName(COOKING_LIGHTING_BRIGHTNESS).ifPresent(optionUid -> {
                double value = quantity.doubleValue();

                // percent value
                if (value > 0 && value < 1) {
                    value *= 100;
                }
                if (value > 100) {
                    value = 100;
                }
                send(Action.POST, RO_VALUES, List.of(new Data(optionUid, (int) value)), null, 1);
            });
        } else if (CHANNEL_BUTTON_TONES.equals(channelUID.getId()) && command instanceof OnOffType) {
            mapFeatureName(COOKING_BUTTON_TONES).ifPresent(optionUid -> send(Action.POST, RO_VALUES,
                    List.of(new Data(optionUid, OnOffType.ON.equals(command))), null, 1));
        } else if (CHANNEL_HOOD_VENTING_LEVEL.equals(channelUID.getId()) && command instanceof StringType) {
            var programUid = getSelectedProgramUid(STATE_HOOD_VENTING);
            var optionUid = mapFeatureName(HOOD_VENTING_LEVEL);
            var optionEnumValue = mapEnumerationValueString(HOOD_VENTING_LEVEL_ENUM_KEY, command.toFullString());

            logger.debug("Setting up venting level state. programUid={} optionUid={}, optionEnumValue={}", programUid,
                    optionUid, optionEnumValue);
            if (Stream.of(programUid, optionUid, optionEnumValue).allMatch(Optional::isPresent)) {
                send(Action.POST, RO_ACTIVE_PROGRAM, List.of(
                        new ProgramData(programUid.get(), List.of(new Data(optionUid.get(), optionEnumValue.get())))),
                        null, 1);
            }
        } else if (CHANNEL_HOOD_INTENSIVE_LEVEL.equals(channelUID.getId()) && command instanceof StringType) {
            var programUid = getSelectedProgramUid(STATE_HOOD_VENTING);
            var optionUid = mapFeatureName(HOOD_INTENSIVE_LEVEL);
            var optionEnumValue = mapEnumerationValueString(HOOD_INTENSIVE_LEVEL_ENUM_KEY, command.toFullString());

            logger.debug("Setting up intensive level state. programUid={} optionUid={}, optionEnumValue={}", programUid,
                    optionUid, optionEnumValue);
            if (Stream.of(programUid, optionUid, optionEnumValue).allMatch(Optional::isPresent)) {
                send(Action.POST, RO_ACTIVE_PROGRAM, List.of(
                        new ProgramData(programUid.get(), List.of(new Data(optionUid.get(), optionEnumValue.get())))),
                        null, 1);
            }
        } else if (CHANNEL_SELECTED_PROGRAM.equals(channelUID.getId()) && command instanceof StringType) {
            getSelectedProgramUid(command.toFullString()).ifPresent(programUid -> send(Action.POST, RO_ACTIVE_PROGRAM,
                    List.of(new ProgramData(programUid, null)), null, 1));
        }
    }

    @Override
    protected void onApplianceDescriptionEvent(final DescriptionChangeEvent event) {
        super.onApplianceDescriptionEvent(event);
        if (event.enumType() != null) {
            switch (event.name()) {
                case COOKING_LIGHTING_BRIGHTNESS ->
                    getLinkedChannel(CHANNEL_FUNCTIONAL_LIGHT_BRIGHTNESS).ifPresent(channel -> {
                        var stateDescriptionBuilder = StateDescriptionFragmentBuilder.create().withPattern("%d %unit%");
                        if (event.min() != null) {
                            stateDescriptionBuilder.withMinimum(BigDecimal.valueOf(event.min()));
                        } else {
                            stateDescriptionBuilder.withMinimum(BigDecimal.valueOf(10));
                        }
                        if (event.max() != null) {
                            stateDescriptionBuilder.withMaximum(BigDecimal.valueOf(event.max()));
                        } else {
                            stateDescriptionBuilder.withMaximum(BigDecimal.valueOf(100));
                        }
                        if (event.stepSize() != null) {
                            stateDescriptionBuilder.withStep(BigDecimal.valueOf(event.stepSize()));
                        }
                        descriptionProvider.setStateDescriptionFragment(channel.getUID(),
                                stateDescriptionBuilder.build());
                    });
            }
        }
    }

    @Override
    protected void onApplianceEvent(Event event) {
        super.onApplianceEvent(event);

        switch (event.name()) {
            case EVENT_COOKING_LIGHTING -> getLinkedChannel(CHANNEL_FUNCTIONAL_LIGHT)
                    .ifPresent(channel -> updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean())));
            case EVENT_COOKING_LIGHTING_BRIGHTNESS -> getLinkedChannel(CHANNEL_FUNCTIONAL_LIGHT_BRIGHTNESS).ifPresent(
                    channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), PERCENT)));
            case EVENT_COOKING_BUTTON_TONES -> getLinkedChannel(CHANNEL_BUTTON_TONES)
                    .ifPresent(channel -> updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean())));
            case EVENT_HOOD_VENTING_LEVEL -> {
                getLinkedChannel(CHANNEL_HOOD_VENTING_LEVEL).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null ? UnDefType.UNDEF : new StringType(event.getValueAsString())));

                if (!STATE_FAN_OFF.equals(event.value())) {
                    getLinkedChannel(CHANNEL_HOOD_INTENSIVE_LEVEL).ifPresent(
                            channel -> updateState(channel.getUID(), new StringType(STATE_INTENSIVE_STAGE_OFF)));
                }
            }
            case EVENT_HOOD_INTENSIVE_LEVEL -> {
                getLinkedChannel(CHANNEL_HOOD_INTENSIVE_LEVEL).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null ? UnDefType.UNDEF : new StringType(event.getValueAsString())));

                if (!STATE_INTENSIVE_STAGE_OFF.equals(event.value())) {
                    getLinkedChannel(CHANNEL_HOOD_VENTING_LEVEL)
                            .ifPresent(channel -> updateState(channel.getUID(), new StringType(STATE_FAN_OFF)));
                }
            }
            case EVENT_POWER_STATE -> {
                if (STATE_OFF.equalsIgnoreCase(event.getValueAsString())) {
                    getLinkedChannel(CHANNEL_HOOD_VENTING_LEVEL)
                            .ifPresent(channel -> updateState(channel.getUID(), new StringType(STATE_FAN_OFF)));
                    getLinkedChannel(CHANNEL_HOOD_INTENSIVE_LEVEL).ifPresent(
                            channel -> updateState(channel.getUID(), new StringType(STATE_INTENSIVE_STAGE_OFF)));
                }
            }
            case EVENT_ACTIVE_PROGRAM -> getLinkedChannel(CHANNEL_SELECTED_PROGRAM).ifPresent(channel -> {
                var state = event.value() == null || STATE_NO_PROGRAM.equals(event.getValueAsString()) ? UnDefType.UNDEF
                        : new StringType(event.getValueAsString());

                updateState(channel.getUID(), state);
                if (state instanceof StringType) {
                    updateSelectedProgramDescription();
                }
            });
        }
    }

    private void updateLevelStateDescriptions() {
        getLinkedChannel(CHANNEL_HOOD_VENTING_LEVEL).ifPresent(channel -> {
            var ventingLevelStateOptions = getEnumerationMembers(HOOD_VENTING_LEVEL_ENUM_KEY).values().stream()
                    .map(ventingLevel -> new StateOption(ventingLevel, mapStage(ventingLevel))).toList();
            logger.debug("Set hood venting level state options. stateOptions={}", ventingLevelStateOptions);
            descriptionProvider.setStateOptions(channel.getUID(), ventingLevelStateOptions);
        });
        getLinkedChannel(CHANNEL_HOOD_INTENSIVE_LEVEL).ifPresent(channel -> {
            var intensiveLevelStateOptions = getEnumerationMembers(HOOD_INTENSIVE_LEVEL_ENUM_KEY).values().stream()
                    .map(intensiveLevel -> new StateOption(intensiveLevel, mapStage(intensiveLevel))).toList();
            logger.debug("Set hood intensive level state options. stateOptions={}", intensiveLevelStateOptions);
            descriptionProvider.setStateOptions(channel.getUID(), intensiveLevelStateOptions);
        });
    }

    private String mapStage(String stage) {
        switch (stage) {
            case STATE_FAN_OFF:
            case STATE_INTENSIVE_STAGE_OFF:
                stage = "Off";
                break;
            case STATE_FAN_STAGE_1:
            case STATE_INTENSIVE_STAGE_1:
                stage = "1";
                break;
            case STATE_FAN_STAGE_2:
            case STATE_INTENSIVE_STAGE_2:
                stage = "2";
                break;
            case STATE_FAN_STAGE_3:
                stage = "3";
                break;
            case STATE_FAN_STAGE_4:
                stage = "4";
                break;
            case STATE_FAN_STAGE_5:
                stage = "5";
                break;
            default:
                stage = mapStringType(stage);
        }

        return stage;
    }
}
