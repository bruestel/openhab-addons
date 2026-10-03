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

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests reading the stream description, with the SDP an Eyes Outdoor Camera II sent; address and parameter sets
 * are replaced.
 *
 * @author Jonas Brüstel - Initial contribution
 */
@NonNullByDefault
public class SdpTest {

    private static final String URL = "rtsp://192.168.1.42:9554/rtsp_tunnel?line=1&inst=1&enableaudio=1";

    private static final String CAMERA_SDP = """
            v=0\r
            o=- 0 0 IN IP4 192.168.1.42\r
            s=LIVE VIEW\r
            c=IN IP4 0.0.0.0\r
            t=0 0\r
            a=control:rtsp://192.168.1.42:9554/rtsp_tunnel?line=1&inst=1&enableaudio=1\r
            m=video 0 RTP/AVP 35\r
            a=rtpmap:35 H264/90000\r
            a=rtpmap:102 H265/90000\r
            a=control:rtsp://192.168.1.42:9554/rtsp_tunnel?line=1&inst=1&enableaudio=1&stream=video\r
            a=recvonly\r
            a=fmtp:35 packetization-mode=1;profile-level-id=4d4028;sprop-parameter-sets=Z01AKJWgHgCJ+VA=,aO48gA==\r
            m=audio 0 RTP/AVP 96\r
            a=rtpmap:96 mpeg4-generic/16000/1\r
            a=fmtp:96 streamtype=5; profile-level-id=5; mode=AAC-hbr; config=1408; SizeLength=13; IndexLength=3; IndexDeltaLength=3\r
            a=control:rtsp://192.168.1.42:9554/rtsp_tunnel?line=1&inst=1&enableaudio=1&stream=audio\r
            a=recvonly\r
            """;

    @Test
    public void cameraDescriptionIsRead() {
        List<Sdp.Media> media = Sdp.parse(CAMERA_SDP, URL);

        assertEquals(2, media.size());
        Sdp.Media video = media.get(0);
        assertEquals("video", video.type());
        assertEquals(35, video.payloadType());
        // the second rtpmap belongs to a payload type the m= line does not offer
        assertEquals("H264", video.encoding());
        assertEquals(90_000, video.clockRate());
        assertEquals("Z01AKJWgHgCJ+VA=,aO48gA==", video.parameter("sprop-parameter-sets"));
        assertEquals(URL + "&stream=video", video.control());

        Sdp.Media audio = media.get(1);
        assertEquals("MPEG4-GENERIC", audio.encoding());
        assertEquals(16_000, audio.clockRate());
        assertEquals(1, audio.channels());
        assertEquals("1408", audio.parameter("config"));
        assertEquals("13", audio.parameter("SizeLength"));
        assertEquals(URL + "&stream=audio", audio.control());
    }

    @Test
    public void relativeControlIsAppendedLikeFfmpegDoes() {
        assertEquals(URL + "/trackID=1", Sdp.resolve(URL, null, "trackID=1"));
        assertEquals("rtsp://host/path/trackID=1", Sdp.resolve("rtsp://host/path/", null, "trackID=1"));
        assertEquals(URL, Sdp.resolve(URL, "*", null));
    }
}
