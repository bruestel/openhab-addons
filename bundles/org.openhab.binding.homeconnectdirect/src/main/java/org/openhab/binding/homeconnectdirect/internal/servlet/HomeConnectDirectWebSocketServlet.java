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
package org.openhab.binding.homeconnectdirect.internal.servlet;

import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SERVLET_WEB_SOCKET_PATTERN;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SUPPORTED_THING_TYPES;

import java.io.IOException;
import java.io.Serial;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.openhab.binding.homeconnectdirect.internal.handler.BaseHomeConnectDirectHandler;
import org.openhab.binding.homeconnectdirect.internal.handler.model.ApplianceMessage;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.serializer.ResourceDeserializer;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.serializer.ResourceSerializer;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.serializer.ZonedDateTimeSerializer;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;
import org.openhab.core.thing.binding.ThingHandler;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 *
 * Home Connect Direct web socket servlet. Proxy events from the appliance to the web console.
 *
 * @author Jonas Brüstel - Initial Contribution
 */
@NonNullByDefault
@WebServlet(urlPatterns = { SERVLET_WEB_SOCKET_PATTERN })
@Component(service = HttpServlet.class, configurationPolicy = ConfigurationPolicy.OPTIONAL)
public class HomeConnectDirectWebSocketServlet extends WebSocketServlet {

    private final Logger logger;
    private final ThingRegistry thingRegistry;
    private final ConfigurationAdmin configurationAdmin;
    private final ServletUtils utils;
    @Serial
    private static final long serialVersionUID = 3_406_770_341_849_696_274L;

    @Activate
    public HomeConnectDirectWebSocketServlet(@Reference ThingRegistry thingRegistry,
            @Reference ConfigurationAdmin configurationAdmin) {
        this.logger = LoggerFactory.getLogger(HomeConnectDirectWebSocketServlet.class);
        this.thingRegistry = thingRegistry;
        this.configurationAdmin = configurationAdmin;
        this.utils = new ServletUtils();
    }

    @Override
    public void configure(@Nullable WebSocketServletFactory factory) {
        if (factory != null) {
            factory.setCreator((servletUpgradeRequest, servletUpgradeResponse) -> {

                try {
                    // basic auth
                    var configuration = utils.getConfiguration(configurationAdmin);
                    if (configuration.basicAuthEnabled) {
                        utils.checkAuthorization(servletUpgradeRequest, servletUpgradeResponse,
                                configuration.basicAuthUsername, configuration.basicAuthPassword);
                    }

                    var path = servletUpgradeRequest.getRequestURI().getPath();
                    var thingUIDString = path.substring(path.lastIndexOf('/') + 1);
                    var thingHandler = getThingHandler(thingUIDString);

                    if (thingHandler.isPresent()) {
                        return new HomeConnectDirectWebSocketHandler(thingHandler.get());
                    }
                } catch (IndexOutOfBoundsException | IOException ignored) {
                }

                servletUpgradeResponse.setStatusCode(HttpStatus.NOT_FOUND_404);
                return null;
            });
        } else {
            logger.warn("Could not configure WebSocket Servlet!");
        }
    }

    private Optional<ThingHandler> getThingHandler(String thingUIDString) {
        return thingRegistry.stream().filter(thing -> SUPPORTED_THING_TYPES.contains(thing.getThingTypeUID()))
                .filter(thing -> Objects.equals(thing.getUID().toString(), thingUIDString)).findFirst()
                .map(Thing::getHandler);
    }

    @WebSocket
    public static class HomeConnectDirectWebSocketHandler {

        private final Logger logger;
        private final ThingHandler thingHandler;
        private final Gson gson;
        private final Consumer<ApplianceMessage> eventConsumer;
        private @Nullable Session session;

        public HomeConnectDirectWebSocketHandler(ThingHandler thingHandler) {
            this.thingHandler = thingHandler;
            this.logger = LoggerFactory.getLogger(HomeConnectDirectWebSocketHandler.class);
            this.gson = new GsonBuilder().registerTypeAdapter(Resource.class, new ResourceSerializer())
                    .registerTypeAdapter(Resource.class, new ResourceDeserializer())
                    .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeSerializer()).create();
            this.eventConsumer = this::sendMessage;
        }

        @OnWebSocketConnect
        public void onConnect(Session session) {
            this.session = session;
            if (thingHandler instanceof BaseHomeConnectDirectHandler handler) {
                handler.getApplianceMessages().forEach(this::sendMessage);
                handler.registerApplianceMessageListener(eventConsumer);
            }
        }

        @OnWebSocketMessage
        public void onMessage(Session session, String message) {
            if (message.equals("PING")) {
                try {
                    session.getRemote().sendString("PONG");
                } catch (IOException e) {
                    logger.debug("Could not send PONG! error={}", e.getMessage());
                }
            }
        }

        @OnWebSocketClose
        public void onClose(int statusCode, String reason) {
            this.session = null;
            if (thingHandler instanceof BaseHomeConnectDirectHandler handler) {
                handler.removeApplianceMessageListener(eventConsumer);
            }
        }

        public void sendMessage(ApplianceMessage message) {
            var session = this.session;
            if (session != null) {
                try {
                    session.getRemote().sendString(gson.toJson(message));
                } catch (IOException e) {
                    logger.debug("Could not send web socket message! error={}", e.getMessage());
                }
            }
        }
    }
}
