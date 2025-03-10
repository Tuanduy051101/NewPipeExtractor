package org.schabi.newpipe.extractor.services.youtube.optimization;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class YoutubeStreamLoader {
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final YoutubeStreamExtractor extractor;
    private final boolean isMeteredConnection;

    public YoutubeStreamLoader(YoutubeStreamExtractor extractor, boolean isMeteredConnection) {
        this.extractor = extractor;
        this.isMeteredConnection = isMeteredConnection;
    }

    public CompletableFuture<StreamLoadResult> loadVideo() {
        // Tải thông tin cần thiết trước
        return extractor.getEssentialInfo()
            .thenApplyAsync(essentialInfo -> {
                try {
                    // Chọn stream tốt nhất dựa trên điều kiện mạng
                    VideoStream videoStream = StreamSelector.selectBestVideoStream(
                        essentialInfo.videoStreams, 
                        isMeteredConnection
                    );
                    AudioStream audioStream = StreamSelector.selectBestAudioStream(
                        essentialInfo.audioStreams,
                        isMeteredConnection
                    );

                    // Tải thông tin phụ trong nền
                    extractor.getAdditionalInfo()
                        .thenAcceptAsync(additionalInfo -> {
                            // Xử lý thông tin phụ khi có
                        }, executor);

                    return new StreamLoadResult(
                        essentialInfo.title,
                        essentialInfo.thumbnails,
                        videoStream,
                        audioStream
                    );
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor);
    }

    public static class StreamLoadResult {
        public final String title;
        public final List<Image> thumbnails;
        public final VideoStream videoStream;
        public final AudioStream audioStream;

        public StreamLoadResult(String title, 
                              List<Image> thumbnails,
                              VideoStream videoStream,
                              AudioStream audioStream) {
            this.title = title;
            this.thumbnails = thumbnails;
            this.videoStream = videoStream;
            this.audioStream = audioStream;
        }
    }
} 