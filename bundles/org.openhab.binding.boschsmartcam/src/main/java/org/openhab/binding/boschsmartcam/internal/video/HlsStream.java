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
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.video.AacDepacketizer.AacFrame;
import org.openhab.binding.boschsmartcam.internal.video.CameraStream.AudioConfig;
import org.openhab.binding.boschsmartcam.internal.video.CameraStream.VideoConfig;
import org.openhab.binding.boschsmartcam.internal.video.H264Depacketizer.AccessUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the live stream of a camera into HLS (RFC 8216) with fragmented MP4 segments, without transcoding: the
 * camera already sends H.264 and AAC, they only have to be put into another container.
 *
 * A segment starts at a keyframe and ends at the first keyframe after {@link #TARGET_SEGMENT_SECONDS}. The last few
 * segments are kept in memory and listed in a sliding playlist.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class HlsStream implements CameraStream.Sink {

    public static final String PLAYLIST_FILE = "live.m3u8";
    public static final String INIT_FILE = "init.mp4";
    public static final String SEGMENT_PREFIX = "segment-";
    public static final String SEGMENT_SUFFIX = ".m4s";

    private static final double TARGET_SEGMENT_SECONDS = 2.0;
    private static final int KEPT_SEGMENTS = 8;
    private static final int LISTED_SEGMENTS = 6;

    /**
     * Players start with the third segment from the end, so the playlist is only handed out once that many exist.
     */
    private static final int SEGMENTS_BEFORE_READY = 3;

    /**
     * A segment that grows beyond this without a keyframe is cut anyway, so memory stays bounded.
     */
    private static final int MAX_PICTURES_PER_SEGMENT = 600;

    public record Segment(int sequence, double duration, byte[] data) {
    }

    private final Logger logger = LoggerFactory.getLogger(HlsStream.class);

    private final String name;
    private final Deque<Segment> segments = new ArrayDeque<>();
    private final List<AccessUnit> pictures = new ArrayList<>();
    private final List<Long> pictureTimes = new ArrayList<>();
    private final Deque<AacFrame> audioFrames = new ArrayDeque<>();
    private final Deque<Long> audioTimes = new ArrayDeque<>();

    private byte @Nullable [] initSegment;
    private int videoClock = 90_000;
    private @Nullable AudioConfig audio;
    private int nextSequence = 1;
    private volatile boolean failed;

    private final TimestampUnwrapper videoTimestamps = new TimestampUnwrapper();
    private final TimestampUnwrapper audioTimestamps = new TimestampUnwrapper();
    private long videoBase = -1;
    private long videoBaseNanos;
    private long audioBase = -1;
    private long audioOffset;
    private long nextAudioTime = -1;

    public HlsStream(String name) {
        this.name = name;
    }

    @Override
    public synchronized void onStart(VideoConfig video, @Nullable AudioConfig audio) {
        try {
            initSegment = Fmp4Writer.initSegment(video, SpsParser.dimensions(video.sps()), audio);
        } catch (IOException e) {
            logger.debug("Could not read the picture size of {}: {}", name, e.getMessage());
            failed = true;
            notifyAll();
            return;
        }
        videoClock = video.clockRate();
        this.audio = audio;
    }

    @Override
    public synchronized void onVideo(AccessUnit accessUnit) {
        long unwrapped = videoTimestamps.unwrap(accessUnit.timestamp());
        if (videoBase < 0) {
            videoBase = unwrapped;
            videoBaseNanos = System.nanoTime();
        }
        long time = unwrapped - videoBase;
        if (!pictures.isEmpty()) {
            double length = (double) (time - pictureTimes.getFirst()) / videoClock;
            if ((accessUnit.keyframe() && length >= TARGET_SEGMENT_SECONDS)
                    || pictures.size() >= MAX_PICTURES_PER_SEGMENT) {
                cut(time);
            }
        }
        pictures.add(accessUnit);
        pictureTimes.add(time);
    }

    @Override
    public synchronized void onAudio(AacFrame frame) {
        AudioConfig config = audio;
        if (config == null || videoBase < 0) {
            // audio is aligned to the first picture
            return;
        }
        long unwrapped = audioTimestamps.unwrap(frame.timestamp());
        if (audioBase < 0) {
            audioBase = unwrapped;
            // the two RTP clocks start at random values, they are lined up by when the first packets arrived
            audioOffset = (System.nanoTime() - videoBaseNanos) * config.sampleRate() / 1_000_000_000L;
        }
        long time = unwrapped - audioBase + audioOffset;
        if (time < 0) {
            return;
        }
        audioFrames.add(frame);
        audioTimes.add(time);
    }

    @Override
    public synchronized void onFailure(IOException e) {
        failed = true;
        notifyAll();
    }

    /**
     * Closes the segment of the pictures collected so far.
     *
     * @param end the time of the picture that starts the next segment, in the video clock
     */
    private void cut(long end) {
        List<Fmp4Writer.Sample> videoSamples = new ArrayList<>();
        for (int i = 0; i < pictures.size(); i++) {
            long next = i + 1 < pictureTimes.size() ? pictureTimes.get(i + 1) : end;
            AccessUnit picture = pictures.get(i);
            videoSamples.add(new Fmp4Writer.Sample(Math.max(1, next - pictureTimes.get(i)),
                    Fmp4Writer.lengthPrefixed(picture.nalUnits()), picture.keyframe()));
        }
        long videoStart = pictureTimes.getFirst();

        List<Fmp4Writer.Sample> audioSamples = new ArrayList<>();
        long audioStart = 0;
        AudioConfig config = audio;
        if (config != null) {
            // audio up to where the video of this segment ends, in the audio clock
            long audioEnd = end * config.sampleRate() / videoClock;
            while (!audioTimes.isEmpty() && audioTimes.getFirst() < audioEnd) {
                long time = audioTimes.removeFirst();
                AacFrame frame = audioFrames.removeFirst();
                if (audioSamples.isEmpty()) {
                    // continue where the last segment ended, so the audio track has no gaps or overlaps
                    audioStart = nextAudioTime < 0 ? time : nextAudioTime;
                }
                audioSamples.add(new Fmp4Writer.Sample(AacDepacketizer.SAMPLES_PER_FRAME, frame.data(), true));
            }
            if (!audioSamples.isEmpty()) {
                nextAudioTime = audioStart + (long) audioSamples.size() * AacDepacketizer.SAMPLES_PER_FRAME;
            }
        }

        int sequence = nextSequence++;
        byte[] data = Fmp4Writer.fragment(sequence, videoStart, videoSamples, audioStart, audioSamples);
        double duration = (double) (end - videoStart) / videoClock;
        segments.addLast(new Segment(sequence, duration, data));
        while (segments.size() > KEPT_SEGMENTS) {
            segments.removeFirst();
        }
        pictures.clear();
        pictureTimes.clear();
        logger.trace("{}: segment {} with {} pictures and {} audio frames, {} s", name, sequence, videoSamples.size(),
                audioSamples.size(), duration);
        notifyAll();
    }

    /**
     * Waits until enough segments exist for a player to start.
     *
     * @return whether the stream is ready, {@code false} if it failed or took too long
     */
    public synchronized boolean awaitReady(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!failed && segments.size() < SEGMENTS_BEFORE_READY) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return false;
            }
            wait(Math.max(1, left / 1_000_000));
        }
        return !failed;
    }

    public boolean isFailed() {
        return failed;
    }

    public synchronized byte @Nullable [] getInitSegment() {
        return initSegment;
    }

    public synchronized byte @Nullable [] getSegment(int sequence) {
        return segments.stream().filter(s -> s.sequence() == sequence).map(Segment::data).findFirst().orElse(null);
    }

    public synchronized String getPlaylist() {
        List<Segment> listed = new ArrayList<>(segments);
        if (listed.size() > LISTED_SEGMENTS) {
            listed = listed.subList(listed.size() - LISTED_SEGMENTS, listed.size());
        }
        int target = (int) Math
                .ceil(listed.stream().mapToDouble(Segment::duration).max().orElse(TARGET_SEGMENT_SECONDS));
        StringBuilder playlist = new StringBuilder();
        playlist.append("#EXTM3U\n#EXT-X-VERSION:7\n");
        playlist.append("#EXT-X-TARGETDURATION:").append(target).append('\n');
        playlist.append("#EXT-X-MEDIA-SEQUENCE:").append(listed.isEmpty() ? 1 : listed.getFirst().sequence())
                .append('\n');
        playlist.append("#EXT-X-INDEPENDENT-SEGMENTS\n");
        playlist.append("#EXT-X-MAP:URI=\"").append(INIT_FILE).append("\"\n");
        for (Segment segment : listed) {
            playlist.append(String.format(Locale.ROOT, "#EXTINF:%.3f,", segment.duration())).append('\n');
            playlist.append(SEGMENT_PREFIX).append(segment.sequence()).append(SEGMENT_SUFFIX).append('\n');
        }
        return playlist.toString();
    }

    /**
     * Turns the 32 bit RTP timestamps into a steadily growing count, they wrap around after about 13 hours of video.
     */
    static class TimestampUnwrapper {
        private long last = -1;
        private long wraps;

        long unwrap(long timestamp) {
            if (last >= 0 && timestamp < last && last - timestamp > 0x80000000L) {
                wraps++;
            }
            last = timestamp;
            return wraps * 0x100000000L + timestamp;
        }
    }
}
