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

import static org.apache.commons.lang3.StringUtils.containsIgnoreCase;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.ABORT_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.ACTIVE_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_ACTIVE_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_DOOR_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_LOCAL_CONTROL_ACTIVE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_OPERATION_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_POWER_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_PROGRAM_COMMAND;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_PROGRAM_PROGRESS;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_REMAINING_PROGRAM_TIME;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_REMOTE_CONTROL_ACTIVE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_REMOTE_START_ALLOWANCE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_SELECTED_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_TYPE_NUMBER;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_TYPE_STRING;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CHANNEL_TYPE_SWITCH;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.COMMAND_PAUSE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.COMMAND_RESUME;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.COMMAND_START;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.COMMAND_STOP;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.CONFIGURATION_EVENT_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_ACTIVE_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_DOOR_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_LOCAL_CONTROL_ACTIVE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_OPERATION_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_POWER_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_PROGRAM_PROGRESS;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_REMAINING_PROGRAM_TIME;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_REMOTE_CONTROL_ACTIVE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_REMOTE_CONTROL_START_ALLOWED;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.EVENT_SELECTED_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.PAUSE_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.POWER_STATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.POWER_STATE_ENUM_KEY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.RESUME_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.SELECTED_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_NO_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_OFF;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_ON;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_OPEN;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.STATE_STANDBY;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.WS_AES_URI_TEMPLATE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.WS_DEVICE_NAME;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.WS_DEVICE_TYPE;
import static org.openhab.binding.homeconnectdirect.internal.HomeConnectDirectBindingConstants.WS_TLS_URI_TEMPLATE;
import static org.openhab.binding.homeconnectdirect.internal.service.profile.model.ConnectionType.AES;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action.GET;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action.NOTIFY;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action.RESPONSE;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.CI;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.CI_AUTHENTICATION;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.CI_INFO;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.CI_SERVICES;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.CI_TZ_INFO;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.EI_DEVICE_READY;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.EI_INITIAL_VALUES;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.IZ;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.IZ_INFO;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.NI;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.NI_INFO;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_ACTIVE_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_ALL_DESCRIPTION_CHANGES;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_ALL_MANDATORY_VALUES;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_DESCRIPTION_CHANGE;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_SELECTED_PROGRAM;
import static org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource.RO_VALUES;
import static org.openhab.core.library.unit.Units.ONE;
import static org.openhab.core.library.unit.Units.PERCENT;
import static org.openhab.core.library.unit.Units.SECOND;

import java.net.URI;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.homeconnectdirect.internal.configuration.HomeConnectDirectApplianceConfiguration;
import org.openhab.binding.homeconnectdirect.internal.handler.model.ApplianceMessage;
import org.openhab.binding.homeconnectdirect.internal.handler.model.DescriptionChangeEvent;
import org.openhab.binding.homeconnectdirect.internal.handler.model.Event;
import org.openhab.binding.homeconnectdirect.internal.handler.model.MessageType;
import org.openhab.binding.homeconnectdirect.internal.provider.HomeConnectDirectDynamicStateDescriptionProvider;
import org.openhab.binding.homeconnectdirect.internal.service.profile.ApplianceProfileService;
import org.openhab.binding.homeconnectdirect.internal.service.profile.model.ApplianceDescription;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.WebSocketAesClientService;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.WebSocketClientService;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.WebSocketHandler;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.WebSocketTlsConscryptClientService;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.exception.WebSocketClientServiceException;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Action;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.ApplianceInfo;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Data;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.DescriptionChangeData;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.DeviceData;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.FirstMessageId;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Message;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.ProgramData;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Resource;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.model.Service;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.serializer.ResourceDeserializer;
import org.openhab.binding.homeconnectdirect.internal.service.websocket.serializer.ResourceSerializer;
import org.openhab.binding.homeconnectdirect.internal.utils.LimitedSizeList;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.StateOption;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

@NonNullByDefault
public class BaseHomeConnectDirectHandler extends BaseThingHandler implements WebSocketHandler {

    private final ApplianceProfileService applianceProfileService;
    private final Logger logger;
    private final Gson gson;
    private final List<Service> services;
    private final SecureRandom secureRandom;
    private final AtomicBoolean connected;
    private final AtomicBoolean disposeInitialized;
    private final LimitedSizeList<ApplianceMessage> applianceMessages;
    private final List<Consumer<ApplianceMessage>> applianceMessageConsumers;
    protected final HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider;
    private final Map<String, Boolean> programMap;
    private final String deviceId;

