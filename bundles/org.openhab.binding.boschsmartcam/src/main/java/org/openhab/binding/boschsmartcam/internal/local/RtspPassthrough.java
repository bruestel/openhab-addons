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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Passes the RTSP tunnel of a camera through a port of openHAB as it is, TLS included. For players that cannot reach
 * the camera themselves, e.g. because it is in a network only openHAB can reach.
 *
 * TLS runs between player and camera, so openHAB cannot look into the connection: the player sees the certificate of
 * the camera and has to check it, and logs in with the user and password of the camera. {@link RtspGateway} is the
 * alternative that does both for the player.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class RtspPassthrough {

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * Each connection carries a whole video stream to the camera, so a handful is plenty.
     */
    private static final int MAX_CONNECTIONS = 8;

    private final Logger logger = LoggerFactory.getLogger(RtspPassthrough.class);

    private final String cameraHost;
    private final int cameraPort;
    private final int port;
    private final Predicate<String> allowedFrom;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();

    private volatile @Nullable ServerSocket serverSocket;

    /**
     * @param port the port openHAB listens on
     * @param allowedFrom whether a player at the given address may connect
     */
    public RtspPassthrough(String cameraHost, int cameraPort, int port, Predicate<String> allowedFrom) {
        this.cameraHost = cameraHost;
        this.cameraPort = cameraPort;
        this.port = port;
        this.allowedFrom = allowedFrom;
    }

    /**
     * @throws IOException if the port cannot be opened, e.g. because something else uses it
     */
    public synchronized void start() throws IOException {
        if (serverSocket != null) {
            return;
        }
        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port));
        serverSocket = server;
        Thread.ofVirtual().name("boschsmartcam-rtsps-" + port).start(() -> accept(server));
        logger.debug("Passing the RTSP tunnel of {} through port {}", cameraHost, port);
    }

    public synchronized void stop() {
        ServerSocket server = serverSocket;
        serverSocket = null;
        if (server != null) {
            close(server);
        }
        sockets.forEach(RtspPassthrough::close);
        sockets.clear();
    }

    private void accept(ServerSocket server) {
        while (!server.isClosed()) {
            Socket player;
            try {
                player = server.accept();
            } catch (IOException e) {
                if (!server.isClosed()) {
                    logger.debug("Accepting on port {} failed: {}", port, e.getMessage());
                }
                continue;
            }
            String address = player.getInetAddress().getHostAddress();
            if (!allowedFrom.test(address)) {
                logger.warn("Refused an RTSP connection from {}, it is not in the allowed networks", address);
                close(player);
            } else if (sockets.size() >= 2 * MAX_CONNECTIONS) {
                logger.debug("Refused an RTSP connection from {}, too many are open", address);
                close(player);
            } else {
                Thread.ofVirtual().name("boschsmartcam-rtsps-" + port + "-" + address).start(() -> relay(player));
            }
        }
    }

    private void relay(Socket player) {
        Socket camera = new Socket();
        try {
            camera.connect(new InetSocketAddress(cameraHost, cameraPort), CONNECT_TIMEOUT_MILLIS);
            camera.setKeepAlive(true);
            player.setKeepAlive(true);
            player.setTcpNoDelay(true);
        } catch (IOException e) {
            logger.debug("Could not reach the RTSP tunnel of {}: {}", cameraHost, e.getMessage());
            close(camera);
            close(player);
            return;
        }
        sockets.add(player);
        sockets.add(camera);
        logger.debug("Passing RTSP from {} through to {}", player.getInetAddress().getHostAddress(), cameraHost);
        Thread.ofVirtual().name("boschsmartcam-rtsps-" + port + "-up").start(() -> copy(player, camera));
        copy(camera, player);
    }

    /**
     * Copies until either side closes, then closes both, which also ends the copy in the other direction.
     */
    private void copy(Socket from, Socket to) {
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException e) {
            logger.trace("RTSP passthrough ended: {}", e.getMessage());
        } finally {
            close(from);
            close(to);
            sockets.remove(from);
            sockets.remove(to);
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
