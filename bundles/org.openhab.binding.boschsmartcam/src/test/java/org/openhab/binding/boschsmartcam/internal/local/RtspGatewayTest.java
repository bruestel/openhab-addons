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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the RTSP gateway against a simulated camera that asks for Digest authentication like the real ones.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class RtspGatewayTest {

    private static final String TOKEN = "00000000-0000-4000-8000-000000000001";
    private static final String USER = "localuser";
    private static final String PASSWORD = "secret";
    private static final String REALM = "Please log in with a valid username";
    private static final String NONCE = "f19444ccab755677f0f78555077e56d0";
    private static final byte[] MEDIA = { '$', 0, 0, 4, 1, 2, 3, 4 };
    private static final String SESSION = "8E1F2A3B";

    private static final char[] KEYSTORE_PASSWORD = "testpass".toCharArray();

    private final List<RtspMessage> seenByCamera = new CopyOnWriteArrayList<>();
    private final CompletableFuture<RtspMessage> teardownSeen = new CompletableFuture<>();
    // whether the camera offers its stream plain and over TLS, as if those channels were linked
    private volatile boolean plainOffered = true;
    private volatile boolean tlsOffered = true;
    private @Nullable ServerSocket camera;
    private @Nullable RtspGateway gateway;

    @BeforeEach
    public void setUp() throws Exception {
        ServerSocket fakeCamera = new ServerSocket(0);
        camera = fakeCamera;
        Thread.ofVirtual().start(() -> serveCamera(fakeCamera));
        RtspGateway rtspGateway = new RtspGateway(0,
                (token, secure) -> TOKEN.equals(token) && (secure ? tlsOffered : plainOffered)
                        ? new RtspGateway.Target("cam", USER, PASSWORD, false, a -> true)
                        : null,
                target -> new Socket("127.0.0.1", fakeCamera.getLocalPort()), serverTls());
        rtspGateway.start();
        gateway = rtspGateway;
    }

    @AfterEach
    public void tearDown() throws IOException {
        RtspGateway rtspGateway = gateway;
        if (rtspGateway != null) {
            rtspGateway.stop();
        }
        ServerSocket fakeCamera = camera;
        if (fakeCamera != null) {
            fakeCamera.close();
        }
    }

    @Test
    public void playerWithoutPasswordIsLoggedInByTheGateway() throws IOException {
        try (Socket player = connect()) {
            InputStream in = new BufferedInputStream(player.getInputStream());
            OutputStream out = player.getOutputStream();

            // the player sends a login of its own, which must not reach the camera
            send(out, "DESCRIBE " + playerUrl() + " RTSP/1.0\r\nCSeq: 2\r\nAuthorization: Basic Zm9vOmJhcg==\r\n\r\n");
            RtspMessage describe = RtspMessage.read(in, in.read());
            assertEquals(200, describe.status());
            assertEquals("2", describe.header("CSeq"));

            send(out, "PLAY " + playerUrl() + " RTSP/1.0\r\nCSeq: 3\r\n\r\n");
            RtspMessage play = RtspMessage.read(in, in.read());
            assertEquals(200, play.status());
            assertArrayEquals(MEDIA, in.readNBytes(MEDIA.length));
        }

        // first try without login, refused; then the gateway's own, accepted - for DESCRIBE only
        assertEquals(3, seenByCamera.size());
        RtspMessage first = seenByCamera.get(0);
        assertNull(first.header("Authorization"));
        assertEquals("rtsp://cam:9554/rtsp_tunnel?line=1&inst=1&enableaudio=1", first.uri());
        assertTrue(validDigest(seenByCamera.get(1)));
        assertTrue(validDigest(seenByCamera.get(2)));
    }

    @Test
    public void sessionThePlayerLeftOpenIsEndedAtTheCamera() throws Exception {
        try (Socket player = connect()) {
            InputStream in = new BufferedInputStream(player.getInputStream());
            send(player.getOutputStream(), "SETUP " + playerUrl() + " RTSP/1.0\r\nCSeq: 4\r\n"
                    + "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n\r\n");
            assertEquals(200, RtspMessage.read(in, in.read()).status());
            // the player goes away without a TEARDOWN
        }

        // bounded generously for slow build machines; it is done as soon as the camera sees the TEARDOWN
        RtspMessage teardown = teardownSeen.get(10, TimeUnit.SECONDS);
        assertEquals(SESSION, teardown.header("Session"));
        assertEquals("5", teardown.header("CSeq"));
        assertTrue(validDigest(teardown));
    }

    @Test
    public void unknownTokenIsRefused() throws IOException {
        try (Socket player = connect()) {
            InputStream in = new BufferedInputStream(player.getInputStream());
            send(player.getOutputStream(), "DESCRIBE rtsp://127.0.0.1/wrong RTSP/1.0\r\nCSeq: 1\r\n\r\n");
            assertEquals(404, RtspMessage.read(in, in.read()).status());
        }
        assertTrue(seenByCamera.isEmpty());
    }

    @Test
    public void playerOverTlsIsServedOnTheSamePort() throws Exception {
        try (Socket player = connectOverTls()) {
            InputStream in = new BufferedInputStream(player.getInputStream());
            send(player.getOutputStream(),
                    "DESCRIBE " + playerUrl().replace("rtsp:", "rtsps:") + " RTSP/1.0\r\nCSeq: 2\r\n\r\n");
            RtspMessage describe = RtspMessage.read(in, in.read());
            assertEquals(200, describe.status());
        }
        assertEquals("rtsp://cam:9554/rtsp_tunnel?line=1&inst=1&enableaudio=1", seenByCamera.get(0).uri());
    }

    @Test
    public void streamIsOnlyOfferedTheWayItIsLinked() throws Exception {
        plainOffered = false;
        try (Socket player = connect()) {
            InputStream in = new BufferedInputStream(player.getInputStream());
            send(player.getOutputStream(), "DESCRIBE " + playerUrl() + " RTSP/1.0\r\nCSeq: 1\r\n\r\n");
            assertEquals(404, RtspMessage.read(in, in.read()).status());
        }
        plainOffered = true;
        tlsOffered = false;
        try (Socket player = connectOverTls()) {
            InputStream in = new BufferedInputStream(player.getInputStream());
            send(player.getOutputStream(),
                    "DESCRIBE " + playerUrl().replace("rtsp:", "rtsps:") + " RTSP/1.0\r\nCSeq: 1\r\n\r\n");
            assertEquals(404, RtspMessage.read(in, in.read()).status());
        }
        assertTrue(seenByCamera.isEmpty());
    }

    @Test
    public void streamsOfOneCameraAreLimited() throws Exception {
        List<Socket> players = new ArrayList<>();
        try {
            for (int i = 0; i < RtspGateway.MAX_STREAMS_PER_CAMERA; i++) {
                Socket player = connect();
                players.add(player);
                assertEquals(200, describe(player));
            }
            try (Socket onTooMany = connect()) {
                assertEquals(453, describe(onTooMany));
            }

            players.removeFirst().close();
            // the gateway notices the closed player on its own thread, bounded generously for slow machines
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            int status;
            do {
                Socket player = connect();
                players.add(player);
                status = describe(player);
            } while (status == 453 && System.nanoTime() < deadline);
            assertEquals(200, status);
        } finally {
            for (Socket player : players) {
                player.close();
            }
        }
    }

    @Test
    public void addressesAreMappedOntoTheCamera() {
        String base = "rtsp://openhab:8554/" + TOKEN;
        assertEquals("rtsp://cam:9554/rtsp_tunnel?line=1&inst=1&enableaudio=1",
                RtspGateway.toCamera(base, base, "cam"));
        assertEquals("rtsp://cam:9554/rtsp_tunnel?line=1&inst=2&enableaudio=0",
                RtspGateway.toCamera(base + "?line=1&inst=2&enableaudio=0", base, "cam"));
        // controls the camera handed out stay as they are
        String control = "rtsp://cam:9554/rtsp_tunnel?line=1&inst=1&enableaudio=1&stream=video";
        assertEquals(control, RtspGateway.toCamera(control, base, "cam"));
    }

    @Test
    public void tokenIsTheFirstPathSegment() {
        assertEquals(TOKEN, RtspGateway.token("rtsp://openhab:8554/" + TOKEN));
        assertEquals(TOKEN, RtspGateway.token("rtsp://openhab:8554/" + TOKEN + "?inst=2"));
        assertEquals(TOKEN, RtspGateway.token("rtsp://openhab:8554/" + TOKEN + "/trackID=1"));
        assertNull(RtspGateway.token("rtsp://openhab:8554/"));
        assertNull(RtspGateway.token("*"));
    }

    private Socket connect() throws IOException {
        RtspGateway rtspGateway = gateway;
        assertNotNull(rtspGateway);
        Socket socket = new Socket("127.0.0.1", rtspGateway.getLocalPort());
        socket.setSoTimeout(5000);
        return socket;
    }

    private Socket connectOverTls() throws Exception {
        RtspGateway rtspGateway = gateway;
        assertNotNull(rtspGateway);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] { new TrustAll() }, null);
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket("127.0.0.1", rtspGateway.getLocalPort());
        socket.setSoTimeout(5000);
        socket.startHandshake();
        return socket;
    }

    private static SSLContext serverTls() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = RtspGatewayTest.class.getResourceAsStream("/tls/test-keystore.p12")) {
            keyStore.load(in, KEYSTORE_PASSWORD);
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, KEYSTORE_PASSWORD);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), null, null);
        return context;
    }

    /**
     * The test certificate is self-signed, as the one openHAB generates.
     */
    private static class TrustAll implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate @Nullable [] chain, @Nullable String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate @Nullable [] chain, @Nullable String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    private int describe(Socket player) throws IOException {
        InputStream in = new BufferedInputStream(player.getInputStream());
        send(player.getOutputStream(), "DESCRIBE " + playerUrl() + " RTSP/1.0\r\nCSeq: 2\r\n\r\n");
        return RtspMessage.read(in, in.read()).status();
    }

    private String playerUrl() {
        RtspGateway rtspGateway = gateway;
        return "rtsp://127.0.0.1:" + (rtspGateway == null ? 0 : rtspGateway.getLocalPort()) + "/" + TOKEN;
    }

    private static void send(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static boolean validDigest(RtspMessage request) {
        String header = request.header("Authorization");
        if (header == null) {
            return false;
        }
        String ha1 = RtspDigest.md5(USER + ":" + REALM + ":" + PASSWORD);
        String ha2 = RtspDigest.md5(request.method() + ":" + request.uri());
        return header.contains("response=\"" + RtspDigest.md5(ha1 + ":" + NONCE + ":" + ha2) + "\"")
                && header.contains("uri=\"" + request.uri() + "\"");
    }

    /**
     * Answers like a camera: 401 without a valid Digest login, otherwise 200, and media after PLAY.
     */
    private void serveCamera(ServerSocket server) {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                Thread.ofVirtual().start(() -> serveConnection(socket));
            } catch (IOException e) {
                // the test closed the camera
            }
        }
    }

    private void serveConnection(Socket connection) {
        try (Socket socket = connection) {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            while (true) {
                int first = in.read();
                if (first < 0) {
                    return;
                }
                RtspMessage request = RtspMessage.read(in, first);
                seenByCamera.add(request);
                if ("TEARDOWN".equals(request.method()) && validDigest(request)) {
                    teardownSeen.complete(request);
                }
                String cseq = request.header("CSeq");
                if (!validDigest(request)) {
                    send(out, "RTSP/1.0 401 Unauthorized\r\nCSeq: " + cseq + "\r\nWWW-Authenticate: Digest realm=\""
                            + REALM + "\",nonce=\"" + NONCE + "\",opaque=\"\",stale=FALSE,algorithm=MD5\r\n\r\n");
                    continue;
                }
                String session = "SETUP".equals(request.method()) ? "Session: " + SESSION + ";timeout=60\r\n" : "";
                send(out, "RTSP/1.0 200 OK\r\nCSeq: " + cseq + "\r\n" + session + "\r\n");
                if ("PLAY".equals(request.method())) {
                    out.write(MEDIA);
                    out.flush();
                }
            }
        } catch (IOException e) {
            // the test closed the connection
        }
    }
}
