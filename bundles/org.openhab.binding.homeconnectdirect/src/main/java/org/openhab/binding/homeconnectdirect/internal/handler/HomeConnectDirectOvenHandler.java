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

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_OVEN_DURATION;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_OVEN_DURATION;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.OVEN_DURATION;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_VALUES;
import static org.openhab.core.library.unit.Units.SECOND;

import java.math.BigDecimal;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.homeconnectdirect.internal.handler.model.DescriptionChangeEvent;
import org.openhab.binding.homeconnectdirect.internal.handler.model.Event;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Data;
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

    private final Logger logger;

    public HomeConnectDirectOvenHandler(Thing thing, ApplianceProfileService applianceProfileService,
            HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider) {
        super(thing, applianceProfileService, descriptionProvider);

        this.logger = LoggerFactory.getLogger(HomeConnectDirectOvenHandler.class);
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
                    var stateDescription = StateDescriptionFragmentBuilder.create()
                            .withMinimum(BigDecimal.valueOf(event.min())).withMaximum(BigDecimal.valueOf(event.max()))
                            .withStep(BigDecimal.valueOf(event.stepSize())).withPattern("%d %unit%").build();
                    descriptionProvider.setStateDescriptionFragment(channel.getUID(), stateDescription);
                });
            }
        }
    }

    @Override
    protected void onApplianceEvent(Event event) {
        super.onApplianceEvent(event);

        switch (event.name()) {
            case EVENT_OVEN_DURATION -> getLinkedChannel(CHANNEL_OVEN_DURATION).ifPresent(
                    channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), SECOND)));
        }
    }
}