    private @Nullable ScheduledFuture<?> reconnectFuture;
    private @Nullable WebSocketClientService webSocketClientService;
    private @Nullable HomeConnectDirectApplianceConfiguration configuration;
    private @Nullable ApplianceDescription applianceDescription;
    private @Nullable String selectedProgram;
    private long outgoingMessageId;
    private long sessionId;

    public BaseHomeConnectDirectHandler(Thing thing, ApplianceProfileService applianceProfileService,
            HomeConnectDirectDynamicStateDescriptionProvider descriptionProvider, String deviceId) {
        super(thing);

        this.applianceProfileService = applianceProfileService;
        this.logger = LoggerFactory.getLogger(BaseHomeConnectDirectHandler.class);
        this.gson = new GsonBuilder().registerTypeAdapter(Resource.class, new ResourceSerializer())
                .registerTypeAdapter(Resource.class, new ResourceDeserializer()).create();
        this.secureRandom = new SecureRandom();
        this.services = new ArrayList<>();
        this.connected = new AtomicBoolean(false);
        this.disposeInitialized = new AtomicBoolean(false);
        this.applianceMessages = new LimitedSizeList<>(300);
        this.applianceMessageConsumers = Collections.synchronizedList(new ArrayList<>());
        this.programMap = new ConcurrentHashMap<>();
        this.descriptionProvider = descriptionProvider;
        this.deviceId = deviceId;
    }

