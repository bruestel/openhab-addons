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

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_COFFEE_MAKER_BEAN_CONTAINER_EMPTY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_COFFEE_MAKER_COUNTDOWN_CALC_AND_CLEAN;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_COFFEE_MAKER_COUNTDOWN_CLEANING;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_COFFEE_MAKER_COUNTDOWN_DESCALING;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_COFFEE_MAKER_COUNTDOWN_WATER_FILTER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_COFFEE_MAKER_DRIP_TRAY_FULL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_COFFEE_MAKER_EMPTY_MILK_TANK;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_COFFEE_MAKER_WATER_TANK_EMPTY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_COFFEE_MAKER_WATER_TANK_NEARLY_EMPTY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_PROCESS_PHASE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COFFEE_MAKER_BEAN_CONTAINER_EMPTY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COFFEE_MAKER_COUNTDOWN_CALC_AND_CLEAN;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COFFEE_MAKER_COUNTDOWN_CLEANING;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COFFEE_MAKER_COUNTDOWN_DESCALING;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COFFEE_MAKER_COUNTDOWN_WATER_FILTER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COFFEE_MAKER_DRIP_TRAY_FULL;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COFFEE_MAKER_EMPTY_MILK_TANK;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COFFEE_MAKER_PROCESS_PHASE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COFFEE_MAKER_WATER_TANK_EMPTY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COFFEE_MAKER_WATER_TANK_NEARLY_EMPTY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_PRESENT;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.homeconnectdirect.internal.handler.model.Event;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.UnDefType;

/**
 * The {@link HomeConnectDirectCoffeeMakerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class HomeConnectDirectCoffeeMakerHandler extends BaseHomeConnectDirectHandler {

    public HomeConnectDirectCoffeeMakerHandler(Thing thing, ApplianceProfileService applianceProfileService,
            HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider) {
        super(thing, applianceProfileService, descriptionProvider);
    }

    @Override
    protected void onApplianceEvent(Event event) {
        super.onApplianceEvent(event);

        switch (event.name()) {
            case EVENT_COFFEE_MAKER_PROCESS_PHASE ->
                getLinkedChannel(CHANNEL_PROCESS_PHASE).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null ? UnDefType.UNDEF : new StringType(event.getValueAsString())));
            case EVENT_COFFEE_MAKER_COUNTDOWN_CLEANING -> getLinkedChannel(CHANNEL_COFFEE_MAKER_COUNTDOWN_CLEANING)
                    .ifPresent(channel -> updateState(channel.getUID(),
                            event.value() == null ? UnDefType.UNDEF : new DecimalType(event.getValueAsInt())));
            case EVENT_COFFEE_MAKER_COUNTDOWN_CALC_AND_CLEAN ->
                getLinkedChannel(CHANNEL_COFFEE_MAKER_COUNTDOWN_CALC_AND_CLEAN)
                        .ifPresent(channel -> updateState(channel.getUID(),
                                event.value() == null ? UnDefType.UNDEF : new DecimalType(event.getValueAsInt())));
            case EVENT_COFFEE_MAKER_COUNTDOWN_DESCALING -> getLinkedChannel(CHANNEL_COFFEE_MAKER_COUNTDOWN_DESCALING)
                    .ifPresent(channel -> updateState(channel.getUID(),
                            event.value() == null ? UnDefType.UNDEF : new DecimalType(event.getValueAsInt())));
            case EVENT_COFFEE_MAKER_COUNTDOWN_WATER_FILTER ->
                getLinkedChannel(CHANNEL_COFFEE_MAKER_COUNTDOWN_WATER_FILTER)
                        .ifPresent(channel -> updateState(channel.getUID(),
                                event.value() == null ? UnDefType.UNDEF : new DecimalType(event.getValueAsInt())));
            case EVENT_COFFEE_MAKER_WATER_TANK_EMPTY ->
                getLinkedChannel(CHANNEL_COFFEE_MAKER_WATER_TANK_EMPTY).ifPresent(
                        channel -> updateState(channel.getUID(), OnOffType.from(STATE_PRESENT.equals(event.value()))));
            case EVENT_COFFEE_MAKER_WATER_TANK_NEARLY_EMPTY ->
                getLinkedChannel(CHANNEL_COFFEE_MAKER_WATER_TANK_NEARLY_EMPTY).ifPresent(
                        channel -> updateState(channel.getUID(), OnOffType.from(STATE_PRESENT.equals(event.value()))));
            case EVENT_COFFEE_MAKER_DRIP_TRAY_FULL -> getLinkedChannel(CHANNEL_COFFEE_MAKER_DRIP_TRAY_FULL).ifPresent(
                    channel -> updateState(channel.getUID(), OnOffType.from(STATE_PRESENT.equals(event.value()))));
            case EVENT_COFFEE_MAKER_EMPTY_MILK_TANK -> getLinkedChannel(CHANNEL_COFFEE_MAKER_EMPTY_MILK_TANK).ifPresent(
                    channel -> updateState(channel.getUID(), OnOffType.from(STATE_PRESENT.equals(event.value()))));
            case EVENT_COFFEE_MAKER_BEAN_CONTAINER_EMPTY ->
                getLinkedChannel(CHANNEL_COFFEE_MAKER_BEAN_CONTAINER_EMPTY).ifPresent(
                        channel -> updateState(channel.getUID(), OnOffType.from(STATE_PRESENT.equals(event.value()))));
        }
    }
}
