package org.schabi.newpipe.extractor.services.youtube.optimization;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.Description;

import java.util.List;

public class AdditionalStreamInfo {
    public final String description;
    public final long viewCount;
    public final String uploaderName;
    public final String uploaderUrl;
    public final List<Image> uploaderAvatars;
    public final List<InfoItem> relatedStreams;
    public final long likeCount;
    public final boolean isLiveStream;
    public final String uploadDate;

    public AdditionalStreamInfo(String description,
                              long viewCount,
                              String uploaderName,
                              String uploaderUrl,
                              List<Image> uploaderAvatars,
                              List<InfoItem> relatedStreams,
                              long likeCount,
                              boolean isLiveStream,
                              String uploadDate) {
        this.description = description;
        this.viewCount = viewCount;
        this.uploaderName = uploaderName;
        this.uploaderUrl = uploaderUrl;
        this.uploaderAvatars = uploaderAvatars;
        this.relatedStreams = relatedStreams;
        this.likeCount = likeCount;
        this.isLiveStream = isLiveStream;
        this.uploadDate = uploadDate;
    }

    public boolean isExpired(long maxAgeMs) {
        return System.currentTimeMillis() - System.currentTimeMillis() > maxAgeMs;
    }
} 