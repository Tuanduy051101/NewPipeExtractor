package org.schabi.newpipe.extractor.services.youtube;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandler;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * A utility class for loading YouTube videos with optimized performance.
 * This class provides methods for fast loading of videos and automatic preloading
 * of related videos.
 */
public class YoutubeStreamLoader {
    private static final Logger LOG = LoggerFactory.getLogger(YoutubeStreamLoader.class);
    
    private final YoutubeStreamPreloader preloader;
    
    /**
     * Create a new YoutubeStreamLoader
     */
    public YoutubeStreamLoader() {
        this.preloader = YoutubeStreamPreloader.getInstance();
    }
    
    /**
     * Load a video with optimized performance
     * 
     * @param url the URL of the video to load
     * @param preloadRelated whether to preload related videos
     * @return a CompletableFuture that will be completed with the StreamInfo
     */
    public CompletableFuture<StreamInfo> loadVideo(String url, boolean preloadRelated) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final StreamingService service = NewPipe.getService(0); // YouTube is service 0
                final LinkHandlerFactory factory = service.getStreamLHFactory();
                final LinkHandler linkHandler = factory.fromUrl(url);
                final String videoId = linkHandler.getId();
                
                // Check if the video is already preloaded
                StreamInfo preloadedInfo = preloader.getPreloadedVideo(videoId);
                if (preloadedInfo != null) {
                    LOG.debug("Using preloaded video: {}", videoId);
                    
                    // If we need full data, load it now
                    if (preloadedInfo.getStreamExtractor() instanceof YoutubeStreamExtractor) {
                        YoutubeStreamExtractor extractor = 
                                (YoutubeStreamExtractor) preloadedInfo.getStreamExtractor();
                        
                        // Load full data if not already loaded
                        if (extractor.isFastLoadingMode()) {
                            extractor.fetchFullData();
                            // Update the StreamInfo with full data
                            preloadedInfo = StreamInfo.getInfo(extractor);
                        }
                    }
                    
                    // Preload related videos if requested
                    if (preloadRelated) {
                        preloadRelatedVideos(preloadedInfo);
                    }
                    
                    return preloadedInfo;
                }
                
                // Video not preloaded, load it now
                final StreamExtractor extractor = service.getStreamExtractor(linkHandler);
                
                // Enable fast loading mode if supported
                if (extractor instanceof YoutubeStreamExtractor) {
                    ((YoutubeStreamExtractor) extractor).setFastLoadingMode(true);
                }
                
                // Fetch essential data
                extractor.fetchPage();
                
                // Create StreamInfo with essential data
                final StreamInfo streamInfo = StreamInfo.getInfo(extractor);
                
                // Load full data in the background
                if (extractor instanceof YoutubeStreamExtractor) {
                    CompletableFuture.runAsync(() -> {
                        try {
                            ((YoutubeStreamExtractor) extractor).fetchFullData();
                        } catch (Exception e) {
                            LOG.debug("Failed to load full data for video: {}", videoId, e);
                        }
                    });
                }
                
                // Preload related videos if requested
                if (preloadRelated) {
                    preloadRelatedVideos(streamInfo);
                }
                
                return streamInfo;
            } catch (Exception e) {
                LOG.error("Failed to load video", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Preload related videos from a StreamInfo
     * 
     * @param streamInfo the StreamInfo containing related videos
     */
    private void preloadRelatedVideos(StreamInfo streamInfo) {
        try {
            // Extract video IDs from related items
            List<String> relatedVideoIds = streamInfo.getRelatedItems().stream()
                    .filter(item -> item instanceof StreamInfoItem)
                    .map(item -> ((StreamInfoItem) item).getUrl())
                    .map(url -> {
                        try {
                            final LinkHandlerFactory factory = 
                                    NewPipe.getService(0).getStreamLHFactory();
                            return factory.fromUrl(url).getId();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
            
            // Preload related videos
            if (!relatedVideoIds.isEmpty()) {
                preloader.preloadVideos(relatedVideoIds);
            }
        } catch (Exception e) {
            LOG.debug("Failed to preload related videos", e);
        }
    }
    
    /**
     * Get the preloader instance
     * 
     * @return the YoutubeStreamPreloader instance
     */
    public YoutubeStreamPreloader getPreloader() {
        return preloader;
    }
} 