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
package org.openhab.binding.boschsmartcam.internal.diagnostics;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Read only look at what a camera says about ONVIF.
 *
 * Bosch offers an {@code onvif_user} endpoint and the cameras list ONVIF among their network services, but whether
 * the event service is actually reachable is unknown. This collects the few things that can be asked without
 * changing anything, so the question can be answered with data instead of guesses.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class OnvifProbe {

    /**
     * RCP+ command for the list of enabled network services.
     */
    public static final String RCP_NETWORK_SERVICES = "0x0c62";

    /**
     * RCP+ command for the ONVIF scopes the camera announces.
     */
    public static final String RCP_ONVIF_SCOPES = "0x0a98";

    /**
     * Where an ONVIF device usually answers.
     */
    public static final String DEVICE_SERVICE_PATH = "/onvif/device_service";

    /**
     * Where the event service lives according to {@code GetServices}. The device service answers some event calls as
     * well, but not all of them.
     */
    public static final String EVENT_SERVICE_PATH = "/onvif/event_service";

    public static final String SOAP_CONTENT_TYPE = "application/soap+xml; charset=utf-8";

    /**
     * The one ONVIF call a device has to answer without any authentication, which makes it the only thing that can be
     * tried while creating a user is not possible.
     */
    public static final String GET_SYSTEM_DATE_AND_TIME = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
              <s:Body xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
                <tds:GetSystemDateAndTime/>
              </s:Body>
            </s:Envelope>
            """;

    /**
     * Needs authorization, unlike {@link #GET_SYSTEM_DATE_AND_TIME}. Tells whether the credentials the cloud hands
     * out are enough for ONVIF, and which services the camera offers.
     */
    public static final String GET_SERVICES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
              <s:Body xmlns:tds="http://www.onvif.org/ver10/device/wsdl">
                <tds:GetServices><tds:IncludeCapability>true</tds:IncludeCapability></tds:GetServices>
              </s:Body>
            </s:Envelope>
            """;

    /**
     * The topics a camera can raise events for - the whole point of the exercise.
     */
    public static final String GET_EVENT_PROPERTIES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
              <s:Body xmlns:tev="http://www.onvif.org/ver10/events/wsdl">
                <tev:GetEventProperties/>
              </s:Body>
            </s:Envelope>
            """;

    /**
     * Lists the MQTT brokers the camera is already configured to publish to. Read only - tells whether the mechanism
     * is unused or whether Bosch itself makes use of it.
     */
    public static final String GET_EVENT_BROKERS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
              <s:Body xmlns:tev="http://www.onvif.org/ver10/events/wsdl">
                <tev:GetEventBrokers/>
              </s:Body>
            </s:Envelope>
            """;

    /**
     * Registers an MQTT broker the camera publishes its events to. The only writing call in here - it changes the
     * configuration of the camera, and {@link #deleteEventBroker(String)} undoes it.
     *
     * The publish filter is left out on purpose for now: it is optional, and leaving it out is the least assuming
     * first attempt. If the camera then publishes nothing, a filter is the next thing to try.
     */
    public static String setEventBroker(String address, String user, String password, String topicPrefix) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                            xmlns:tt="http://www.onvif.org/ver10/schema">
                  <s:Body xmlns:tev="http://www.onvif.org/ver10/events/wsdl">
                    <tev:SetEventBroker>
                      <tev:EventBroker>
                        <tt:Address>%s</tt:Address>
                        <tt:TopicPrefix>%s</tt:TopicPrefix>
                        <tt:UserName>%s</tt:UserName>
                        <tt:Password>%s</tt:Password>
                      </tev:EventBroker>
                    </tev:SetEventBroker>
                  </s:Body>
                </s:Envelope>
                """.formatted(escape(address), escape(topicPrefix), escape(user), escape(password));
    }

    public static String deleteEventBroker(String address) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope">
                  <s:Body xmlns:tev="http://www.onvif.org/ver10/events/wsdl">
                    <tev:DeleteEventBroker><tev:Address>%s</tev:Address></tev:DeleteEventBroker>
                  </s:Body>
                </s:Envelope>
                """.formatted(escape(address));
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /**
     * WS-BaseNotification: asks the camera to push its events to a URL of ours. The last candidate for local events
     * after the event broker turned out to be a stub - a subscription expires by itself, so it is a smaller step than
     * a stored broker configuration.
     *
     * @param consumerUrl where the camera should post the notifications to
     * @param minutes how long the subscription should last before it has to be renewed
     */
    public static String subscribe(String consumerUrl, int minutes) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                            xmlns:wsa="http://www.w3.org/2005/08/addressing">
                  <s:Body xmlns:wsnt="http://docs.oasis-open.org/wsn/b-2">
                    <wsnt:Subscribe>
                      <wsnt:ConsumerReference>
                        <wsa:Address>%s</wsa:Address>
                      </wsnt:ConsumerReference>
                      <wsnt:InitialTerminationTime>PT%dM</wsnt:InitialTerminationTime>
                    </wsnt:Subscribe>
                  </s:Body>
                </s:Envelope>
                """.formatted(escape(consumerUrl), minutes);
    }

    private final Map<String, String> results = new LinkedHashMap<>();

    /**
     * @param name what was asked
     * @param value what came back, or the reason it did not
     */
    public void add(String name, String value) {
        results.put(name, value);
    }

    public void addFailure(String name, Exception e) {
        results.put(name, "failed: " + e.getMessage());
    }

    /**
     * @param command RCP+ command to read
     * @return path and query for a read of that command over {@code /rcp.xml}
     */
    public static String rcpReadPath(String command) {
        return "/rcp.xml?command=" + command + "&type=P_OCTET&direction=READ&num=1&payload=";
    }

    /**
     * RCP+ answers carry their payload as space separated hex. Printable characters are shown next to it, since the
     * interesting parts - service names, scopes - are text.
     */
    public static String describeRcp(byte[] answer) {
        String text = new String(answer, StandardCharsets.UTF_8);
        StringBuilder readable = new StringBuilder();
        for (char c : text.toCharArray()) {
            readable.append(c >= 0x20 && c < 0x7f ? c : '.');
        }
        return text.length() + " bytes\n" + readable;
    }

    @Override
    public String toString() {
        StringBuilder report = new StringBuilder("ONVIF probe\n===========\n\n");
        results.forEach((name, value) -> report.append(name).append('\n').append("-".repeat(name.length())).append('\n')
                .append(value).append("\n\n"));
        return report.toString();
    }
}
