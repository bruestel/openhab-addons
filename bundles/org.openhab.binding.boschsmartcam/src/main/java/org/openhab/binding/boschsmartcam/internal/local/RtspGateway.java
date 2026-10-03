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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.BoschSmartCamBindingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Offers the streams of all cameras on one port of openHAB, without a password:
 * {@code rtsp://<openhab>:<port>/<token>} plays the camera the token belongs to. Given a certificate, the same port
 * also speaks TLS, {@code rtsps://<openhab>:<port>/<token>}; the first byte of a connection tells the two apart.
 *
 * The player speaks RTSP with openHAB, openHAB speaks RTSP over TLS with the camera. The requests of the player
 * are rewritten on the way: the address is replaced with the one of the camera, and openHAB logs in with the user
 * and password of the camera - whatever the player sends for that is dropped. When the camera asks for
 * authentication, its answer is caught and the request is sent again, logged in, so the player never notices.
 * Answers and media are passed on unchanged.
 *
 * Like the snapshot, a stream is guarded by the token in the address and by the networks a player has to be in.
 *
 * When a player goes away without ending its session, the gateway ends it at the camera, so the camera does not keep
 * streams nobody receives.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class RtspGateway {

    /**
     * The camera a token stands for. Each camera decides whether it offers its stream at all, plain or over TLS.
     *
     * @param allowedFrom whether a player at the given address may watch
     */
    public record Target(String host, String user, String password, boolean trustAll, Predicate<String> allowedFrom) {
    }

    @FunctionalInterface
    public interface TargetResolver {
        /**
         * @param secure whether the player came over TLS
         * @return the camera, or {@code null} if the token is unknown or the camera offers no stream that way
         */
        @Nullable
        Target resolve(String token, boolean secure);
    }

    @FunctionalInterface
    public interface CameraConnector {
        Socket connect(Target target) throws IOException;
    }

    /**
     * Path and query of the stream in full resolution with sound, used when the player asks for no other.
     */
    static final String CAMERA_PATH = "/rtsp_tunnel";
    static final String DEFAULT_QUERY = "?line=1&inst=1&enableaudio=1";

    private static final int MAX_SESSIONS = 16;
    private static final int TLS_HANDSHAKE = 0x16;
    private static final int HANDSHAKE_TIMEOUT_MILLIS = 10000;

    private final Logger logger = LoggerFactory.getLogger(RtspGateway.class);

    private final int port;
    private final TargetResolver resolver;
    private final CameraConnector connector;
    private final @Nullable SSLContext tls;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private volatile @Nullable ServerSocket serverSocket;

    /**
     * @param tls the certificate offered to players coming over TLS, {@code null} to offer plain RTSP only
     */
    public RtspGateway(int port, TargetResolver resolver, CameraConnector connector, @Nullable SSLContext tls) {
        this.port = port;
        this.resolver = resolver;
        this.connector = connector;
        this.tls = tls;
    }

    /**
     * @throws IOException if the port cannot be opened
     */
    public synchronized void start() throws IOException {
        if (serverSocket != null) {
            return;
        }
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port));
        serverSocket = server;
        Thread.ofVirtual().name("boschsmartcam-rtsp-gateway").start(() -> accept(server));
        logger.debug("Offering the camera streams on port {}", port);
    }

    public synchronized void stop() {
        ServerSocket server = serverSocket;
        serverSocket = null;
        if (server != null) {
            close(server);
        }
        sockets.forEach(RtspGateway::close);
        sockets.clear();
    }

    /**
     * @return the port actually listened on, of use when 0 was given
     */
    public int getLocalPort() {
        ServerSocket server = serverSocket;
        return server == null ? -1 : server.getLocalPort();
    }

    private void accept(ServerSocket server) {
        while (!server.isClosed()) {
            try {
                Socket player = server.accept();
                if (sockets.size() >= 2 * MAX_SESSIONS) {
                    logger.debug("Refused an RTSP connection from {}, too many are open",
                            player.getInetAddress().getHostAddress());
                    close(player);
                    continue;
                }
                Thread.ofVirtual().name("boschsmartcam-rtsp-" + player.getInetAddress().getHostAddress())
                        .start(() -> new Session(player).run());
            } catch (IOException e) {
                if (!server.isClosed()) {
                    logger.debug("Accepting on port {} failed: {}", port, e.getMessage());
                }
            }
        }
    }

    /**
     * @return the token in an address like {@code rtsp://host:port/<token>/...}, or {@code null} if there is none
     */
    static @Nullable String token(String uri) {
        int scheme = uri.indexOf("://");
        if (scheme < 0) {
            return null;
        }
        int slash = uri.indexOf('/', scheme + 3);
        if (slash < 0) {
            return null;
        }
        int end = slash + 1;
        while (end < uri.length() && uri.charAt(end) != '/' && uri.charAt(end) != '?') {
            end++;
        }
        String token = uri.substring(slash + 1, end);
        return token.isEmpty() ? null : token;
    }

    /**
     * Maps an address of the player onto the camera. The part behind the token is kept: a query replaces the default
     * one, so {@code ?line=1&inst=2&enableaudio=1} gives the small resolution. Addresses that do not start with
     * the base the player used, such as the absolute controls the camera hands out, stay as they are.
     *
     * @param base what the player used up to and including the token, e.g. {@code rtsp://openhab:8554/<token>}
     */
    static String toCamera(String uri, String base, String cameraHost) {
        if (!uri.regionMatches(true, 0, base, 0, base.length())) {
            return uri;
        }
        String rest = uri.substring(base.length());
        String camera = "rtsp://" + cameraHost + ":" + BoschSmartCamBindingConstants.RTSP_PORT + CAMERA_PATH;
        if (rest.startsWith("?")) {
            return camera + rest;
        }
        return camera + DEFAULT_QUERY + rest;
    }

    /**
     * One player and its connection to the camera.
     */
    private class Session {
        private final Socket player;
        // the player itself, or the TLS layered over it
        private volatile Socket connection;
        private final Map<String, RtspMessage> pending = new ConcurrentHashMap<>();
        private final Set<String> retried = ConcurrentHashMap.newKeySet();
        private final Object cameraLock = new Object();
        private final AtomicBoolean ended = new AtomicBoolean();
        private volatile @Nullable String session;
        private volatile boolean tornDown;
        private volatile int lastCseq;
        private String cameraUri = "";
        private @Nullable Socket camera;
        private @Nullable RtspDigest digest;
        private String base = "";
        private String cameraHost = "";

        Session(Socket player) {
            this.player = player;
            this.connection = player;
        }

        void run() {
            String address = player.getInetAddress().getHostAddress();
            try {
                // read unbuffered, so nothing beyond the first byte is taken from a TLS handshake
                int first = player.getInputStream().read();
                if (first < 0) {
                    return;
                }
                boolean secure = first == TLS_HANDSHAKE;
                if (secure) {
                    SSLContext context = tls;
                    if (context == null) {
                        logger.debug("Refused a TLS connection from {}, the gateway has no certificate", address);
                        return;
                    }
                    SSLSocket ssl = (SSLSocket) context.getSocketFactory().createSocket(player,
                            new ByteArrayInputStream(new byte[] { (byte) first }), true);
                    connection = ssl;
                    ssl.setSoTimeout(HANDSHAKE_TIMEOUT_MILLIS);
                    ssl.startHandshake();
                    ssl.setSoTimeout(0);
                }
                InputStream fromPlayer = new BufferedInputStream(connection.getInputStream());
                OutputStream toPlayer = connection.getOutputStream();
                if (secure) {
                    first = fromPlayer.read();
                    if (first < 0) {
                        return;
                    }
                }
                RtspMessage request = RtspMessage.read(fromPlayer, first);
                String token = token(request.uri());
                Target target = token == null ? null : resolver.resolve(token, secure);
                if (token == null || target == null || !target.allowedFrom().test(address)) {
                    // the same answer for both, so a wrong token cannot be told apart from a forbidden network
                    logger.debug("Refused an RTSP request from {}", address);
                    RtspMessage.response(404, "Not Found", request.header("CSeq")).write(toPlayer);
                    return;
                }
                base = request.uri().substring(0, request.uri().indexOf(token) + token.length());
                cameraHost = target.host();
                cameraUri = toCamera(request.uri(), base, cameraHost);
                digest = new RtspDigest(target.user(), target.password());
                Socket cameraSocket = connector.connect(target);
                cameraSocket.setSoTimeout(0);
                camera = cameraSocket;
                sockets.add(connection);
                sockets.add(cameraSocket);
                logger.debug("Relaying the stream of {} to {}", cameraHost, address);

                InputStream fromCamera = new BufferedInputStream(cameraSocket.getInputStream());
                Thread.ofVirtual().name("boschsmartcam-rtsp-camera-" + cameraHost)
                        .start(() -> cameraToPlayer(fromCamera, toPlayer));
                forward(request);
                playerToCamera(fromPlayer);
            } catch (IOException e) {
                logger.trace("RTSP session of {} ended: {}", address, e.getMessage());
            } finally {
                end();
            }
        }

        private void playerToCamera(InputStream fromPlayer) throws IOException {
            while (true) {
                int first = fromPlayer.read();
                if (first < 0) {
                    return;
                }
                if (first == '$') {
                    // RTCP of the player, interleaved like the media
                    byte[] header = fromPlayer.readNBytes(3);
                    if (header.length < 3) {
                        throw new EOFException("Interleaved frame cut off");
                    }
                    byte[] data = fromPlayer.readNBytes(((header[1] & 0xff) << 8) | (header[2] & 0xff));
                    synchronized (cameraLock) {
                        OutputStream out = cameraOut();
                        out.write('$');
                        out.write(header);
                        out.write(data);
                        out.flush();
                    }
                } else {
                    forward(RtspMessage.read(fromPlayer, first));
                }
            }
        }

        /**
         * Sends a request of the player to the camera, rewritten and logged in.
         */
        private void forward(RtspMessage request) throws IOException {
            RtspMessage rewritten = request.withUri(toCamera(request.uri(), base, cameraHost))
                    .withHeader("Authorization", null);
            String cseq = rewritten.header("CSeq");
            if (cseq != null) {
                pending.put(cseq.strip(), rewritten);
                try {
                    lastCseq = Math.max(lastCseq, Integer.parseInt(cseq.strip()));
                } catch (NumberFormatException e) {
                    // a player that does not count, the teardown then uses a number of its own
                }
            }
            if ("TEARDOWN".equalsIgnoreCase(rewritten.method())) {
                tornDown = true;
            }
            send(rewritten);
        }

        private void send(RtspMessage request) throws IOException {
            RtspDigest currentDigest = digest;
            String authorization = currentDigest == null ? null
                    : currentDigest.authorization(request.method(), request.uri());
            RtspMessage outgoing = authorization == null ? request : request.withHeader("Authorization", authorization);
            synchronized (cameraLock) {
                outgoing.write(cameraOut());
            }
        }

        private void cameraToPlayer(InputStream fromCamera, OutputStream toPlayer) {
            try {
                while (true) {
                    int first = fromCamera.read();
                    if (first < 0) {
                        return;
                    }
                    if (first == '$') {
                        byte[] header = fromCamera.readNBytes(3);
                        if (header.length < 3) {
                            return;
                        }
                        byte[] data = fromCamera.readNBytes(((header[1] & 0xff) << 8) | (header[2] & 0xff));
                        toPlayer.write('$');
                        toPlayer.write(header);
                        toPlayer.write(data);
                        toPlayer.flush();
                        continue;
                    }
                    RtspMessage response = RtspMessage.read(fromCamera, first);
                    String cseq = response.header("CSeq");
                    String key = cseq == null ? "" : cseq.strip();
                    RtspMessage request = pending.get(key);
                    String challenge = response.header("WWW-Authenticate");
                    RtspDigest currentDigest = digest;
                    if (response.status() == 401 && request != null && challenge != null && currentDigest != null
                            && retried.add(key) && currentDigest.takeChallenge(challenge)) {
                        // log in and ask again; the player only sees the answer to that
                        send(request);
                        continue;
                    }
                    pending.remove(key);
                    retried.remove(key);
                    String sessionHeader = response.header("Session");
                    if (sessionHeader != null) {
                        session = sessionHeader.split(";")[0].strip();
                    }
                    response.write(toPlayer);
                }
            } catch (IOException e) {
                logger.trace("Stream of {} ended: {}", cameraHost, e.getMessage());
            } finally {
                end();
            }
        }

        private OutputStream cameraOut() throws IOException {
            Socket current = camera;
            if (current == null) {
                throw new IOException("No connection to the camera");
            }
            return current.getOutputStream();
        }

        private void end() {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            close(connection);
            close(player);
            sockets.remove(connection);
            Socket current = camera;
            if (current != null) {
                tearDown(current);
                close(current);
                sockets.remove(current);
            }
        }

        /**
         * Ends the session at the camera if the player went away without doing so. The answer is not awaited.
         */
        private void tearDown(Socket current) {
            String currentSession = session;
            if (currentSession == null || tornDown || current.isClosed()) {
                return;
            }
            RtspMessage teardown = RtspMessage.request("TEARDOWN", cameraUri, String.valueOf(lastCseq + 1))
                    .withHeader("Session", currentSession);
            try {
                send(teardown);
                current.shutdownOutput();
                logger.debug("Ended the session at {} the player left open", cameraHost);
            } catch (IOException e) {
                logger.trace("Could not end the session at {}: {}", cameraHost, e.getMessage());
            }
        }
    }

    private static void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            // already gone
        }
    }
}
