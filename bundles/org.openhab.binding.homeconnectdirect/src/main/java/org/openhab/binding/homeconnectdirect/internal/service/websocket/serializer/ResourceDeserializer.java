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
package org.openhab.binding.homeconnectdirect.internal.service.websocket.serializer;

import java.lang.reflect.Type;

import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

public class ResourceDeserializer implements JsonDeserializer<Resource> {
    @Override
    public Resource deserialize(JsonElement jsonElement, Type type,
            JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        String resourceString = jsonElement.getAsString();
        String[] parts = resourceString.split("/", 3);
        String service = parts.length > 1 ? parts[1] : "";
        String endpoint = parts.length > 2 ? parts[2] : "";

        return new Resource(service, endpoint);
    }
}
