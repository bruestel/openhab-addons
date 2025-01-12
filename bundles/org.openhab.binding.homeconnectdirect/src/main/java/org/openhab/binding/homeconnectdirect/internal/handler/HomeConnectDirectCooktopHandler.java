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

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_BUTTON_TONES;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.COOKING_BUTTON_TONES;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_COOKING_BUTTON_TONES;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_VALUES;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.homeconnectdirect.internal.handler.model.Event;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Data;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.types.Command;

/**
 * The {@link HomeConnectDirectCooktopHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class HomeConnectDirectCooktopHandler extends BaseHomeConnectDirectHandler {

    public HomeConnectDirectCooktopHandler(Thing thing, ApplianceProfileService applianceProfileService,
            HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider, String deviceId) {
        super(thing, applianceProfileService, descriptionProvider, deviceId);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        super.handleCommand(channelUID, command);

        if (CHANNEL_BUTTON_TONES.equals(channelUID.getId()) && command instanceof OnOffType) {
            mapFeatureName(COOKING_BUTTON_TONES).ifPresent(optionUid -> send(Action.POST, RO_VALUES,
                    List.of(new Data(optionUid, OnOffType.ON.equals(command))), null, 1));
        }
    }

    @Override
    protected void onApplianceEvent(Event event) {
        super.onApplianceEvent(event);

        if (event.name().equals(EVENT_COOKING_BUTTON_TONES)) {
            getLinkedChannel(CHANNEL_BUTTON_TONES)
                    .ifPresent(channel -> updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean())));
        }
    }
}
