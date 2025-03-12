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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A utility class for preloading YouTube video information.
 * This class manages a cache of preloaded video information and provides methods
 * to preload videos that are likely to be viewed next.
 */
public class YoutubeStreamPreloader {
    private static final Logger LOG = LoggerFactory.getLogger(YoutubeStreamPreloader.class);
    
    // Maximum number of videos to keep in the cache
    private static final int MAX_CACHE_SIZE = 10;
    
    // Maximum number of concurrent preloading tasks
    private static final int MAX_CONCURRENT_TASKS = 3;
    
    // Executor service for preloading tasks
    private final ExecutorService executor;
    
    // Cache of preloaded video information (LRU cache)
    private final Map<String, StreamInfo> preloadedVideos;
    
    // Map to track ongoing preload tasks
    private final Map<String, CompletableFuture<StreamInfo>> ongoingTasks;
    
    // Singleton instance
    private static YoutubeStreamPreloader instance;
    
    /**
     * Get the singleton instance of the preloader
     * @return the preloader instance
     */
    public static synchronized YoutubeStreamPreloader getInstance() {
        if (instance == null) {
            instance = new YoutubeStreamPreloader();
        }
        return instance;
    }
    
    /**
     * Private constructor to enforce singleton pattern
     */
    private YoutubeStreamPreloader() {
        // Use a thread pool with a bounded queue to limit concurrent tasks
        executor = new ThreadPoolExecutor(
                1, MAX_CONCURRENT_TASKS,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                r -> {
                    Thread t = new Thread(r, "YoutubePreloader");
                    t.setPriority(Thread.MIN_PRIORITY); // Use low priority for preloading
                    return t;
                },
                new ThreadPoolExecutor.DiscardPolicy() // Discard tasks when queue is full
        );
        
        // Use a synchronized LinkedHashMap with access order to implement LRU cache
        preloadedVideos = Collections.synchronizedMap(
                new LinkedHashMap<String, StreamInfo>(MAX_CACHE_SIZE + 1, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, StreamInfo> eldest) {
                        return size() > MAX_CACHE_SIZE;
                    }
                });
        
        ongoingTasks = new ConcurrentHashMap<>();
    }
    
    /**
     * Preload a list of video IDs in the background
     * @param videoIds list of video IDs to preload
     */
    public void preloadVideos(List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return;
        }
        
        // Limit the number of videos to preload
        final List<String> limitedIds = videoIds.size() > MAX_CACHE_SIZE 
                ? videoIds.subList(0, MAX_CACHE_SIZE) 
                : videoIds;
        
        for (String videoId : limitedIds) {
            // Skip if already cached or being preloaded
            if (preloadedVideos.containsKey(videoId) || ongoingTasks.containsKey(videoId)) {
                continue;
            }
            
            // Start preloading task
            CompletableFuture<StreamInfo> future = CompletableFuture.supplyAsync(() -> {
                try {
                    LOG.debug("Preloading video: {}", videoId);
                    return preloadVideo(videoId);
                } catch (Exception e) {
                    LOG.debug("Failed to preload video: {}", videoId, e);
                    throw new RuntimeException(e);
                } finally {
                    ongoingTasks.remove(videoId);
                }
            }, executor);
            
            ongoingTasks.put(videoId, future);
        }
    }
    
    /**
     * Get a preloaded video from the cache
     * @param videoId the video ID to retrieve
     * @return the preloaded StreamInfo or null if not in cache
     */
    public StreamInfo getPreloadedVideo(String videoId) {
        // Move the entry to the end of the LRU cache if it exists
        return preloadedVideos.get(videoId);
    }
    
    /**
     * Check if a video is preloaded or being preloaded
     * @param videoId the video ID to check
     * @return true if the video is preloaded or being preloaded
     */
    public boolean isVideoPreloaded(String videoId) {
        return preloadedVideos.containsKey(videoId) || ongoingTasks.containsKey(videoId);
    }
    
    /**
     * Wait for a preloaded video to be ready
     * @param videoId the video ID to wait for
     * @param timeoutMs maximum time to wait in milliseconds
     * @return the preloaded StreamInfo or null if not available
     */
    public StreamInfo waitForPreloadedVideo(String videoId, long timeoutMs) {
        // Check if already in cache
        StreamInfo info = preloadedVideos.get(videoId);
        if (info != null) {
            return info;
        }
        
        // Check if being preloaded
        CompletableFuture<StreamInfo> future = ongoingTasks.get(videoId);
        if (future != null) {
            try {
                return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                LOG.debug("Failed to wait for preloaded video: {}", videoId, e);
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * Preload a single video and add it to the cache
     * @param videoId the video ID to preload
     * @return the preloaded StreamInfo
     */
    private StreamInfo preloadVideo(String videoId) throws ExtractionException, IOException {
        final StreamingService service = NewPipe.getService(0); // YouTube is service 0
        final LinkHandlerFactory factory = service.getStreamLHFactory();
        
        try {
            final LinkHandler linkHandler = factory.fromId(videoId);
            final StreamExtractor extractor = service.getStreamExtractor(linkHandler);
            
            // Enable fast loading mode if the extractor supports it
            if (extractor instanceof YoutubeStreamExtractor) {
                ((YoutubeStreamExtractor) extractor).setFastLoadingMode(true);
            }
            
            // Fetch only essential data
            extractor.fetchPage();
            
            // Create StreamInfo with essential data
            final StreamInfo streamInfo = StreamInfo.getInfo(extractor);
            
            // Add to cache
            preloadedVideos.put(videoId, streamInfo);
            
            return streamInfo;
        } catch (ParsingException e) {
            throw new ExtractionException("Could not parse video ID: " + videoId, e);
        }
    }
    
    /**
     * Clear the preload cache
     */
    public void clearCache() {
        preloadedVideos.clear();
        
        // Cancel ongoing tasks
        for (CompletableFuture<StreamInfo> future : ongoingTasks.values()) {
            future.cancel(true);
        }
        ongoingTasks.clear();
    }
    
    /**
     * Shutdown the preloader
     */
    public void shutdown() {
        clearCache();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 