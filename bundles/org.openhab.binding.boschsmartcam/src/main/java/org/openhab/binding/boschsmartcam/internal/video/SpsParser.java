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
import java.io.IOException;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * Reads the picture size from an H.264 sequence parameter set (ITU-T H.264, 7.3.2.1.1). That is the only thing the
 * container needs from it, everything else is passed on untouched.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public final class SpsParser {

    /**
     * Profiles whose SPS carries the chroma format and the scaling lists.
     */
    private static final Set<Integer> HIGH_PROFILES = Set.of(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134,
            135);

    public record Dimensions(int width, int height) {
    }

    private SpsParser() {
    }

    /**
     * @param sps the SPS with its NAL header
     */
    public static Dimensions dimensions(byte[] sps) throws IOException {
        BitReader bits = new BitReader(unescape(sps));
        bits.skip(8); // NAL header
        int profile = bits.read(8);
        bits.skip(16); // constraint flags and level
        bits.readUe(); // seq_parameter_set_id
        int chromaFormat = 1;
        if (HIGH_PROFILES.contains(profile)) {
            chromaFormat = bits.readUe();
            if (chromaFormat == 3) {
                bits.skip(1); // separate_colour_plane_flag
            }
            bits.readUe(); // bit_depth_luma_minus8
            bits.readUe(); // bit_depth_chroma_minus8
            bits.skip(1); // qpprime_y_zero_transform_bypass_flag
            if (bits.read(1) == 1) {
                for (int i = 0; i < (chromaFormat == 3 ? 12 : 8); i++) {
                    if (bits.read(1) == 1) {
                        skipScalingList(bits, i < 6 ? 16 : 64);
                    }
                }
            }
        }
        bits.readUe(); // log2_max_frame_num_minus4
        int pocType = bits.readUe();
        if (pocType == 0) {
            bits.readUe(); // log2_max_pic_order_cnt_lsb_minus4
        } else if (pocType == 1) {
            bits.skip(1);
            bits.readSe();
            bits.readSe();
            int cycle = bits.readUe();
            for (int i = 0; i < cycle; i++) {
                bits.readSe();
            }
        }
        bits.readUe(); // max_num_ref_frames
        bits.skip(1); // gaps_in_frame_num_value_allowed_flag
        int widthInMbs = bits.readUe() + 1;
        int heightInMapUnits = bits.readUe() + 1;
        int frameMbsOnly = bits.read(1);
        if (frameMbsOnly == 0) {
            bits.skip(1); // mb_adaptive_frame_field_flag
        }
        bits.skip(1); // direct_8x8_inference_flag
        int cropLeft = 0;
        int cropRight = 0;
        int cropTop = 0;
        int cropBottom = 0;
        if (bits.read(1) == 1) {
            cropLeft = bits.readUe();
            cropRight = bits.readUe();
            cropTop = bits.readUe();
            cropBottom = bits.readUe();
        }
        int cropUnitX = chromaFormat == 1 || chromaFormat == 2 ? 2 : 1;
        int cropUnitY = (chromaFormat == 1 ? 2 : 1) * (2 - frameMbsOnly);
        int width = widthInMbs * 16 - (cropLeft + cropRight) * cropUnitX;
        int height = heightInMapUnits * 16 * (2 - frameMbsOnly) - (cropTop + cropBottom) * cropUnitY;
        return new Dimensions(width, height);
    }

    private static void skipScalingList(BitReader bits, int size) throws IOException {
        int last = 8;
        int next = 8;
        for (int j = 0; j < size; j++) {
            if (next != 0) {
                next = (last + bits.readSe() + 256) % 256;
            }
            last = next == 0 ? last : next;
        }
    }

    /**
     * Removes the emulation prevention bytes, the {@code 03} in {@code 00 00 03}.
     */
    static byte[] unescape(byte[] nal) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(nal.length);
        int zeros = 0;
        for (byte b : nal) {
            if (zeros >= 2 && b == 3) {
                zeros = 0;
                continue;
            }
            out.write(b);
            zeros = b == 0 ? zeros + 1 : 0;
        }
        return out.toByteArray();
    }

    private static class BitReader {
        private final byte[] data;
        private int position;

        BitReader(byte[] data) {
            this.data = data;
        }

        int read(int count) throws IOException {
            int value = 0;
            for (int i = 0; i < count; i++) {
                if (position >= data.length * 8) {
                    throw new IOException("The SPS ends early");
                }
                value = (value << 1) | ((data[position / 8] >> (7 - position % 8)) & 1);
                position++;
            }
            return value;
        }

        void skip(int count) throws IOException {
            read(count);
        }

        int readUe() throws IOException {
            int zeros = 0;
            while (read(1) == 0) {
                if (++zeros > 31) {
                    throw new IOException("Broken Exp-Golomb code in the SPS");
                }
            }
            return (1 << zeros) - 1 + read(zeros);
        }

        int readSe() throws IOException {
            int value = readUe();
            return (value & 1) == 1 ? (value + 1) / 2 : -(value / 2);
        }
    }
}
