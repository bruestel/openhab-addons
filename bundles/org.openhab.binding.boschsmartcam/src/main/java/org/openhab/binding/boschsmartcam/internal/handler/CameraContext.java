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
package org.openhab.binding.boschsmartcam.internal.handler;

import java.security.cert.X509Certificate;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.boschsmartcam.internal.net.CidrMatcher;

/**
 * What the binding provides to its cameras and may change while they run, through the binding configuration or the
 * RTSP gateway; a camera therefore asks for it each time instead of keeping it.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public interface CameraContext {

    /**
     * @return the client for the cameras configured to accept any certificate, created on first use
     */
    HttpClient getTrustAllHttpClient();

    /**
     * @return the networks that may fetch the snapshot, stream and event addresses
     */
    CidrMatcher getAllowedNetworks();

    /**
     * @return the port of the RTSP gateway, or 0 if it does not run
     */
    int getRtspGatewayPort();

    /**
     * @return the certificate the gateway presents for RTSPS, or {@code null} if it only offers plain RTSP
     */
    @Nullable
    X509Certificate getRtspGatewayCertificate();
}
