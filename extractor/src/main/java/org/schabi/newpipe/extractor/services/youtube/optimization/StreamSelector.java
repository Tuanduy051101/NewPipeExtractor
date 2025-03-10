package org.schabi.newpipe.extractor.services.youtube.optimization;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;
import java.util.Optional;

public class StreamSelector {
    private static final int DEFAULT_VIDEO_QUALITY = 720;
    private static final int MOBILE_VIDEO_QUALITY = 480;
    private static final int LOW_BANDWIDTH_QUALITY = 360;

    public static VideoStream selectBestVideoStream(List<VideoStream> streams, boolean isMeteredConnection) {
        int targetQuality = isMeteredConnection ? MOBILE_VIDEO_QUALITY : DEFAULT_VIDEO_QUALITY;
        return findClosestQualityStream(streams, targetQuality);
    }

    public static AudioStream selectBestAudioStream(List<AudioStream> streams, boolean isMeteredConnection) {
        // Sort by bitrate, higher first
        Optional<AudioStream> bestStream = streams.stream()
            .sorted((s1, s2) -> Long.compare(s2.getAverageBitrate(), s1.getAverageBitrate()))
            .findFirst();
            
        return bestStream.orElse(streams.get(0));
    }

    private static VideoStream findClosestQualityStream(List<VideoStream> streams, int targetQuality) {
        return streams.stream()
            .min((s1, s2) -> {
                int q1 = Math.abs(getQuality(s1) - targetQuality);
                int q2 = Math.abs(getQuality(s2) - targetQuality);
                return Integer.compare(q1, q2);
            })
            .orElse(streams.get(0));
    }

    private static int getQuality(VideoStream stream) {
        try {
            return Integer.parseInt(stream.getResolution().replace("p", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
} 