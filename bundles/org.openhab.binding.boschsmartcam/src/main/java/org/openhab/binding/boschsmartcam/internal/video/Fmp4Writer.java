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

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.boschsmartcam.internal.video.CameraStream.AudioConfig;
import org.openhab.binding.boschsmartcam.internal.video.CameraStream.VideoConfig;

/**
 * Writes fragmented MP4 (ISO/IEC 14496-12) for HLS: one init segment that describes the tracks, then fragments of
 * samples. Video is track 1, audio track 2.
 *
 * The cameras send no B-frames, so every picture is shown when it is decoded and no composition offsets are needed.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public final class Fmp4Writer {

    public static final int VIDEO_TRACK = 1;
    public static final int AUDIO_TRACK = 2;

    /**
     * A picture or an audio frame as it goes into a fragment.
     *
     * @param duration in units of the timescale of its track
     * @param data for video the NAL units each behind a four byte length, for audio the raw AAC frame
     */
    public record Sample(long duration, byte[] data, boolean keyframe) {
    }

    private static final int SAMPLE_FLAGS_SYNC = 0x02000000;
    private static final int SAMPLE_FLAGS_NON_SYNC = 0x01010000;

    private Fmp4Writer() {
    }

    public static byte[] initSegment(VideoConfig video, SpsParser.Dimensions size, @Nullable AudioConfig audio) {
        Box ftyp = new Box("ftyp").fourcc("iso6").u32(512).fourcc("iso6").fourcc("iso5").fourcc("mp41");

        Box moov = new Box("moov");
        moov.add(mvhd(audio == null ? VIDEO_TRACK + 1 : AUDIO_TRACK + 1));
        moov.add(videoTrak(video, size));
        if (audio != null) {
            moov.add(audioTrak(audio));
        }
        Box mvex = new Box("mvex").add(trex(VIDEO_TRACK));
        if (audio != null) {
            mvex.add(trex(AUDIO_TRACK));
        }
        moov.add(mvex);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ftyp.writeTo(out);
        moov.writeTo(out);
        return out.toByteArray();
    }

    /**
     * @param sequence number of the fragment, counting from 1
     * @param videoStart decode time of the first picture, in the video timescale
     * @param audioStart decode time of the first audio frame, in the audio timescale
     */
    public static byte[] fragment(int sequence, long videoStart, List<Sample> video, long audioStart,
            List<Sample> audio) {
        // the offsets into mdat depend on the size of moof, so it is built twice
        Box moof = moof(sequence, videoStart, video, audioStart, audio, 0, 0);
        int moofSize = moof.size();
        int videoOffset = moofSize + 8;
        int audioOffset = videoOffset + video.stream().mapToInt(s -> s.data().length).sum();
        moof = moof(sequence, videoStart, video, audioStart, audio, videoOffset, audioOffset);

        Box mdat = new Box("mdat");
        video.forEach(sample -> mdat.bytes(sample.data()));
        audio.forEach(sample -> mdat.bytes(sample.data()));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        moof.writeTo(out);
        mdat.writeTo(out);
        return out.toByteArray();
    }

    /**
     * @return the NAL units of a picture each behind a four byte length, as MP4 stores them. Parameter sets and
     *         access unit delimiters are left out, the init segment carries the former.
     */
    public static byte[] lengthPrefixed(List<byte[]> nalUnits) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] nal : nalUnits) {
            int type = nal[0] & 0x1f;
            if (type == H264Depacketizer.NAL_SPS || type == H264Depacketizer.NAL_PPS || type == 9) {
                continue;
            }
            out.writeBytes(ByteBuffer.allocate(4).putInt(nal.length).array());
            out.writeBytes(nal);
        }
        return out.toByteArray();
    }

    private static Box moof(int sequence, long videoStart, List<Sample> video, long audioStart, List<Sample> audio,
            int videoOffset, int audioOffset) {
        Box moof = new Box("moof").add(new Box("mfhd").fullBox(0, 0).u32(sequence));
        moof.add(traf(VIDEO_TRACK, videoStart, video, videoOffset, true));
        if (!audio.isEmpty()) {
            moof.add(traf(AUDIO_TRACK, audioStart, audio, audioOffset, false));
        }
        return moof;
    }

    private static Box traf(int track, long start, List<Sample> samples, int dataOffset, boolean withFlags) {
        // default-base-is-moof: the data offsets count from the start of moof
        Box tfhd = new Box("tfhd").fullBox(0, 0x020000).u32(track);
        Box tfdt = new Box("tfdt").fullBox(1, 0).u64(start);
        // data offset, sample duration, sample size and, for video, sample flags present
        int trunFlags = 0x000001 | 0x000100 | 0x000200 | (withFlags ? 0x000400 : 0);
        Box trun = new Box("trun").fullBox(0, trunFlags).u32(samples.size()).u32(dataOffset);
        for (Sample sample : samples) {
            trun.u32((int) sample.duration()).u32(sample.data().length);
            if (withFlags) {
                trun.u32(sample.keyframe() ? SAMPLE_FLAGS_SYNC : SAMPLE_FLAGS_NON_SYNC);
            }
        }
        return new Box("traf").add(tfhd).add(tfdt).add(trun);
    }

    private static Box mvhd(int nextTrack) {
        Box mvhd = new Box("mvhd").fullBox(0, 0).u32(0).u32(0).u32(1000).u32(0).u32(0x00010000).u16(0x0100).zeros(10);
        matrix(mvhd);
        return mvhd.zeros(24).u32(nextTrack);
    }

    private static Box videoTrak(VideoConfig video, SpsParser.Dimensions size) {
        Box tkhd = new Box("tkhd").fullBox(0, 3).u32(0).u32(0).u32(VIDEO_TRACK).u32(0).u32(0).zeros(8).u16(0).u16(0)
                .u16(0).u16(0);
        matrix(tkhd);
        tkhd.u32(size.width() << 16).u32(size.height() << 16);

        Box avcC = new Box("avcC").u8(1).u8(video.sps()[1] & 0xff).u8(video.sps()[2] & 0xff).u8(video.sps()[3] & 0xff)
                .u8(0xff).u8(0xe1).u16(video.sps().length).bytes(video.sps()).u8(1).u16(video.pps().length)
                .bytes(video.pps());
        Box avc1 = new Box("avc1").zeros(6).u16(1).zeros(16).u16(size.width()).u16(size.height()).u32(0x00480000)
                .u32(0x00480000).u32(0).u16(1).zeros(32).u16(0x0018).u16(0xffff).add(avcC);

        Box stbl = sampleTable(avc1);
        Box minf = new Box("minf").add(new Box("vmhd").fullBox(0, 1).zeros(8)).add(dinf()).add(stbl);
        Box mdia = new Box("mdia").add(mdhd(video.clockRate())).add(hdlr("vide", "VideoHandler")).add(minf);
        return new Box("trak").add(tkhd).add(mdia);
    }

    private static Box audioTrak(AudioConfig audio) {
        Box tkhd = new Box("tkhd").fullBox(0, 3).u32(0).u32(0).u32(AUDIO_TRACK).u32(0).u32(0).zeros(8).u16(0).u16(1)
                .u16(0x0100).u16(0);
        matrix(tkhd);
        tkhd.u32(0).u32(0);

        byte[] config = audio.audioSpecificConfig();
        // ES_Descriptor > DecoderConfigDescriptor (AAC, audio stream) > DecoderSpecificInfo, then SLConfig
        Box esds = new Box("esds").fullBox(0, 0).u8(0x03).u8(23 + config.length).u16(AUDIO_TRACK).u8(0).u8(0x04)
                .u8(15 + config.length).u8(0x40).u8(0x15).u8(0).u16(0).u32(0).u32(0).u8(0x05).u8(config.length)
                .bytes(config).u8(0x06).u8(1).u8(2);
        Box mp4a = new Box("mp4a").zeros(6).u16(1).zeros(8).u16(audio.channels()).u16(16).u16(0).u16(0)
                .u32(audio.sampleRate() << 16).add(esds);

        Box stbl = sampleTable(mp4a);
        Box minf = new Box("minf").add(new Box("smhd").fullBox(0, 0).u16(0).u16(0)).add(dinf()).add(stbl);
        Box mdia = new Box("mdia").add(mdhd(audio.sampleRate())).add(hdlr("soun", "SoundHandler")).add(minf);
        return new Box("trak").add(tkhd).add(mdia);
    }

    private static Box sampleTable(Box sampleEntry) {
        return new Box("stbl").add(new Box("stsd").fullBox(0, 0).u32(1).add(sampleEntry))
                .add(new Box("stts").fullBox(0, 0).u32(0)).add(new Box("stsc").fullBox(0, 0).u32(0))
                .add(new Box("stsz").fullBox(0, 0).u32(0).u32(0)).add(new Box("stco").fullBox(0, 0).u32(0));
    }

    private static Box mdhd(int timescale) {
        // language "und"
        return new Box("mdhd").fullBox(0, 0).u32(0).u32(0).u32(timescale).u32(0).u16(0x55c4).u16(0);
    }

    private static Box hdlr(String type, String name) {
        return new Box("hdlr").fullBox(0, 0).u32(0).fourcc(type).zeros(12)
                .bytes((name + "\0").getBytes(StandardCharsets.US_ASCII));
    }

    private static Box dinf() {
        return new Box("dinf").add(new Box("dref").fullBox(0, 0).u32(1).add(new Box("url ").fullBox(0, 1)));
    }

    private static Box trex(int track) {
        return new Box("trex").fullBox(0, 0).u32(track).u32(1).u32(0).u32(0).u32(0);
    }

    private static void matrix(Box box) {
        box.u32(0x00010000).u32(0).u32(0).u32(0).u32(0x00010000).u32(0).u32(0).u32(0).u32(0x40000000);
    }

    /**
     * An MP4 box: size and type, then its own fields and the boxes it contains, in the order they were added.
     */
    static final class Box {
        private final String type;
        private final ByteArrayOutputStream content = new ByteArrayOutputStream();

        Box(String type) {
            this.type = type;
        }

        Box fullBox(int version, int flags) {
            return u8(version).u8(flags >> 16).u8(flags >> 8).u8(flags);
        }

        Box u8(int value) {
            content.write(value & 0xff);
            return this;
        }

        Box u16(int value) {
            return u8(value >> 8).u8(value);
        }

        Box u32(int value) {
            return u16(value >> 16).u16(value);
        }

        Box u64(long value) {
            return u32((int) (value >> 32)).u32((int) value);
        }

        Box fourcc(String code) {
            content.writeBytes(code.getBytes(StandardCharsets.US_ASCII));
            return this;
        }

        Box zeros(int count) {
            content.writeBytes(new byte[count]);
            return this;
        }

        Box bytes(byte[] data) {
            content.writeBytes(data);
            return this;
        }

        Box add(Box child) {
            child.writeTo(content);
            return this;
        }

        int size() {
            return 8 + content.size();
        }

        void writeTo(ByteArrayOutputStream out) {
            out.writeBytes(ByteBuffer.allocate(4).putInt(size()).array());
            out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
            out.writeBytes(content.toByteArray());
        }
    }
}
