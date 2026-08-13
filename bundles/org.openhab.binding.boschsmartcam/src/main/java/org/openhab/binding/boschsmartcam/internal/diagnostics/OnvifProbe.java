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
