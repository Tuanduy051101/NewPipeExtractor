package org.schabi.newpipe.extractor.services.youtube.optimization;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;

public class EssentialStreamInfo {
    public final List<VideoStream> videoStreams;
    public final List<AudioStream> audioStreams;
    public final String title;
    public final List<Image> thumbnails;
    public final String videoId;
    public final long timestamp;

    public EssentialStreamInfo(String videoId,
                             List<VideoStream> videoStreams,
                             List<AudioStream> audioStreams,
                             String title,
                             List<Image> thumbnails) {
        this.videoId = videoId;
        this.videoStreams = videoStreams;
        this.audioStreams = audioStreams;
        this.title = title;
        this.thumbnails = thumbnails;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isExpired(long maxAgeMs) {
        return System.currentTimeMillis() - timestamp > maxAgeMs;
    }
} 