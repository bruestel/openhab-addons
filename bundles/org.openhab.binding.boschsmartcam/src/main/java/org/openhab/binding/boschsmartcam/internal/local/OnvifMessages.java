/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.boschsmartcam.internal.local;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The few SOAP messages of the ONVIF event service the binding needs, and reading their answers.
 *
 * The bodies are the ones measured against the cameras. A {@code Filter} in the subscription is refused with HTTP 400,
 * so the subscription takes everything and the binding sorts out what it needs.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public final class OnvifMessages {

    public static final String EVENT_SERVICE_PATH = "/onvif/event_service";
    public static final String DEVICE_SERVICE_PATH = "/onvif/device_service";
    public static final String CONTENT_TYPE = "application/soap+xml; charset=utf-8";

    /**
     * Prefix of every topic the cameras send.
     */
    public static final String TOPIC_PREFIX = "tns1:BoschSmartHome/";

    private static final String NS_SOAP = "http://www.w3.org/2003/05/soap-envelope";
    private static final String NS_EVENTS = "http://www.onvif.org/ver10/events/wsdl";
    private static final String NS_NOTIFICATION = "http://docs.oasis-open.org/wsn/b-2";
    private static final String NS_ADDRESSING = "http://www.w3.org/2005/08/addressing";
    private static final String NS_SCHEMA = "http://www.onvif.org/ver10/schema";
    private static final String NS_DEVICE = "http://www.onvif.org/ver10/device/wsdl";

    // elements and attributes read from the answers
    private static final String ADDRESS = "Address";
    private static final String MODEL = "Model";
    private static final String NOTIFICATION_MESSAGE = "NotificationMessage";
    private static final String TOPIC = "Topic";
    private static final String MESSAGE = "Message";
    private static final String DATA = "Data";
    private static final String SIMPLE_ITEM = "SimpleItem";
    private static final String NAME = "Name";
    private static final String VALUE = "Value";
    private static final String PROPERTY_OPERATION = "PropertyOperation";
    private static final String UTC_TIME = "UtcTime";

    /**
     * How long the camera holds a pull open without an event, and how many events one answer carries at most.
     */
    private static final int PULL_TIMEOUT_SECONDS = 30;
    private static final int MAX_MESSAGES_PER_PULL = 100;
    /**
     * How much of an unreadable answer is put into the message, enough to tell what came back.
     */
    private static final int MAX_LOGGED_ANSWER_LENGTH = 500;

    private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

    /**
     * The camera caps the lifetime at 60 seconds whatever is asked for, and every pull extends it again.
     */
    public static final String CREATE_PULL_POINT_SUBSCRIPTION = envelope("""
            <tev:CreatePullPointSubscription>\
            <tev:InitialTerminationTime>PT10M</tev:InitialTerminationTime>\
            </tev:CreatePullPointSubscription>""");

    /**
     * A real long poll: without an event the camera answers after the timeout with an empty response.
     */
    public static final String PULL_MESSAGES = envelope("""
            <tev:PullMessages><tev:Timeout>PT%dS</tev:Timeout>\
            <tev:MessageLimit>%d</tev:MessageLimit></tev:PullMessages>""".formatted(PULL_TIMEOUT_SECONDS,
            MAX_MESSAGES_PER_PULL));

    public static final String UNSUBSCRIBE = envelope("<wsnt:Unsubscribe/>");

    /**
     * Unlike the rest of the device service it needs the local user, but it names the model of the camera.
     */
    public static final String GET_DEVICE_INFORMATION = envelope("<GetDeviceInformation xmlns=\"" + NS_DEVICE + "\"/>");

    private OnvifMessages() {
    }

    private static String envelope(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><s:Envelope xmlns:s=\"" + NS_SOAP + "\" xmlns:tev=\""
                + NS_EVENTS + "\" xmlns:wsnt=\"" + NS_NOTIFICATION + "\"><s:Body>" + body + "</s:Body></s:Envelope>";
    }

    /**
     * @return the address of the new subscription, e.g. {@code https://192.168.0.42/Web_Service?Idx=3}
     */
    public static String parseSubscriptionAddress(byte[] response) throws IOException {
        NodeList addresses = parse(response).getElementsByTagNameNS(NS_ADDRESSING, ADDRESS);
        if (addresses.getLength() == 0) {
            throw new IOException("The answer carries no subscription address");
        }
        return addresses.item(0).getTextContent().strip();
    }

    /**
     * @return the {@code Model} of a {@code GetDeviceInformationResponse}, e.g. {@code HOME_Eyes_Outdoor}, or
     *         {@code null} if the answer carries none
     */
    public static @Nullable String parseModel(byte[] response) throws IOException {
        NodeList models = parse(response).getElementsByTagNameNS(NS_DEVICE, MODEL);
        if (models.getLength() == 0) {
            return null;
        }
        String model = models.item(0).getTextContent().strip();
        return model.isEmpty() ? null : model;
    }

    /**
     * Reads the notifications of a {@code PullMessagesResponse}. Topics outside {@link #TOPIC_PREFIX} are kept with
     * their full name - the list the camera announces is incomplete, so nothing is thrown away here.
     */
    public static List<OnvifEvent> parseNotifications(byte[] response) throws IOException {
        List<OnvifEvent> events = new ArrayList<>();
        NodeList messages = parse(response).getElementsByTagNameNS(NS_NOTIFICATION, NOTIFICATION_MESSAGE);
        for (int i = 0; i < messages.getLength(); i++) {
            Element notification = (Element) messages.item(i);
            Element topicElement = firstChild(notification, NS_NOTIFICATION, TOPIC);
            Element message = firstChild(notification, NS_SCHEMA, MESSAGE);
            if (topicElement == null || message == null) {
                continue;
            }
            String topic = topicElement.getTextContent().strip();
            if (topic.startsWith(TOPIC_PREFIX)) {
                topic = topic.substring(TOPIC_PREFIX.length());
            }
            Map<String, String> data = new HashMap<>();
            Element dataElement = firstChild(message, NS_SCHEMA, DATA);
            if (dataElement != null) {
                NodeList items = dataElement.getElementsByTagNameNS(NS_SCHEMA, SIMPLE_ITEM);
                for (int j = 0; j < items.getLength(); j++) {
                    Element item = (Element) items.item(j);
                    data.put(item.getAttribute(NAME), item.getAttribute(VALUE));
                }
            }
            String operation = message.getAttribute(PROPERTY_OPERATION);
            events.add(new OnvifEvent(topic, parseTime(message.getAttribute(UTC_TIME)),
                    operation.isBlank() ? null : operation, data));
        }
        return events;
    }

    private static @Nullable Instant parseTime(String value) {
        try {
            return value.isBlank() ? null : Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static @Nullable Element firstChild(Element parent, String namespace, String name) {
        NodeList children = parent.getElementsByTagNameNS(namespace, name);
        return children.getLength() == 0 ? null : (Element) children.item(0);
    }

    private static Document parse(byte[] response) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // the answer comes from the network, so nothing in it may reach out for more
            factory.setFeature(DISALLOW_DOCTYPE, true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(response));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IOException("Unreadable answer from the camera: " + new String(response, 0,
                    Math.min(response.length, MAX_LOGGED_ANSWER_LENGTH), StandardCharsets.UTF_8), e);
        }
    }
}
