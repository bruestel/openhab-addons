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

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class ResourceSerializer implements JsonSerializer<Resource> {

    @Override
    public JsonElement serialize(Resource resource, Type type, JsonSerializationContext jsonSerializationContext) {
        String resourceString = "/" + resource.service() + "/" + resource.endpoint();
        return new JsonPrimitive(resourceString);
    }
}
