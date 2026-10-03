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
package org.openhab.binding.boschsmartcam.internal.video;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A minimal RTSP client (RFC 2326) for one session over one connection, with the media interleaved into that
 * connection. That is what the cameras offer on their RTSP tunnel, and it needs no other port.
 *
 * The cameras ask for HTTP Digest authentication with MD5 and without {@code qop}, already for {@code OPTIONS}.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class RtspClient implements Closeable {

    /**
     * Receives the packets interleaved into the connection, RTP on the even channels and RTCP on the odd ones.
     */
    @FunctionalInterface
    public interface PacketListener {
        void onPacket(int channel, byte[] data);
    }

    public record Response(int status, Map<String, String> headers, byte[] body) {

        public @Nullable String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private static final Pattern DIGEST_PARAMETER = Pattern.compile("(\\w+)=\"?([^\",]*)\"?");
    private static final String USER_AGENT = "openHAB Bosch Smart Home Camera Binding";
    private static final int MAX_HEADER_LINES = 100;

    private final Logger logger = LoggerFactory.getLogger(RtspClient.class);

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final String user;
    private final String password;

    private int cseq;
    private @Nullable String session;
    private int sessionTimeoutSeconds = 60;
    private @Nullable String realm;
    private @Nullable String nonce;
    private @Nullable String opaque;

    public RtspClient(Socket socket, String user, String password) throws IOException {
        this.socket = socket;
        this.in = new BufferedInputStream(socket.getInputStream());
        this.out = socket.getOutputStream();
        this.user = user;
        this.password = password;
    }

    /**
     * Sends a request and waits for its answer, authenticating if the camera asks for it.
     */
    public Response request(String method, String uri, Map<String, String> headers) throws IOException {
        Response response = send(method, uri, headers);
        if (response.status() == 401) {
            String challenge = response.header("WWW-Authenticate");
            if (challenge == null || !challenge.regionMatches(true, 0, "Digest", 0, 6)) {
                throw new IOException("The camera asks for an unsupported authentication: " + challenge);
            }
            takeChallenge(challenge);
            response = send(method, uri, headers);
        }
        if (response.status() != 200) {
            throw new IOException("The camera answered " + response.status() + " to " + method + " " + uri);
        }
        return response;
    }

    public List<Sdp.Media> describe(String url) throws IOException {
        Response response = request("DESCRIBE", url, Map.of("Accept", "application/sdp"));
        String base = response.header("Content-Base");
        String sdp = new String(response.body(), StandardCharsets.UTF_8);
        logger.debug("SDP of {}: {}", url, sdp);
        return Sdp.parse(sdp, base == null || base.isBlank() ? url : base);
    }

    /**
     * Sets a stream up to be interleaved on {@code channel} (RTP) and {@code channel + 1} (RTCP).
     */
    public void setup(Sdp.Media media, int channel) throws IOException {
        Response response = request("SETUP", media.control(),
                Map.of("Transport", "RTP/AVP/TCP;unicast;interleaved=" + channel + "-" + (channel + 1)));
        String sessionHeader = response.header("Session");
        if (sessionHeader == null) {
            throw new IOException("The camera did not open a session");
        }
        String[] parts = sessionHeader.split(";");
        session = parts[0].strip();
        for (String part : parts) {
            String trimmed = part.strip();
            if (trimmed.startsWith("timeout=")) {
                try {
                    sessionTimeoutSeconds = Integer.parseInt(trimmed.substring("timeout=".length()));
                } catch (NumberFormatException e) {
                    // keep the default
                }
            }
        }
    }

    public void play(String url) throws IOException {
        request("PLAY", url, Map.of("Range", "npt=0.000-"));
    }

    /**
     * @return how often the session has to be refreshed, half the timeout the camera gave
     */
    public int keepaliveSeconds() {
        return Math.max(5, sessionTimeoutSeconds / 2);
    }

    /**
     * Refreshes the session without waiting for the answer, which {@link #readPackets} skips.
     */
    public void keepalive(String url) throws IOException {
        write("GET_PARAMETER", url, Map.of());
    }

    /**
     * Ends the session. The answer is not awaited, the connection is closed right after.
     */
    public void teardown(String url) {
        try {
            write("TEARDOWN", url, Map.of());
        } catch (IOException e) {
            logger.trace("Could not end the session: {}", e.getMessage());
        }
    }

    /**
     * Reads the interleaved packets until the connection fails or is closed. Answers to requests sent meanwhile are
     * read and dropped.
     */
    public void readPackets(PacketListener listener) throws IOException {
        while (true) {
            int first = in.read();
            if (first < 0) {
                throw new EOFException("The camera closed the connection");
            }
            if (first == '$') {
                int channel = readByte();
                int length = (readByte() << 8) | readByte();
                listener.onPacket(channel, in.readNBytes(length));
            } else {
                Response response = readResponse(first);
                if (response.status() != 200) {
                    logger.debug("The camera answered {} to a request on the open session", response.status());
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private Response send(String method, String uri, Map<String, String> headers) throws IOException {
        int sent = write(method, uri, headers);
        while (true) {
            int first = in.read();
            if (first < 0) {
                throw new EOFException("The camera closed the connection");
            }
            if (first == '$') {
                // media of a stream that is already playing, nobody listens yet
                readByte();
                int length = (readByte() << 8) | readByte();
                in.skipNBytes(length);
                continue;
            }
            Response response = readResponse(first);
            String answered = response.header("CSeq");
            if (answered == null || answered.strip().equals(String.valueOf(sent))) {
                return response;
            }
        }
    }

    private synchronized int write(String method, String uri, Map<String, String> headers) throws IOException {
        int sequence = ++cseq;
        StringBuilder request = new StringBuilder();
        request.append(method).append(' ').append(uri).append(" RTSP/1.0\r\n");
        request.append("CSeq: ").append(sequence).append("\r\n");
        request.append("User-Agent: ").append(USER_AGENT).append("\r\n");
        String currentSession = session;
        if (currentSession != null) {
            request.append("Session: ").append(currentSession).append("\r\n");
        }
        String authorization = authorization(method, uri);
        if (authorization != null) {
            request.append("Authorization: ").append(authorization).append("\r\n");
        }
        headers.forEach((name, value) -> request.append(name).append(": ").append(value).append("\r\n"));
        request.append("\r\n");
        logger.trace("> {} {} CSeq {}", method, uri, sequence);
        out.write(request.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
        return sequence;
    }

    private Response readResponse(int first) throws IOException {
        String statusLine = (char) first + readLine();
        String[] status = statusLine.split(" ", 3);
        if (status.length < 2 || !status[0].startsWith("RTSP/")) {
            throw new IOException("Unexpected answer from the camera: " + statusLine);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 0; i < MAX_HEADER_LINES; i++) {
            String line = readLine();
            if (line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).strip().toLowerCase(Locale.ROOT),
                        line.substring(colon + 1).strip());
            }
        }
        String length = headers.get("content-length");
        byte[] body = length == null ? new byte[0] : in.readNBytes(Integer.parseInt(length.strip()));
        try {
            logger.trace("< {}", statusLine);
            return new Response(Integer.parseInt(status[1]), headers, body);
        } catch (NumberFormatException e) {
            throw new IOException("Unexpected answer from the camera: " + statusLine, e);
        }
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("The camera closed the connection");
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                line.write(b);
            }
        }
        return line.toString(StandardCharsets.UTF_8);
    }

    private int readByte() throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException("The camera closed the connection");
        }
        return b;
    }

    private void takeChallenge(String challenge) {
        Matcher matcher = DIGEST_PARAMETER.matcher(challenge.substring(6));
        while (matcher.find()) {
            switch (matcher.group(1).toLowerCase(Locale.ROOT)) {
                case "realm" -> realm = matcher.group(2);
                case "nonce" -> nonce = matcher.group(2);
                case "opaque" -> opaque = matcher.group(2);
                default -> {
                    // algorithm is MD5, stale needs no handling
                }
            }
        }
    }

    private @Nullable String authorization(String method, String uri) {
        String currentRealm = realm;
        String currentNonce = nonce;
        if (currentRealm == null || currentNonce == null) {
            return null;
        }
        String ha1 = md5(user + ":" + currentRealm + ":" + password);
        String ha2 = md5(method + ":" + uri);
        String response = md5(ha1 + ":" + currentNonce + ":" + ha2);
        String currentOpaque = opaque;
        return "Digest username=\"" + user + "\", realm=\"" + currentRealm + "\", nonce=\"" + currentNonce
                + "\", uri=\"" + uri + "\", response=\"" + response + "\""
                + (currentOpaque == null || currentOpaque.isEmpty() ? "" : ", opaque=\"" + currentOpaque + "\"")
                + ", algorithm=MD5";
    }

    static String md5(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is part of every JDK", e);
        }
    }
}
