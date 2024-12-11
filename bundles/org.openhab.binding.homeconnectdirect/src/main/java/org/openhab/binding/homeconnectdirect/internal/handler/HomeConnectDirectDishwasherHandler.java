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

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_DISHWASHER_MACHINE_CARE_REMINDER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_DISHWASHER_RINSE_AID_LACK;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_DISHWASHER_RINSE_AID_NEARLY_EMPTY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_DISHWASHER_SALT_LACK;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_DISHWASHER_SALT_NEARLY_EMPTY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_PROGRAM_PHASE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_DISHWASHER_MACHINE_CARE_REMINDER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_DISHWASHER_PROGRAM_PHASE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_DISHWASHER_RINSE_AID_LACK;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_DISHWASHER_RINSE_AID_NEARLY_EMPTY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_DISHWASHER_SALT_LACK;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_DISHWASHER_SALT_NEARLY_EMPTY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_PRESENT;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.homeconnectdirect.internal.handler.model.Event;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.UnDefType;

/**
 * The {@link HomeConnectDirectDishwasherHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class HomeConnectDirectDishwasherHandler extends BaseHomeConnectDirectHandler {

    public HomeConnectDirectDishwasherHandler(Thing thing, ApplianceProfileService applianceProfileService,
            HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider) {
        super(thing, applianceProfileService, descriptionProvider);
    }

    @Override
    protected void onApplianceEvent(Event event) {
        super.onApplianceEvent(event);

        switch (event.name()) {
            case EVENT_DISHWASHER_PROGRAM_PHASE ->
                getLinkedChannel(CHANNEL_PROGRAM_PHASE).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null ? UnDefType.UNDEF : new StringType(event.getValueAsString())));
            case EVENT_DISHWASHER_SALT_LACK ->
                getLinkedChannel(CHANNEL_DISHWASHER_SALT_LACK).ifPresent(channel -> updateState(channel.getUID(),
                        OnOffType.from(STATE_PRESENT.equalsIgnoreCase(event.getValueAsString()))));
            case EVENT_DISHWASHER_RINSE_AID_LACK ->
                getLinkedChannel(CHANNEL_DISHWASHER_RINSE_AID_LACK).ifPresent(channel -> updateState(channel.getUID(),
                        OnOffType.from(STATE_PRESENT.equalsIgnoreCase(event.getValueAsString()))));
            case EVENT_DISHWASHER_SALT_NEARLY_EMPTY -> getLinkedChannel(CHANNEL_DISHWASHER_SALT_NEARLY_EMPTY)
                    .ifPresent(channel -> updateState(channel.getUID(),
                            OnOffType.from(STATE_PRESENT.equalsIgnoreCase(event.getValueAsString()))));
            case EVENT_DISHWASHER_RINSE_AID_NEARLY_EMPTY -> getLinkedChannel(CHANNEL_DISHWASHER_RINSE_AID_NEARLY_EMPTY)
                    .ifPresent(channel -> updateState(channel.getUID(),
                            OnOffType.from(STATE_PRESENT.equalsIgnoreCase(event.getValueAsString()))));
            case EVENT_DISHWASHER_MACHINE_CARE_REMINDER -> getLinkedChannel(CHANNEL_DISHWASHER_MACHINE_CARE_REMINDER)
                    .ifPresent(channel -> updateState(channel.getUID(),
                            OnOffType.from(STATE_PRESENT.equalsIgnoreCase(event.getValueAsString()))));
        }
    }
}
