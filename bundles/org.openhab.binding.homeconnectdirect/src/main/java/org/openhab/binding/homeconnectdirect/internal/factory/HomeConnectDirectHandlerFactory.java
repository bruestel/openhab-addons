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
package org.openhab.binding.homeconnectdirect.internal.factory;

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CONFIGURATION_PID;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SUPPORTED_THING_TYPES;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.THING_TYPE_COFFEE_MAKER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.THING_TYPE_DISHWASHER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.THING_TYPE_DRYER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.THING_TYPE_OVEN;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.THING_TYPE_WASHER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.THING_TYPE_WASHER_DRYER;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.homeconnectdirect.internal.handler.BaseHomeConnectDirectHandler;
import org.openhab.binding.homeconnectdirect.internal.handler.HomeConnectDirectCoffeeMakerHandler;
import org.openhab.binding.homeconnectdirect.internal.handler.HomeConnectDirectDishwasherHandler;
import org.openhab.binding.homeconnectdirect.internal.handler.HomeConnectDirectOvenHandler;
import org.openhab.binding.homeconnectdirect.internal.handler.HomeConnectDirectWasherDryerHandler;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link HomeConnectDirectHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = CONFIGURATION_PID, service = ThingHandlerFactory.class)
public class HomeConnectDirectHandlerFactory extends BaseThingHandlerFactory {

    private final ApplianceProfileService applianceProfileService;
    private final HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider;

    @Activate
    public HomeConnectDirectHandlerFactory(@Reference ApplianceProfileService applianceProfileService,
            @Reference HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider) {
        this.applianceProfileService = applianceProfileService;
        this.descriptionProvider = descriptionProvider;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_WASHER.equals(thingTypeUID) || THING_TYPE_WASHER_DRYER.equals(thingTypeUID)
                || THING_TYPE_DRYER.equals(thingTypeUID)) {
            return new HomeConnectDirectWasherDryerHandler(thing, applianceProfileService, descriptionProvider);
        } else if (THING_TYPE_DISHWASHER.equals(thingTypeUID)) {
            return new HomeConnectDirectDishwasherHandler(thing, applianceProfileService, descriptionProvider);
        } else if (THING_TYPE_COFFEE_MAKER.equals(thingTypeUID)) {
            return new HomeConnectDirectCoffeeMakerHandler(thing, applianceProfileService, descriptionProvider);
        } else if (THING_TYPE_OVEN.equals(thingTypeUID)) {
            return new HomeConnectDirectOvenHandler(thing, applianceProfileService, descriptionProvider);
        } else {
            return new BaseHomeConnectDirectHandler(thing, applianceProfileService, descriptionProvider);
        }
    }
}
