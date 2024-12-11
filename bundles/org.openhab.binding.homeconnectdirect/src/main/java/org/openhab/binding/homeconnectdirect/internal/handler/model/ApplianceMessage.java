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
package org.openhab.binding.homeconnectdirect.internal.handler.model;

import java.time.ZonedDateTime;
import java.util.List;

import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Message;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource;

public record ApplianceMessage(ZonedDateTime dateTime, long id, MessageType type, Resource resource, int version,
        Action action, Integer code, Message<Object> source, List<Event> data,
        List<DescriptionChangeEvent> descriptions) {
}
