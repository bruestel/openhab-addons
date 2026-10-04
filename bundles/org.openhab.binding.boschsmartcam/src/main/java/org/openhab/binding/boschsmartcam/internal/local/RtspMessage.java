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

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * An RTSP request or response (RFC 2326): a start line, headers in their original order, and an optional body.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public final class RtspMessage {

    private static final int MAX_LINE_LENGTH = 8192;
    private static final int MAX_HEADERS = 100;
    private static final int MAX_BODY = 1024 * 1024;

    private String startLine;
    private final List<String[]> headers;
    private final byte[] body;

    private RtspMessage(String startLine, List<String[]> headers, byte[] body) {
        this.startLine = startLine;
        this.headers = headers;
        this.body = body;
    }

    /**
     * Reads a message whose first byte was already taken from the stream.
     */
    public static RtspMessage read(InputStream in, int first) throws IOException {
        String startLine = (char) first + readLine(in);
        List<String[]> headers = new ArrayList<>();
        for (int i = 0; i < MAX_HEADERS; i++) {
            String line = readLine(in);
            if (line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.add(new String[] { line.substring(0, colon).strip(), line.substring(colon + 1).strip() });
            }
        }
        RtspMessage message = new RtspMessage(startLine, headers, new byte[0]);
        String length = message.header("Content-Length");
        if (length == null) {
            return message;
        }
        int size;
        try {
            size = Integer.parseInt(length.strip());
        } catch (NumberFormatException e) {
            throw new IOException("Unreadable Content-Length " + length, e);
        }
        if (size < 0 || size > MAX_BODY) {
            throw new IOException("Body of " + size + " bytes refused");
        }
        byte[] body = in.readNBytes(size);
        if (body.length < size) {
            throw new EOFException("The body ends early");
        }
        return new RtspMessage(startLine, headers, body);
    }

    public String startLine() {
        return startLine;
    }

    /**
     * @return the method of a request
     */
    public String method() {
        return startLine.split(" ", 2)[0];
    }

    /**
     * @return the URI of a request
     */
    public String uri() {
        String[] parts = startLine.split(" ");
        return parts.length > 1 ? parts[1] : "";
    }

    /**
     * @return the status of a response, or 0 if this is no response
     */
    public int status() {
        String[] parts = startLine.split(" ");
        if (parts.length < 2 || !parts[0].startsWith("RTSP/")) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public RtspMessage withUri(String uri) {
        String[] parts = startLine.split(" ");
        List<String[]> copy = new ArrayList<>(headers);
        return new RtspMessage(parts[0] + " " + uri + (parts.length > 2 ? " " + parts[2] : ""), copy, body);
    }

    public @Nullable String header(String name) {
        return headers.stream().filter(h -> h[0].equalsIgnoreCase(name)).map(h -> h[1]).findFirst().orElse(null);
    }

    /**
     * @return a copy with the header set, replacing every existing one of that name
     */
    public RtspMessage withHeader(String name, @Nullable String value) {
        List<String[]> copy = new ArrayList<>(headers.stream().filter(h -> !h[0].equalsIgnoreCase(name)).toList());
        if (value != null) {
            copy.add(new String[] { name, value });
        }
        return new RtspMessage(startLine, copy, body);
    }

    public void write(OutputStream out) throws IOException {
        StringBuilder text = new StringBuilder(startLine).append("\r\n");
        headers.forEach(h -> text.append(h[0]).append(": ").append(h[1]).append("\r\n"));
        text.append("\r\n");
        out.write(text.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
    }

    public static RtspMessage request(String method, String uri, String cseq) {
        List<String[]> headers = new ArrayList<>();
        headers.add(new String[] { "CSeq", cseq });
        return new RtspMessage(method + " " + uri + " RTSP/1.0", headers, new byte[0]);
    }

    public static RtspMessage response(int status, String reason, @Nullable String cseq) {
        List<String[]> headers = new ArrayList<>();
        if (cseq != null) {
            headers.add(new String[] { "CSeq", cseq });
        }
        return new RtspMessage("RTSP/1.0 " + status + " " + reason, headers, new byte[0]);
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new EOFException("The connection ended in the middle of a message");
            }
            if (b == '\n') {
                return line.toString(StandardCharsets.UTF_8);
            }
            if (b != '\r') {
                if (line.size() >= MAX_LINE_LENGTH) {
                    throw new IOException("Line too long");
                }
                line.write(b);
            }
        }
    }
}
