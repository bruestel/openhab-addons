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

import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.TLS;
import static org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants.X_509;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CRL;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.util.ssl.SslContextFactory;

/**
 * Trust in the cameras themselves. Every camera presents a certificate issued below the root Bosch publishes for its
 * local API, together with the complete chain, so that root is the only trust anchor needed.
 *
 * The name in such a certificate is not the host name but the MAC address of the camera, so the usual host name
 * verification cannot work. Instead, once a camera thing knows which camera it talks to, it binds the host to that
 * MAC address, and every later connection to the host has to present exactly that camera.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class CameraTrust {

    /**
     * {@code RSA Root CA G1 Prod} of Robert Bosch Smart Home GmbH, as published in the best practice section of the
     * local API documentation.
     */
    private static final String ROOT_RESOURCE = "/cert/bosch-device-root-ca.pem";

    private static final int CONNECT_TIMEOUT_MILLIS = 3000;

    private final KeyStore trustStore;
    private final X509TrustManager rootTrustManager;
    private final SSLContext sslContext;
    private final SSLContext bindingContext;
    private final SSLContext trustAllBindingContext;
    private final SSLContext trustAllContext;

    /**
     * MAC address each bound host has to present, in the form {@code 64:da:a0:12:34:56}.
     */
    private final Map<String, String> macAddressByHost = new ConcurrentHashMap<>();

    public CameraTrust() {
        try (InputStream rootStream = CameraTrust.class.getResourceAsStream(ROOT_RESOURCE)) {
            if (rootStream == null) {
                throw new IllegalStateException("Bundled certificate " + ROOT_RESOURCE + " not found");
            }
            Certificate root = CertificateFactory.getInstance(X_509).generateCertificate(rootStream);
            trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry(ROOT_RESOURCE, root);

            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trustStore);
            rootTrustManager = Arrays.stream(factory.getTrustManagers()).filter(X509TrustManager.class::isInstance)
                    .map(X509TrustManager.class::cast).findFirst()
                    .orElseThrow(() -> new GeneralSecurityException("No X509TrustManager available"));
            // reading the identity must work for any camera, that is how a camera becomes known in the first place
            sslContext = createContext(new CameraTrustManager(true, false));
            bindingContext = createContext(new CameraTrustManager(true, true));
            trustAllContext = createContext(new CameraTrustManager(false, false));
            // trusting any certificate skips the chain, not the camera a host is bound to
            trustAllBindingContext = createContext(new CameraTrustManager(false, true));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not set up the trust in the cameras", e);
        }
    }

    private static SSLContext createContext(TrustManager trustManager) throws GeneralSecurityException {
        SSLContext context = SSLContext.getInstance(TLS);
        context.init(null, new TrustManager[] { trustManager }, null);
        return context;
    }

    /**
     * @return the TLS configuration for an HTTP client that talks to cameras only. It accepts a camera below the
     *         Bosch root, and for a bound host only the camera with the bound MAC address.
     */
    public SslContextFactory.Client createSslContextFactory() {
        return createSslContextFactory(new CameraTrustManager(true, true));
    }

    private SslContextFactory.Client createSslContextFactory(X509ExtendedTrustManager trustManager) {
        SslContextFactory.Client factory = new SslContextFactory.Client() {
            // the signature is Jetty's, without null annotations
            @Override
            @NonNullByDefault({})
            protected TrustManager[] getTrustManagers(KeyStore store, Collection<? extends CRL> crls) {
                return new TrustManager[] { trustManager };
            }
        };
        factory.setTrustStore(trustStore);
        // the host name is not checked by the JDK: an X509ExtendedTrustManager identifies the peer itself, here by the
        // MAC address in its certificate
        return factory;
    }

    /**
     * Requires every later connection to {@code host} to present the camera with that MAC address.
     *
     * @param host the host exactly as it is used in the URLs
     */
    public void bind(String host, String macAddress) {
        macAddressByHost.put(host, macAddress);
    }

    /**
     * Lets the host present any camera again, once no thing uses it any more.
     */
    public void release(String host) {
        macAddressByHost.remove(host);
    }

    /**
     * @return the trust manager the HTTP clients use, for tests
     */
    X509ExtendedTrustManager createBindingTrustManager() {
        return new CameraTrustManager(true, true);
    }

    /**
     * @return the trust manager of the clients that trust any certificate, for tests
     */
    X509ExtendedTrustManager createTrustAllBindingTrustManager() {
        return new CameraTrustManager(false, true);
    }

    /**
     * @return the TLS configuration for a client that accepts a certificate below any root. Only meant as a way out
     *         should Bosch ever change the root its cameras chain up to; a bound host still has to present its camera.
     */
    public SslContextFactory.Client createTrustAllSslContextFactory() {
        return createSslContextFactory(new CameraTrustManager(false, true));
    }

    /**
     * Opens a TLS connection to a camera for a protocol the HTTP client does not speak, such as RTSP. The same rules
     * apply as for the HTTP client: below the Bosch root, and for a bound host only its camera.
     *
     * @param trustAll whether to accept a certificate below any root; a bound host still has to present its camera
     */
    public SSLSocket openSocket(String host, int port, boolean trustAll, int timeoutMillis) throws IOException {
        SSLContext context = trustAll ? trustAllBindingContext : bindingContext;
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            socket.startHandshake();
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /**
     * Connects to {@code host:port} and reads who answers. Unless {@code trustAll} is set, the handshake only
     * succeeds for a genuine Bosch camera, so this doubles as the check whether there is one at all.
     *
     * @param trustAll whether to skip verifying the certificate chain; the MAC address is read either way
     * @throws javax.net.ssl.SSLHandshakeException if the certificate does not chain up to the Bosch root
     * @throws IOException if nothing answers
     */
    public CameraIdentity readIdentity(String host, int port, boolean trustAll) throws IOException {
        SSLContext context = trustAll ? trustAllContext : sslContext;
        try (SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(CONNECT_TIMEOUT_MILLIS);
            socket.startHandshake();
            Certificate[] chain = socket.getSession().getPeerCertificates();
            if (chain.length == 0 || !(chain[0] instanceof X509Certificate leaf)) {
                throw new IOException(host + " presented no certificate");
            }
            return CameraIdentity.fromCertificate(leaf);
        }
    }

    /**
     * Verifies the chain against the Bosch root and, instead of the host name, the MAC address of a bound host. Being
     * an {@link X509ExtendedTrustManager}, the JDK leaves the identification of the peer entirely to it.
     */
    private class CameraTrustManager extends X509ExtendedTrustManager {

        private final boolean verifyChain;
        private final boolean checkBinding;

        /**
         * @param verifyChain whether the chain has to lead to the Bosch root; off when any certificate is trusted
         * @param checkBinding whether a bound host has to present the camera with its MAC address
         */
        CameraTrustManager(boolean verifyChain, boolean checkBinding) {
            this.verifyChain = verifyChain;
            this.checkBinding = checkBinding;
        }

        @Override
        public void checkServerTrusted(X509Certificate @Nullable [] chain, @Nullable String authType,
                @Nullable SSLEngine engine) throws CertificateException {
            checkServerTrusted(chain, authType, engine == null ? null : engine.getPeerHost());
        }

        @Override
        public void checkServerTrusted(X509Certificate @Nullable [] chain, @Nullable String authType,
                @Nullable Socket socket) throws CertificateException {
            String host = null;
            if (socket instanceof SSLSocket sslSocket && sslSocket.getHandshakeSession() != null) {
                host = sslSocket.getHandshakeSession().getPeerHost();
            }
            checkServerTrusted(chain, authType, host);
        }

        @Override
        public void checkServerTrusted(X509Certificate @Nullable [] chain, @Nullable String authType)
                throws CertificateException {
            checkServerTrusted(chain, authType, (String) null);
        }

        private void checkServerTrusted(X509Certificate @Nullable [] chain, @Nullable String authType,
                @Nullable String host) throws CertificateException {
            if (verifyChain) {
                rootTrustManager.checkServerTrusted(chain, authType);
            }
            String expected = host == null ? null : macAddressByHost.get(host);
            if (!checkBinding || expected == null || chain == null || chain.length == 0) {
                return;
            }
            String found;
            try {
                found = CameraIdentity.fromCertificate(chain[0]).macAddress();
            } catch (IOException e) {
                throw new CertificateException(e.getMessage(), e);
            }
            if (!expected.equals(found)) {
                throw new CertificateException(
                        "%s presented the camera %s instead of %s".formatted(host, found, expected));
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate @Nullable [] chain, @Nullable String authType,
                @Nullable Socket socket) throws CertificateException {
            throw new CertificateException("Cameras are only talked to as a client");
        }

        @Override
        public void checkClientTrusted(X509Certificate @Nullable [] chain, @Nullable String authType,
                @Nullable SSLEngine engine) throws CertificateException {
            throw new CertificateException("Cameras are only talked to as a client");
        }

        @Override
        public void checkClientTrusted(X509Certificate @Nullable [] chain, @Nullable String authType)
                throws CertificateException {
            throw new CertificateException("Cameras are only talked to as a client");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return rootTrustManager.getAcceptedIssuers();
        }
    }
}
