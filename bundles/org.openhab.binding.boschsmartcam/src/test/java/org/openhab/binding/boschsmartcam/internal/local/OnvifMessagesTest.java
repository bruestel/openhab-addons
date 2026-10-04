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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests reading the answers of the ONVIF event service, modelled on what the cameras sent.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class OnvifMessagesTest {

    private static final String NAMESPACES = """
            xmlns:s="http://www.w3.org/2003/05/soap-envelope" \
            xmlns:wsa5="http://www.w3.org/2005/08/addressing" \
            xmlns:wsnt="http://docs.oasis-open.org/wsn/b-2" \
            xmlns:tev="http://www.onvif.org/ver10/events/wsdl" \
            xmlns:tt="http://www.onvif.org/ver10/schema" \
            xmlns:tns1="http://www.onvif.org/ver10/topics\"""";

    @Test
    public void subscriptionAddressIsRead() throws IOException {
        String answer = "<s:Envelope " + NAMESPACES + "><s:Body><tev:CreatePullPointSubscriptionResponse>"
                + "<tev:SubscriptionReference><wsa5:Address>https://192.168.1.42/Web_Service?Idx=7</wsa5:Address>"
                + "</tev:SubscriptionReference></tev:CreatePullPointSubscriptionResponse></s:Body></s:Envelope>";

        assertEquals("https://192.168.1.42/Web_Service?Idx=7", OnvifMessages.parseSubscriptionAddress(bytes(answer)));
    }

    @Test
    public void propertiesAndEventsAreRead() throws IOException {
        String answer = "<s:Envelope " + NAMESPACES + "><s:Body><tev:PullMessagesResponse>"
                + notification("AlarmMode", "UtcTime=\"2026-09-30T08:28:17.249Z\" PropertyOperation=\"Initialized\"",
                        "",
                        "<tt:SimpleItem Name=\"State\" Value=\"intrusion_unarmed\"/>"
                                + "<tt:SimpleItem Name=\"Reason\" Value=\"INTERNAL_INIT\"/>")
                + notification("PersonDetected", "UtcTime=\"2026-09-30T08:45:28.100Z\"",
                        "<tt:Source><tt:SimpleItem Name=\"Source\" Value=\"1\"/></tt:Source>",
                        "<tt:SimpleItem Name=\"State\" Value=\"true\"/>"
                                + "<tt:SimpleItem Name=\"ClipId\" Value=\"10083\"/>"
                                + "<tt:SimpleItem Name=\"Starttime\" Value=\"\"/>")
                + "</tev:PullMessagesResponse></s:Body></s:Envelope>";

        List<OnvifEvent> events = OnvifMessages.parseNotifications(bytes(answer));

        assertEquals(2, events.size());
        OnvifEvent alarmMode = events.get(0);
        assertEquals("AlarmMode", alarmMode.topic());
        assertEquals("Initialized", alarmMode.propertyOperation());
        assertEquals("intrusion_unarmed", alarmMode.get("State"));
        assertEquals(Instant.parse("2026-09-30T08:28:17.249Z"), alarmMode.time());

        OnvifEvent person = events.get(1);
        assertEquals("PersonDetected", person.topic());
        assertNull(person.propertyOperation());
        assertTrue(person.isTrue("State"));
        assertEquals("10083", person.get("ClipId"));
        assertEquals("", person.get("Starttime"));
        // the source is not part of the data
        assertNull(person.get("Source"));
    }

    @Test
    public void emptyAnswerOfALongPollHasNoEvents() throws IOException {
        String answer = "<s:Envelope " + NAMESPACES + "><s:Body><tev:PullMessagesResponse>"
                + "<tev:CurrentTime>2026-09-30T08:46:00Z</tev:CurrentTime>"
                + "</tev:PullMessagesResponse></s:Body></s:Envelope>";

        assertTrue(OnvifMessages.parseNotifications(bytes(answer)).isEmpty());
    }

    @Test
    public void documentTypeDeclarationsAreRefused() {
        String answer = "<?xml version=\"1.0\"?><!DOCTYPE s [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<s:Envelope " + NAMESPACES + "><s:Body>&x;</s:Body></s:Envelope>";

        assertThrows(IOException.class, () -> OnvifMessages.parseNotifications(bytes(answer)));
    }

    private static String notification(String topic, String messageAttributes, String source, String data) {
        return "<wsnt:NotificationMessage><wsnt:Topic Dialect=\"http://www.onvif.org/ver10/tev/topicExpression/ConcreteSet\">"
                + "tns1:BoschSmartHome/" + topic + "</wsnt:Topic><wsnt:Message><tt:Message " + messageAttributes + ">"
                + source + "<tt:Data>" + data + "</tt:Data></tt:Message></wsnt:Message></wsnt:NotificationMessage>";
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
