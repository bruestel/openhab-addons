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
package org.openhab.binding.boschsmartcam.internal.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests the address ranges the discovery probes.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class BoschSmartCamDiscoveryServiceTest {

    @Test
    public void hostsOfSlash24LeaveOutNetworkAndBroadcast() throws Exception {
        List<InetAddress> hosts = BoschSmartCamDiscoveryService
                .hostsOf(InetAddress.getByName("192.168.1.244").getAddress(), 24);

        assertEquals(254, hosts.size());
        assertEquals("192.168.1.1", hosts.getFirst().getHostAddress());
        assertEquals("192.168.1.254", hosts.getLast().getHostAddress());
    }

    @Test
    public void hostsOfSlash22StartAtTheNetworkBoundary() throws Exception {
        List<InetAddress> hosts = BoschSmartCamDiscoveryService.hostsOf(InetAddress.getByName("10.0.3.4").getAddress(),
                22);

        assertEquals(1022, hosts.size());
        assertEquals("10.0.0.1", hosts.getFirst().getHostAddress());
        assertEquals("10.0.3.254", hosts.getLast().getHostAddress());
    }

    @Test
    public void hostsOfWorkAboveTheSignBit() throws Exception {
        List<InetAddress> hosts = BoschSmartCamDiscoveryService
                .hostsOf(InetAddress.getByName("192.168.178.20").getAddress(), 24);

        assertEquals(254, hosts.size());
        assertEquals("192.168.178.1", hosts.getFirst().getHostAddress());
    }
}
