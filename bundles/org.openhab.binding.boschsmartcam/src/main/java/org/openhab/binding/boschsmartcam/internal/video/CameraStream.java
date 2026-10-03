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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocket;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.local.CameraTrust;
import org.openhab.binding.boschsmartcam.internal.video.AacDepacketizer.AacFrame;
import org.openhab.binding.boschsmartcam.internal.video.H264Depacketizer.AccessUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives the live stream of a camera through its RTSP tunnel and hands out pictures and audio frames.
 *
 * The connection is TLS on port 9554 with the same trust as everything else, so it is bound to the camera. It runs
 * on a virtual thread of its own, a stream blocks on the network for as long as it lasts.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class CameraStream {

    /**
     * Port of the RTSP tunnel of the cameras.
     */
    public static final int RTSP_PORT = 9554;

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    /**
     * The camera sends pictures many times a second, silence for this long means the stream is dead.
     */
    private static final int READ_TIMEOUT_MILLIS = 10_000;

    private static final String ENCODING_H264 = "H264";
    private static final String ENCODING_AAC = "MPEG4-GENERIC";

    /**
     * What a stream delivers. Called from the thread of the stream, so a sink must not block for long.
     */
    public interface Sink {

        /**
         * Called once the camera described its stream, before the first picture.
         */
        void onStart(VideoConfig video, @Nullable AudioConfig audio);

        void onVideo(AccessUnit accessUnit);

        void onAudio(AacFrame frame);

        /**
         * The stream ended without being stopped.
         */
        void onFailure(IOException e);
    }

    /**
     * @param sps the sequence parameter set, with NAL header
     * @param pps the picture parameter set, with NAL header
     * @param clockRate the RTP clock of the video, 90 kHz for H.264
     */
    public record VideoConfig(byte[] sps, byte[] pps, int clockRate) {
    }

    /**
     * @param audioSpecificConfig the AudioSpecificConfig of ISO 14496-3, {@code config} in the SDP
     */
    public record AudioConfig(int sampleRate, int channels, byte[] audioSpecificConfig) {
    }

    private final Logger logger = LoggerFactory.getLogger(CameraStream.class);

    private final CameraTrust cameraTrust;
    private final ScheduledExecutorService scheduler;
    private final String host;
    private final String user;
    private final String password;
    private final boolean trustAll;
    private final String url;
    private final boolean withAudio;
    private final Sink sink;

    private volatile boolean running;
    private volatile @Nullable RtspClient client;
    private volatile @Nullable ScheduledFuture<?> keepaliveJob;

    /**
     * @param instance 1 for the full resolution, 2 for the small one
     * @param withAudio whether to receive the sound as well
     */
    public CameraStream(CameraTrust cameraTrust, ScheduledExecutorService scheduler, String host, String user,
            String password, boolean trustAll, int instance, boolean withAudio, Sink sink) {
        this.cameraTrust = cameraTrust;
        this.scheduler = scheduler;
        this.host = host;
        this.user = user;
        this.password = password;
        this.trustAll = trustAll;
        this.url = "rtsp://" + host + ":" + RTSP_PORT + "/rtsp_tunnel?line=1&inst=" + instance + "&enableaudio="
                + (withAudio ? 1 : 0);
        this.withAudio = withAudio;
        this.sink = sink;
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        Thread.ofVirtual().name("boschsmartcam-stream-" + host).start(this::run);
    }

    public synchronized void stop() {
        running = false;
        ScheduledFuture<?> job = keepaliveJob;
        if (job != null) {
            job.cancel(false);
            keepaliveJob = null;
        }
        RtspClient current = client;
        if (current != null) {
            current.teardown(url);
            close(current);
            client = null;
        }
    }

    public boolean isRunning() {
        return running;
    }

    private void run() {
        RtspClient current = null;
        try {
            SSLSocket socket = cameraTrust.openSocket(host, RTSP_PORT, trustAll, CONNECT_TIMEOUT_MILLIS);
            current = new RtspClient(socket, user, password);
            client = current;
            if (!running) {
                return;
            }

            List<Sdp.Media> media = current.describe(url);
            Sdp.Media video = media.stream().filter(m -> ENCODING_H264.equals(m.encoding())).findFirst()
                    .orElseThrow(() -> new IOException("The camera offers no H.264 video"));
            Sdp.Media audio = withAudio
                    ? media.stream().filter(m -> ENCODING_AAC.equals(m.encoding())).findFirst().orElse(null)
                    : null;

            VideoConfig videoConfig = videoConfig(video);
            AudioConfig audioConfig = audio == null ? null : audioConfig(audio);

            current.setup(video, 0);
            if (audioConfig != null && audio != null) {
                current.setup(audio, 2);
            }

            H264Depacketizer h264 = new H264Depacketizer(sink::onVideo);
            AacDepacketizer aac = audio == null ? null
                    : new AacDepacketizer(sink::onAudio, intParameter(audio, "sizelength", 13),
                            intParameter(audio, "indexlength", 3));
            int videoType = video.payloadType();

            sink.onStart(videoConfig, audioConfig);
            current.play(url);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            RtspClient forKeepalive = current;
            keepaliveJob = scheduler.scheduleWithFixedDelay(() -> {
                try {
                    forKeepalive.keepalive(url);
                } catch (IOException e) {
                    logger.debug("Keepalive for {} failed: {}", host, e.getMessage());
                }
            }, current.keepaliveSeconds(), current.keepaliveSeconds(), TimeUnit.SECONDS);
            logger.debug("Streaming {}", url);

            current.readPackets((channel, data) -> {
                if (channel != 0 && channel != 2) {
                    // RTCP, the camera does not need an answer to keep sending
                    return;
                }
                try {
                    RtpPacket packet = RtpPacket.parse(data);
                    if (channel == 0 && packet.payloadType() == videoType) {
                        h264.process(packet);
                    } else if (channel == 2 && aac != null) {
                        aac.process(packet);
                    }
                } catch (IOException e) {
                    logger.trace("Skipping a broken packet: {}", e.getMessage());
                }
            });
        } catch (IOException e) {
            if (running) {
                logger.debug("Stream of {} failed: {}", host, e.getMessage());
                running = false;
                sink.onFailure(e);
            }
        } finally {
            if (current != null) {
                close(current);
            }
            ScheduledFuture<?> job = keepaliveJob;
            if (job != null) {
                job.cancel(false);
            }
        }
    }

    private static VideoConfig videoConfig(Sdp.Media video) throws IOException {
        String sets = video.parameter("sprop-parameter-sets");
        byte[] sps = null;
        byte[] pps = null;
        if (sets != null) {
            for (String set : sets.split(",")) {
                byte[] nal = Base64.getDecoder().decode(set.strip());
                if (nal.length == 0) {
                    continue;
                }
                int type = nal[0] & 0x1f;
                if (type == H264Depacketizer.NAL_SPS) {
                    sps = nal;
                } else if (type == H264Depacketizer.NAL_PPS) {
                    pps = nal;
                }
            }
        }
        if (sps == null || pps == null) {
            throw new IOException("The camera did not describe the parameter sets of its video");
        }
        return new VideoConfig(sps, pps, video.clockRate() > 0 ? video.clockRate() : 90_000);
    }

    private static @Nullable AudioConfig audioConfig(Sdp.Media audio) {
        String config = audio.parameter("config");
        if (config == null || config.isBlank() || audio.clockRate() <= 0) {
            return null;
        }
        try {
            return new AudioConfig(audio.clockRate(), audio.channels(), HexFormat.of().parseHex(config));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int intParameter(Sdp.Media media, String name, int fallback) {
        String value = media.parameter(name);
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void close(RtspClient current) {
        try {
            current.close();
        } catch (IOException e) {
            logger.trace("Could not close the stream of {}: {}", host, e.getMessage());
        }
    }

    /**
     * @return the parameter sets followed by the NAL units of the picture, each behind a start code - the form a raw
     *         {@code .h264} file has
     */
    public static byte[] annexB(VideoConfig config, AccessUnit accessUnit) {
        List<byte[]> units = new ArrayList<>();
        if (accessUnit.keyframe()) {
            units.add(config.sps());
            units.add(config.pps());
        }
        units.addAll(accessUnit.nalUnits());
        int size = units.stream().mapToInt(unit -> unit.length + 4).sum();
        byte[] result = new byte[size];
        int offset = 0;
        for (byte[] unit : units) {
            result[offset + 3] = 1;
            System.arraycopy(unit, 0, result, offset + 4, unit.length);
            offset += unit.length + 4;
        }
        return result;
    }
}
