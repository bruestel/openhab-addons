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
package org.openhab.binding.boschsmartcam.internal.api;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants;
import org.openhab.core.io.net.http.TlsTrustManagerProvider;
import org.osgi.service.component.annotations.Component;

/**
 * The Bosch cloud API is not served with a publicly trusted certificate but with one issued by an internal Bosch CA.
 * This provider makes openHAB trust that CA - and only that CA - for the API host, so certificate renewals keep
 * working while the connection stays verified. The Bosch app ships the same certificates and pins them the same way.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@Component(service = TlsTrustManagerProvider.class)
@NonNullByDefault
public class BoschSmartCamTlsTrustManagerProvider implements TlsTrustManagerProvider {

    /**
     * The root is what the API certificate ultimately chains up to and is valid until 2115, the issuing CA is added
     * as well so the chain still validates if Bosch ever stops sending the intermediate.
     */
    private static final String[] CERTIFICATE_RESOURCES = { "/cert/bosch-st-root-ca.pem",
            "/cert/bosch-video-ca-2a.pem" };

    private final X509ExtendedTrustManager trustManager;

    public BoschSmartCamTlsTrustManagerProvider() {
        trustManager = createTrustManager();
    }

    @Override
    public String getHostName() {
        return BoschSmartCamBindingConstants.API_HOST;
    }

    @Override
    public X509ExtendedTrustManager getTrustManager() {
        return trustManager;
    }

    private static X509ExtendedTrustManager createTrustManager() {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);

            for (String resource : CERTIFICATE_RESOURCES) {
                try (InputStream certificateStream = BoschSmartCamTlsTrustManagerProvider.class
                        .getResourceAsStream(resource)) {
                    if (certificateStream == null) {
                        throw new IllegalStateException("Bundled certificate " + resource + " not found");
                    }
                    X509Certificate certificate = (X509Certificate) certificateFactory
                            .generateCertificate(certificateStream);
                    keyStore.setCertificateEntry(resource, certificate);
                }
            }

            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore);
            for (TrustManager manager : factory.getTrustManagers()) {
                if (manager instanceof X509ExtendedTrustManager extendedTrustManager) {
                    return extendedTrustManager;
                }
            }
            throw new IllegalStateException("No X509ExtendedTrustManager available");
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not initialize trust manager for the Bosch cloud API", e);
        }
    }
}
