package org.schabi.newpipe.extractor.services.youtube.optimization;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SmartStreamCache {
    private static final long DEFAULT_CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    private static final int MAX_CACHE_SIZE = 100;

    private final Map<String, EssentialStreamInfo> essentialInfoCache;
    private final Map<String, AdditionalStreamInfo> additionalInfoCache;

    public SmartStreamCache() {
        this.essentialInfoCache = new ConcurrentHashMap<>();
        this.additionalInfoCache = new ConcurrentHashMap<>();
    }

    public void cacheEssentialInfo(String videoId, EssentialStreamInfo info) {
        if (essentialInfoCache.size() >= MAX_CACHE_SIZE) {
            // Remove oldest entries if cache is full
            String oldestKey = essentialInfoCache.keySet().stream()
                .filter(key -> essentialInfoCache.get(key).isExpired(DEFAULT_CACHE_DURATION_MS))
                .findFirst()
                .orElse(essentialInfoCache.keySet().iterator().next());
            essentialInfoCache.remove(oldestKey);
        }
        essentialInfoCache.put(videoId, info);
    }

    public void cacheAdditionalInfo(String videoId, AdditionalStreamInfo info) {
        if (additionalInfoCache.size() >= MAX_CACHE_SIZE) {
            // Remove an old entry if cache is full
            String oldestKey = additionalInfoCache.keySet().iterator().next();
            additionalInfoCache.remove(oldestKey);
        }
        additionalInfoCache.put(videoId, info);
    }

    public EssentialStreamInfo getEssentialInfo(String videoId) {
        EssentialStreamInfo info = essentialInfoCache.get(videoId);
        if (info != null && !info.isExpired(DEFAULT_CACHE_DURATION_MS)) {
            return info;
        }
        essentialInfoCache.remove(videoId);
        return null;
    }

    public AdditionalStreamInfo getAdditionalInfo(String videoId) {
        return additionalInfoCache.get(videoId);
    }

    public void clearCache() {
        essentialInfoCache.clear();
        additionalInfoCache.clear();
    }
} 