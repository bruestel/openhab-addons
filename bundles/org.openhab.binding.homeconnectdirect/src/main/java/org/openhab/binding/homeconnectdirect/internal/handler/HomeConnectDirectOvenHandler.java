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

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_DOOR_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_OVEN_CURRENT_CAVITY_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_OVEN_CURRENT_MEAT_PROBE_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_OVEN_DURATION;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_OVEN_CURRENT_CAVITY_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_OVEN_CURRENT_MEAT_PROBE_TEMPERATURE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_OVEN_DURATION;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.OVEN_DURATION;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_OPEN;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_VALUES;
import static org.openhab.core.library.unit.SIUnits.CELSIUS;
import static org.openhab.core.library.unit.Units.SECOND;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.homeconnectdirect.internal.handler.model.DescriptionChangeEvent;
import org.openhab.binding.homeconnectdirect.internal.handler.model.Event;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Data;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HomeConnectDirectOvenHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class HomeConnectDirectOvenHandler extends BaseHomeConnectDirectHandler {

    private final static String DOOR_STATE_POSTFIX = ".DoorState";

    private final Logger logger;
    private final ConcurrentHashMap<String, Boolean> doorStateMap;

    public HomeConnectDirectOvenHandler(Thing thing, ApplianceProfileService applianceProfileService,
            HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider, String deviceId) {
        super(thing, applianceProfileService, descriptionProvider, deviceId);

        this.logger = LoggerFactory.getLogger(HomeConnectDirectOvenHandler.class);
        this.doorStateMap = new ConcurrentHashMap<>();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        super.handleCommand(channelUID, command);

        if (CHANNEL_OVEN_DURATION.equals(channelUID.getId()) && command instanceof QuantityType<?> quantity) {
            var durationQuantityType = quantity.toUnit(Units.SECOND);
            if (durationQuantityType != null) {
                mapFeatureName(OVEN_DURATION).ifPresent(optionUid -> send(Action.POST, RO_VALUES,
                        List.of(new Data(optionUid, durationQuantityType.intValue())), null, 1));
            } else {
                logger.warn("Could not set duration! uid={}", getThing().getUID());
            }
        }
    }

    @Override
    protected void onApplianceDescriptionEvent(DescriptionChangeEvent event) {
        super.onApplianceDescriptionEvent(event);
        if (event.enumType() != null) {
            switch (event.name()) {
                case OVEN_DURATION -> getLinkedChannel(CHANNEL_OVEN_DURATION).ifPresent(channel -> {
                    var stateDescriptionBuilder = StateDescriptionFragmentBuilder.create().withPattern("%d %unit%");
                    if (event.min() != null) {
                        stateDescriptionBuilder.withMinimum(BigDecimal.valueOf(event.min()));
                    }
                    if (event.max() != null) {
                        stateDescriptionBuilder.withMaximum(BigDecimal.valueOf(event.max()));
                    }
                    if (event.stepSize() != null) {
                        stateDescriptionBuilder.withStep(BigDecimal.valueOf(event.stepSize()));
                    }
                    descriptionProvider.setStateDescriptionFragment(channel.getUID(), stateDescriptionBuilder.build());
                });
            }
        }
    }

    @Override
    protected void onApplianceEvent(Event event) {
        if (!StringUtils.endsWith(event.name(), DOOR_STATE_POSTFIX)) {
            super.onApplianceEvent(event);
        }

        if (event.name().equals(EVENT_OVEN_DURATION)) {
            getLinkedChannel(CHANNEL_OVEN_DURATION).ifPresent(
                    channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), SECOND)));
        } else if (StringUtils.endsWith(event.name(), DOOR_STATE_POSTFIX)) {
            boolean open = STATE_OPEN.equals(event.value());
            doorStateMap.put(event.name(), open);
            boolean atLeastOneDoorIsOpen = doorStateMap.values().stream().anyMatch(aBoolean -> aBoolean);
            getLinkedChannel(CHANNEL_DOOR_STATE).ifPresent(channel -> updateState(channel.getUID(),
                    atLeastOneDoorIsOpen ? OpenClosedType.OPEN : OpenClosedType.CLOSED));
        } else if (event.name().equals(EVENT_OVEN_CURRENT_CAVITY_TEMPERATURE)) {
            getLinkedChannel(CHANNEL_OVEN_CURRENT_CAVITY_TEMPERATURE).ifPresent(
                    channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), CELSIUS)));
        } else if (event.name().equals(EVENT_OVEN_CURRENT_MEAT_PROBE_TEMPERATURE)) {
            getLinkedChannel(CHANNEL_OVEN_CURRENT_MEAT_PROBE_TEMPERATURE).ifPresent(
                    channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), CELSIUS)));
        }
    }
}