    @Override
    public void initialize() {
        disposeInitialized.set(false);
        services.clear();
        var configuration = getConfigAs(HomeConnectDirectApplianceConfiguration.class);
        this.configuration = configuration;

        if (StringUtils.isBlank(configuration.address) || StringUtils.isBlank(configuration.haId)
                || StringUtils.isBlank(configuration.connectionType)) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "The configuration contains an error. Please fill in all mandatory fields.");
        } else {
            var profile = applianceProfileService.getProfile(configuration.haId);

            if (profile.isEmpty()) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                        "Please fetch the appliance profiles from your Home Connect account at http(s)://[YOUROPENHAB]:[YOURPORT]/homeconnectdirect (e.g. http://192.168.178.100:8080/homeconnectdirect).");
            } else {
                if (!ThingStatus.OFFLINE.equals(thing.getStatus())) {
                    updateStatus(ThingStatus.UNKNOWN);
                }

                scheduler.execute(() -> {
                    var applianceDescription = applianceProfileService.getDescription(profile.get());
                    this.applianceDescription = applianceDescription;

                    // program list
                    programMap.clear();
                    applianceDescription.deviceDescription().programList.forEach(program -> {
                        var programName = applianceDescription.featureMapping().featureMap.get(program.uid);
                        if (programName != null) {
                            programMap.put(programName, program.available);
                        }
                    });
                    updateSelectedProgramDescription();

                    var connectionType = profile.get().connectionType();
                    var key = profile.get().key();
                    var iv = profile.get().iv();

                    try {
                        var osName = System.getProperty("os.name");
                        var osArch = System.getProperty("os.arch");
                        if (AES.equals(connectionType)) {
                            URI uri = URI.create(String.format(WS_AES_URI_TEMPLATE, configuration.address));
                            var webSocketClientService = new WebSocketAesClientService(getThing(), uri, key, iv, this,
                                    scheduler);
                            webSocketClientService.connect();
                            this.webSocketClientService = webSocketClientService;
                        } else if (isConscryptSupported(osName, osArch)) {
                            URI uri = URI.create(String.format(WS_TLS_URI_TEMPLATE, configuration.address));
                            var webSocketClientService = new WebSocketTlsConscryptClientService(getThing(), uri, key,
                                    this, scheduler);
                            webSocketClientService.connect();
                            this.webSocketClientService = webSocketClientService;
                        } else {
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.DISABLED,
                                    "TLS connection is not supported on the current system configuration (" + osName
                                            + " " + osArch + ").");
                        }
                    } catch (WebSocketClientServiceException e) {
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                        scheduleReconnect();
                    }
                });
            }
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            sendGet(RO_ALL_MANDATORY_VALUES);
        } else if (CHANNEL_POWER_STATE.equals(channelUID.getId()) && command instanceof OnOffType) {
            mapFeatureName(POWER_STATE).ifPresent(settingUid -> {
                Optional<Integer> value;
                if (OnOffType.ON.equals(command)) {
                    value = mapEnumerationValueString(POWER_STATE_ENUM_KEY, STATE_ON);
                } else {
                    value = mapEnumerationValueString(POWER_STATE_ENUM_KEY, STATE_OFF)
                            .or(() -> mapEnumerationValueString(POWER_STATE_ENUM_KEY, STATE_STANDBY));
                }

                value.ifPresent(
                        integer -> send(Action.POST, RO_VALUES, List.of(new Data(settingUid, integer)), null, 1));
            });

        } else if (CHANNEL_PROGRAM_COMMAND.equals(channelUID.getId()) && command instanceof StringType) {
            if (COMMAND_START.equalsIgnoreCase(command.toFullString())) {
                var selectedProgram = this.selectedProgram;
                if (selectedProgram != null) {
                    getSelectedProgramUid(selectedProgram).ifPresent(programUid -> send(Action.POST, RO_ACTIVE_PROGRAM,
                            List.of(new ProgramData(programUid, null)), null, 1));
                }
            } else if (COMMAND_STOP.equalsIgnoreCase(command.toFullString())) {
                mapFeatureName(ABORT_PROGRAM).ifPresent(
                        commandUid -> send(Action.POST, RO_VALUES, List.of(new Data(commandUid, true)), null, 1));
            } else if (COMMAND_PAUSE.equalsIgnoreCase(command.toFullString())) {
                mapFeatureName(PAUSE_PROGRAM).ifPresent(
                        commandUid -> send(Action.POST, RO_VALUES, List.of(new Data(commandUid, true)), null, 1));
            } else if (COMMAND_RESUME.equalsIgnoreCase(command.toFullString())) {
                mapFeatureName(RESUME_PROGRAM).ifPresent(
                        commandUid -> send(Action.POST, RO_VALUES, List.of(new Data(commandUid, true)), null, 1));
            } else if (isValidJson(command.toFullString())) {
                try {
                    ProgramData programData = gson.fromJson(command.toFullString(), ProgramData.class);
                    if (programData != null) {
                        send(Action.POST, RO_ACTIVE_PROGRAM, List.of(programData), null, 1);
                    }
                } catch (JsonSyntaxException e) {
                    logger.warn("Could not deserialize command! command={} error={}", command.toFullString(),
                            e.getMessage());
                }
            }
        } else if (CHANNEL_SELECTED_PROGRAM.equals(channelUID.getId()) && command instanceof StringType) {
            getSelectedProgramUid(command.toFullString()).ifPresent(selectedProgramUid -> send(Action.POST,
                    RO_SELECTED_PROGRAM, List.of(new ProgramData(selectedProgramUid, null)), null, 1));
        } else {
            // check dynamic channels
            getLinkedChannel(channelUID.getId())
                    .filter(channel -> CHANNEL_TYPE_SWITCH.equals(channel.getChannelTypeUID())
                            || CHANNEL_TYPE_STRING.equals(channel.getChannelTypeUID())
                            || CHANNEL_TYPE_NUMBER.equals(channel.getChannelTypeUID()))
                    .filter(channel -> channel.getConfiguration().containsKey(CONFIGURATION_EVENT_KEY))
                    .ifPresent(channel -> {
                        var eventKey = channel.getConfiguration().get(CONFIGURATION_EVENT_KEY).toString();
                        mapFeatureName(eventKey).ifPresent(uid -> {
                            if (CHANNEL_TYPE_SWITCH.equals(channel.getChannelTypeUID())
                                    && command instanceof OnOffType) {
                                send(Action.POST, RO_VALUES, List.of(new Data(uid, OnOffType.ON.equals(command))), null,
                                        1);
                            } else if (CHANNEL_TYPE_STRING.equals(channel.getChannelTypeUID())
                                    && command instanceof StringType) {
                                var enumerationTypeUid = getEnumerationUid(uid);
                                var applianceDescription = this.applianceDescription;
                                if (enumerationTypeUid != null && applianceDescription != null) {
                                    applianceDescription.featureMapping().enumDescriptionList.stream()
                                            .filter(enumDescription -> enumDescription.id == enumerationTypeUid)
                                            .filter(enumDescription -> enumDescription.values
                                                    .containsValue(command.toFullString()))
                                            .map(enumDescription -> enumDescription.values).findFirst()
                                            .flatMap(integerStringMap -> integerStringMap.entrySet().stream()
                                                    .filter(entry -> entry.getValue().equals(command.toFullString()))
                                                    .findFirst().map(Map.Entry::getKey))
                                            .ifPresent(value -> send(Action.POST, RO_VALUES,
                                                    List.of(new Data(uid, value)), null, 1));
                                } else {
                                    send(Action.POST, RO_VALUES, List.of(new Data(uid, command.toFullString())), null,
                                            1);
                                }
                            } else if (CHANNEL_TYPE_NUMBER.equals(channel.getChannelTypeUID())
                                    && command instanceof Number number) {
                                send(Action.POST, RO_VALUES, List.of(new Data(uid, number.intValue())), null, 1);
                            }
                        });
                    });
        }
    }

    @Override
    public void dispose() {
        disposeInitialized.set(true);
        var webSocketClientService = this.webSocketClientService;
        if (webSocketClientService != null) {
            webSocketClientService.disconnect();
        }
        stopReconnectSchedule();
        applianceMessageConsumers.clear();
    }

    @Override
    public void onWebSocketConnect() {
        updateStatus(ThingStatus.ONLINE);
        connected.set(true);
        logger.debug("WebSocket connection opened (thingUID={}).", thing.getUID());
    }

    @Override
    public void onWebSocketMessage(String rawMessage, WebSocketClientService websocketClientService) {
        Message<Object> msg = gson.fromJson(rawMessage, new TypeToken<Message<Object>>() {
        }.getType());

        if (msg != null) {
            var applianceMessage = mapApplianceMessage(msg, rawMessage, true);
            applianceMessages.add(applianceMessage);
            applianceMessageConsumers.forEach(consumer -> consumer.accept(applianceMessage));
            applianceMessage.data().forEach(this::onApplianceEvent);
            applianceMessage.descriptions().forEach(this::onApplianceDescriptionEvent);

            switch (msg.action()) {
                case POST -> {
                    if (EI_INITIAL_VALUES.equals(msg.resource())) {
                        Message<FirstMessageId> message = Objects
                                .requireNonNull(gson.fromJson(rawMessage, new TypeToken<Message<FirstMessageId>>() {
                                }.getType()));
                        sessionId = message.sessionId();
                        outgoingMessageId = Objects.requireNonNull(message.data()).get(0).messageId();

                        // reply
                        var data = new DeviceData(WS_DEVICE_TYPE, WS_DEVICE_NAME, deviceId);
                        send(RESPONSE, message.resource(), List.of(data), message.messageId(), message.version());

                        // get services
                        sendGet(CI_SERVICES);
                    } else {
                        logger.warn("Unknown resource! message={} thingUID={}", msg, thing.getUID());
                    }
                }
                case RESPONSE, NOTIFY -> {
                    if (msg.code() != null) {
                        logger.trace("Received message: resource={} code={} thingUID={}", msg.resource(), msg.code(),
                                thing.getUID());
                    } else if (CI_SERVICES.equals(msg.resource())) {
                        Message<Service> message = Objects
                                .requireNonNull(gson.fromJson(rawMessage, new TypeToken<Message<Service>>() {
                                }.getType()));
                        services.clear();
                        services.addAll(Objects.requireNonNull(message.data()));

                        // authenticate (needed by washer)
                        sendGet(CI_AUTHENTICATION, List.of(Map.of("nonce", generateNonce())));

                        // needed by some services
                        sendNotify(EI_DEVICE_READY);

                        // get device info
                        services.forEach(s -> {
                            switch (s.service()) {
                                case CI -> {
                                    sendGet(CI_INFO);
                                    sendGet(CI_TZ_INFO);
                                }
                                case IZ -> sendGet(IZ_INFO);
                                case NI -> sendGet(NI_INFO);
                            }
                        });

                        // get appliance info and subscribe for updates
                        sendGet(RO_ALL_MANDATORY_VALUES);
                        sendGet(RO_VALUES);
                        sendGet(RO_ALL_DESCRIPTION_CHANGES);
                    } else if (IZ_INFO.equals(msg.resource()) || CI_INFO.equals(msg.resource())) {
                        Message<ApplianceInfo> message = Objects
                                .requireNonNull(gson.fromJson(rawMessage, new TypeToken<Message<ApplianceInfo>>() {
                                }.getType()));
                        var applianceInfo = Objects.requireNonNull(message.data()).get(0);
                        logger.debug("Received appliance info: {} (thingUID={})", applianceInfo, thing.getUID());
                    } else if (RO_ALL_MANDATORY_VALUES.equals(msg.resource()) || RO_VALUES.equals(msg.resource())) {
                        Message<Data> message = Objects
                                .requireNonNull(gson.fromJson(rawMessage, new TypeToken<Message<Data>>() {
                                }.getType()));

                        logger.debug("Received appliance update: {} (thingUID={})", message.data(), thing.getUID());
                    }
                }
                case GET -> logger.trace("Received message: {} ({})", msg, thing.getUID());
            }
        }
    }

    @Override
    public void onWebSocketClose() {
        connected.set(false);
        updateStatus(ThingStatus.OFFLINE);
        scheduleReconnect();
        logger.debug("WebSocket closed (thingUID={})!", thing.getUID());
    }

    @Override
    public void onWebSocketError(Throwable throwable) {
        logger.debug("WebSocket error: {} (thingUID={})", throwable.getMessage(), thing.getUID());

        if (!connected.get()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, throwable.getMessage());
            scheduleReconnect();
        }
    }

    public List<ApplianceMessage> getApplianceMessages() {
        return applianceMessages.getAllElements();
    }

    public void registerApplianceMessageListener(Consumer<ApplianceMessage> consumer) {
        applianceMessageConsumers.add(consumer);
    }

    public void removeApplianceMessageListener(Consumer<ApplianceMessage> consumer) {
        applianceMessageConsumers.remove(consumer);
    }

    protected void onApplianceDescriptionEvent(DescriptionChangeEvent event) {
        var applianceDescription = this.applianceDescription;

        if (applianceDescription != null && applianceDescription.deviceDescription().programList.stream()
                .anyMatch(program -> program.uid == event.uid())) {
            programMap.put(event.name(), event.available());
            updateSelectedProgramDescription();
        }
    }

    protected void onApplianceEvent(Event event) {
        switch (event.name()) {
            case EVENT_POWER_STATE ->
                getLinkedChannel(CHANNEL_POWER_STATE).ifPresent(channel -> updateState(channel.getUID(),
                        OnOffType.from(STATE_ON.equalsIgnoreCase(event.getValueAsString()))));
            case EVENT_DOOR_STATE ->
                getLinkedChannel(CHANNEL_DOOR_STATE).ifPresent(channel -> updateState(channel.getUID(),
                        STATE_OPEN.equals(event.value()) ? OpenClosedType.OPEN : OpenClosedType.CLOSED));
            case EVENT_OPERATION_STATE ->
                getLinkedChannel(CHANNEL_OPERATION_STATE).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null ? UnDefType.UNDEF : new StringType(event.getValueAsString())));
            case EVENT_REMOTE_CONTROL_START_ALLOWED -> getLinkedChannel(CHANNEL_REMOTE_START_ALLOWANCE)
                    .ifPresent(channel -> updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean())));
            case EVENT_REMOTE_CONTROL_ACTIVE -> getLinkedChannel(CHANNEL_REMOTE_CONTROL_ACTIVE)
                    .ifPresent(channel -> updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean())));
            case EVENT_LOCAL_CONTROL_ACTIVE -> getLinkedChannel(CHANNEL_LOCAL_CONTROL_ACTIVE)
                    .ifPresent(channel -> updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean())));
            case EVENT_SELECTED_PROGRAM -> {
                selectedProgram = event.getValueAsString();
                getLinkedChannel(CHANNEL_SELECTED_PROGRAM).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null || STATE_NO_PROGRAM.equals(event.getValueAsString()) ? UnDefType.UNDEF
                                : new StringType(event.getValueAsString())));
            }
            case EVENT_ACTIVE_PROGRAM ->
                getLinkedChannel(CHANNEL_ACTIVE_PROGRAM).ifPresent(channel -> updateState(channel.getUID(),
                        event.value() == null || STATE_NO_PROGRAM.equals(event.getValueAsString()) ? UnDefType.UNDEF
                                : new StringType(event.getValueAsString())));
            case EVENT_REMAINING_PROGRAM_TIME -> getLinkedChannel(CHANNEL_REMAINING_PROGRAM_TIME).ifPresent(
                    channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), SECOND)));
            case EVENT_PROGRAM_PROGRESS -> getLinkedChannel(CHANNEL_PROGRAM_PROGRESS).ifPresent(
                    channel -> updateState(channel.getUID(), new QuantityType<>(event.getValueAsInt(), PERCENT)));
        }

        // update dynamic channels
        getThing().getChannels().stream()
                .filter(channel -> CHANNEL_TYPE_SWITCH.equals(channel.getChannelTypeUID())
                        || CHANNEL_TYPE_STRING.equals(channel.getChannelTypeUID())
                        || CHANNEL_TYPE_NUMBER.equals(channel.getChannelTypeUID()))
                .filter(channel -> channel.getConfiguration().containsKey(CONFIGURATION_EVENT_KEY))
                .filter(channel -> event.name().equals(channel.getConfiguration().get(CONFIGURATION_EVENT_KEY)))
                .filter(channel -> isLinked(channel.getUID())).forEach(channel -> {
                    if (CHANNEL_TYPE_SWITCH.equals(channel.getChannelTypeUID())) {
                        updateState(channel.getUID(), OnOffType.from(event.getValueAsBoolean()));
                    } else if (CHANNEL_TYPE_STRING.equals(channel.getChannelTypeUID())) {
                        updateState(channel.getUID(), StringType.valueOf(event.getValueAsString()));
                    } else if (CHANNEL_TYPE_NUMBER.equals(channel.getChannelTypeUID())) {
                        updateState(channel.getUID(), QuantityType.valueOf(event.getValueAsInt(), ONE));
                    }
                });
    }

    protected Optional<Channel> getLinkedChannel(String channelId) {
        Channel channel = getThing().getChannel(channelId);
        if (channel == null || !isLinked(channelId)) {
            return Optional.empty();
        } else {
            return Optional.of(channel);
        }
    }

    protected Optional<Integer> getSelectedProgramUid(String programKey) {
        var applianceDescription = this.applianceDescription;
        Optional<Integer> programUid = Optional.empty();
        if (applianceDescription != null) {
            programUid = mapFeatureName(programKey).filter(uid -> applianceDescription.deviceDescription().programList
                    .stream().anyMatch(program -> uid == program.uid));
        }

        return programUid;
    }

    protected Optional<Integer> mapEnumerationValueString(String enumKey, String value) {
        var applianceDescription = this.applianceDescription;
        if (applianceDescription != null) {
            return applianceDescription.featureMapping().enumDescriptionList.stream()
                    .filter(enumDescription -> enumDescription.key.equals(enumKey)).findFirst()
                    .map(enumDescription -> enumDescription.values).filter(map -> map.containsValue(value))
                    .map(map -> map.entrySet().stream()
                            .filter(integerStringEntry -> value.equals(integerStringEntry.getValue()))
                            .map(Map.Entry::getKey).findFirst())
                    .filter(Optional::isPresent).map(Optional::get);
        }
        return Optional.empty();
    }

    protected Optional<String> mapEnumerationValue(String enumKey, int enumerationValue) {
        var applianceDescription = this.applianceDescription;
        if (applianceDescription != null) {
            return applianceDescription.featureMapping().enumDescriptionList.stream()
                    .filter(enumDescription -> enumDescription.key.equals(enumKey)).findFirst()
                    .filter(enumDescription -> enumDescription.values.containsKey(enumerationValue))
                    .map(enumDescription -> enumDescription.values.get(enumerationValue));
        }
        return Optional.empty();
    }

    protected void sendGet(Resource resource) {
        sendGet(resource, null);
    }

    protected void sendGet(Resource resource, @Nullable List<Object> data) {
        send(GET, resource, data, null, null);
    }

    protected void sendNotify(Resource resource) {
        send(NOTIFY, resource, null, null, null);
    }

    protected void send(Action action, Resource resource, @Nullable List<Object> data, @Nullable Long messageId,
            @Nullable Integer versionObject) {
        int version;
        if (versionObject != null) {
            version = versionObject;
        } else {
            var latestVersion = services.stream().filter(s -> s.service().equals(resource.service())).findFirst()
                    .map(Service::version).orElse(null);
            version = Objects.requireNonNullElse(latestVersion, 1);
        }

        // special case GET services
        if (GET.equals(action) && CI_SERVICES.equals(resource)) {
            version = 1;
        }

        long msgId = Objects.requireNonNullElseGet(messageId, () -> outgoingMessageId++);

        var message = new Message<>(sessionId, msgId, resource, version, action, null, data);
        var webSocketClientService = this.webSocketClientService;
        var rawMessage = gson.toJson(message);
        if (webSocketClientService != null) {
            webSocketClientService.send(rawMessage);
        }

        var applianceMessage = mapApplianceMessage(message, rawMessage, false);
        applianceMessages.add(applianceMessage);
        applianceMessageConsumers.forEach(consumer -> consumer.accept(applianceMessage));
    }

    protected Object mapFeatureValue(int uid, Object value) {
        var applianceDescription = this.applianceDescription;

        if (applianceDescription != null) {
            var enumerationUid = getEnumerationUid(uid);
            if (enumerationUid != null) {
                Integer enumValueKey = mapObjectToInteger(value);
                if (enumValueKey != null) {
                    var enumValue = applianceDescription.featureMapping().enumDescriptionList.stream()
                            .filter(enumDescription -> enumDescription.id == enumerationUid)
                            .filter(enumDescription -> enumDescription.values.containsKey(enumValueKey)).findFirst()
                            .map(enumDescription -> enumDescription.values.get(enumValueKey));
                    if (enumValue.isPresent()) {
                        return enumValue.get();
                    }
                }
            }
        }

        return value;
    }

    protected Object mapProgramUid(Object programUidObject) {
        var applianceDescription = this.applianceDescription;
        var programUid = mapObjectToInteger(programUidObject);

        if (applianceDescription != null && programUid != null) {
            var program = applianceDescription.featureMapping().featureMap.get(programUid);
            if (program != null) {
                return program;
            }
        }

        return Objects.requireNonNullElse(programUid, programUidObject);
    }

    protected Optional<Integer> mapFeatureName(String featureName) {
        var applianceDescription = this.applianceDescription;

        if (applianceDescription != null) {
            return applianceDescription.featureMapping().featureMap.entrySet().stream()
                    .filter(entry -> featureName.equals(entry.getValue())).findFirst().map(Map.Entry::getKey)
                    .or(() -> applianceDescription.featureMapping().errorMap.entrySet().stream()
                            .filter(entry -> featureName.equals(entry.getValue())).findFirst().map(Map.Entry::getKey));
        }
        return Optional.empty();
    }

    protected List<Integer> getEnumerationValues(int enumTypeUid) {
        var applianceDescription = this.applianceDescription;

        if (applianceDescription != null) {
            return applianceDescription.deviceDescription().enumerationTypeList.stream()
                    .filter(enumerationType -> enumerationType.id == enumTypeUid)
                    .map(enumerationType -> enumerationType.values).flatMap(List::stream).collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    protected Optional<Integer> getEnumerationParent(int enumTypeUid) {
        var applianceDescription = this.applianceDescription;

        if (applianceDescription != null) {
            return applianceDescription.deviceDescription().enumerationTypeList.stream()
                    .filter(enumerationType -> enumerationType.id == enumTypeUid)
                    .map(enumerationType -> enumerationType.subsetOf).filter(Objects::nonNull).findFirst();
        }
        return Optional.empty();
    }

    /**
     * Map Home Connect key and value names to label.
     * e.g. Dishcare.Dishwasher.Program.Eco50 --> Eco50 or BSH.Common.EnumType.OperationState.DelayedStart --> Delayed
     * Start
     *
     * @param type type
     * @return human readable label
     */
    protected String mapStringType(String type) {
        int index = type.lastIndexOf(".");
        if (index > 0) {
            String sub = type.substring(index + 1);
            StringBuilder sb = new StringBuilder();
            for (String word : sub.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")) {
                sb.append(" ");
                sb.append(word);
            }
            return sb.toString().trim();
        }
        return type;
    }

    private String generateNonce() {
        byte[] codeVerifier = new byte[32];
        secureRandom.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }

    private synchronized void scheduleReconnect() {
        var reconnectFuture = this.reconnectFuture;

        if ((reconnectFuture == null || reconnectFuture.isCancelled() || reconnectFuture.isDone())
                && !disposeInitialized.get()) {
            var configuration = this.configuration;
            int delay = 1;
            if (configuration != null) {
                delay = configuration.connectionRetryDelay;
            }
            logger.trace("Schedule reconnect in {} minute(s) ({}).", delay, thing.getUID());
            this.reconnectFuture = scheduler.schedule(this::initialize, delay, TimeUnit.MINUTES);
        }
    }

    private synchronized void stopReconnectSchedule() {
        ScheduledFuture<?> reconnectFuture = this.reconnectFuture;
        if (reconnectFuture != null) {
            reconnectFuture.cancel(true);
        }
    }

    private ApplianceMessage mapApplianceMessage(Message<Object> message, String rawMessage, boolean incoming) {
        List<Event> eventDataList = new ArrayList<>();
        List<DescriptionChangeEvent> descriptionChangeEventList = new ArrayList<>();
        if (NOTIFY.equals(message.action()) || RESPONSE.equals(message.action())) {
            if (RO_VALUES.equals(message.resource()) || RO_ALL_MANDATORY_VALUES.equals(message.resource())) {
                Message<Data> dataMessage = Objects
                        .requireNonNull(gson.fromJson(rawMessage, new TypeToken<Message<Data>>() {
                        }.getType()));
                var dataList = dataMessage.data();
                if (dataList != null) {
                    dataList.forEach(d -> {
                        var name = mapFeatureUid(d.uid());
                        var value = SELECTED_PROGRAM.equals(name) || ACTIVE_PROGRAM.equals(name)
                                ? mapProgramUid(d.value())
                                : mapFeatureValue(d.uid(), d.value());
                        var event = new Event(name, value);
                        eventDataList.add(event);
                    });
                }
            } else if (RO_DESCRIPTION_CHANGE.equals(message.resource())
                    || RO_ALL_DESCRIPTION_CHANGES.equals(message.resource())) {
                Message<DescriptionChangeData> dataMessage = Objects
                        .requireNonNull(gson.fromJson(rawMessage, new TypeToken<Message<DescriptionChangeData>>() {
                        }.getType()));
                var descriptionChangedataList = dataMessage.data();
                if (descriptionChangedataList != null) {
                    descriptionChangedataList.forEach(d -> {
                        var name = mapFeatureUid(d.uid());
                        var event = new DescriptionChangeEvent(name, d.uid(), d.parentUid(), d.available(), d.access(),
                                d.min(), d.max(), d.stepSize(), d.defaultValue(), d.enumType());
                        descriptionChangeEventList.add(event);
                    });
                }
            }
        }

        return new ApplianceMessage(ZonedDateTime.now(), message.messageId(),
                incoming ? MessageType.INCOMING : MessageType.OUTGOING, message.resource(), message.version(),
                message.action(), message.code(), message, eventDataList, descriptionChangeEventList);
    }

    private boolean isConscryptSupported(@Nullable String osName, @Nullable String osArch) {
        if (osName == null || osArch == null) {
            return false;
        }

        if (containsIgnoreCase(osName, "win") && osArch.contains("x86")) {
            return true;
        } else if ((containsIgnoreCase(osName, "nix") || containsIgnoreCase(osName, "nux")
                || containsIgnoreCase(osName, "aix")) && is64BitSupported(osArch)) {
            return true;
        } else if ((containsIgnoreCase(osName, "mac") || containsIgnoreCase(osName, "darwin"))
                && is64BitSupported(osArch)) {
            return true;
        } else {
            return false;
        }
    }

    private boolean is64BitSupported(String osArch) {
        return containsIgnoreCase(osArch, "amd64") || containsIgnoreCase(osArch, "x86_64");
    }

    private void updateSelectedProgramDescription() {
        getLinkedChannel(CHANNEL_SELECTED_PROGRAM).ifPresent(channel -> {
            var programOptions = programMap.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey)
                    .map(programName -> new StateOption(programName, mapStringType(programName))).toList();

            descriptionProvider.setStateOptions(channel.getUID(), programOptions);
        });
    }

    private @Nullable Integer getEnumerationUid(int uid) {
        var applianceDescription = this.applianceDescription;

        if (applianceDescription != null) {
            var deviceDescription = applianceDescription.deviceDescription();
            return deviceDescription.settingList.stream().filter(setting -> setting.uid == uid)
                    .filter(setting -> setting.enumerationType != null).map(setting -> setting.enumerationType)
                    .findFirst()
                    .or(() -> deviceDescription.statusList.stream().filter(status -> status.uid == uid)
                            .filter(status -> status.enumerationType != null).findFirst()
                            .map(setting -> setting.enumerationType))
                    .or(() -> deviceDescription.eventList.stream().filter(event -> event.uid == uid)
                            .filter(event -> event.enumerationType != null).findFirst()
                            .map(event -> event.enumerationType))
                    .or(() -> deviceDescription.optionList.stream().filter(option -> option.uid == uid)
                            .filter(option -> option.enumerationType != null).findFirst()
                            .map(option -> option.enumerationType))
                    .orElse(null);
        }

        return null;
    }

    private String mapFeatureUid(int uid) {
        var applianceDescription = this.applianceDescription;

        if (applianceDescription != null) {
            if (applianceDescription.featureMapping().featureMap.containsKey(uid)) {
                var name = applianceDescription.featureMapping().featureMap.get(uid);
                if (name != null) {
                    return name;
                }
            } else if (applianceDescription.featureMapping().errorMap.containsKey(uid)) {
                var name = applianceDescription.featureMapping().errorMap.get(uid);
                if (name != null) {
                    return name;
                }
            }
        }

        return "" + uid;
    }

    private @Nullable Integer mapObjectToInteger(Object object) {
        Integer integerValue = null;

        if (object instanceof Integer intValue) {
            integerValue = intValue;
        } else if (object instanceof Long longValue) {
            integerValue = longValue.intValue();
        } else if (object instanceof Float floatValue && floatValue % 1 == 0) {
            integerValue = floatValue.intValue();
        } else if (object instanceof Double doubleValue && doubleValue % 1 == 0) {
            integerValue = doubleValue.intValue();
        }

        return integerValue;
    }

    private boolean isValidJson(String json) {
        try {
            JsonParser.parseString(json);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }
}
