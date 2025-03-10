package org.schabi.newpipe.extractor.stream;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Lớp quản lý cache cho thông tin video
 * Giúp tránh tải lại dữ liệu không cần thiết và tăng tốc độ phát video
 */
public class StreamInfoCache {
    private static final long CACHE_EXPIRATION_MS = 30 * 60 * 1000; // 30 phút
    private static final int MAX_CACHE_SIZE = 100; // Số lượng video tối đa trong cache
    
    // Cache cho thông tin video cơ bản (để phát nhanh)
    private static final Map<String, CacheEntry<StreamInfo>> basicInfoCache = new ConcurrentHashMap<>();
    
    // Cache cho thông tin video đầy đủ
    private static final Map<String, CacheEntry<StreamInfo>> fullInfoCache = new ConcurrentHashMap<>();
    
    // Thread pool để tải thông tin trong nền
    private static final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(3);
    
    /**
     * Lấy thông tin cơ bản của video từ cache hoặc tải mới nếu chưa có
     */
    public static StreamInfo getBasicInfo(int serviceId, String url, 
            Localization localization, ContentCountry contentCountry) 
            throws IOException, ExtractionException {
        
        final String cacheKey = getCacheKey(serviceId, url);
        
        // Kiểm tra cache thông tin đầy đủ trước
        CacheEntry<StreamInfo> fullInfoEntry = fullInfoCache.get(cacheKey);
        if (fullInfoEntry != null && !fullInfoEntry.isExpired()) {
            return fullInfoEntry.getData();
        }
        
        // Kiểm tra cache thông tin cơ bản
        CacheEntry<StreamInfo> basicInfoEntry = basicInfoCache.get(cacheKey);
        if (basicInfoEntry != null && !basicInfoEntry.isExpired()) {
            return basicInfoEntry.getData();
        }
        
        // Nếu không có trong cache, tải thông tin cơ bản
        StreamInfo basicInfo = StreamInfo.getBasicInfo(serviceId, url, localization, contentCountry);
        
        // Lưu vào cache
        basicInfoCache.put(cacheKey, new CacheEntry<>(basicInfo));
        
        // Tải thông tin đầy đủ trong nền
        loadFullInfoInBackground(basicInfo, serviceId, url, localization, contentCountry);
        
        return basicInfo;
    }
    
    /**
     * Lấy thông tin đầy đủ của video từ cache hoặc tải mới nếu chưa có
     */
    public static StreamInfo getFullInfo(int serviceId, String url, 
            Localization localization, ContentCountry contentCountry) 
            throws IOException, ExtractionException {
        
        final String cacheKey = getCacheKey(serviceId, url);
        
        // Kiểm tra cache thông tin đầy đủ
        CacheEntry<StreamInfo> fullInfoEntry = fullInfoCache.get(cacheKey);
        if (fullInfoEntry != null && !fullInfoEntry.isExpired()) {
            return fullInfoEntry.getData();
        }
        
        // Nếu không có trong cache, tải thông tin đầy đủ
        StreamInfo fullInfo = StreamInfo.getInfo(serviceId, url, localization, contentCountry);
        
        // Lưu vào cache
        fullInfoCache.put(cacheKey, new CacheEntry<>(fullInfo));
        
        return fullInfo;
    }
    
    /**
     * Tải trước thông tin cơ bản cho một danh sách video
     */
    public static void preloadBasicInfo(int serviceId, List<String> urls, 
            Localization localization, ContentCountry contentCountry) {
        
        for (String url : urls) {
            final String cacheKey = getCacheKey(serviceId, url);
            
            // Bỏ qua nếu đã có trong cache
            if (basicInfoCache.containsKey(cacheKey) || fullInfoCache.containsKey(cacheKey)) {
                continue;
            }
            
            // Tải thông tin cơ bản trong nền
            backgroundExecutor.submit(() -> {
                try {
                    StreamInfo basicInfo = StreamInfo.getBasicInfo(serviceId, url, localization, contentCountry);
                    basicInfoCache.put(cacheKey, new CacheEntry<>(basicInfo));
                } catch (Exception e) {
                    // Bỏ qua lỗi khi tải trước
                }
            });
        }
    }
    
    /**
     * Tải thông tin đầy đủ trong nền sau khi đã có thông tin cơ bản
     */
    private static void loadFullInfoInBackground(StreamInfo basicInfo, int serviceId, String url, 
            Localization localization, ContentCountry contentCountry) {
        
        final String cacheKey = getCacheKey(serviceId, url);
        
        backgroundExecutor.submit(() -> {
            try {
                // Nếu đã có thông tin cơ bản, tải thêm thông tin còn lại
                if (basicInfo.isBasicInfoOnly()) {
                    basicInfo.loadRemainingInfo();
                    
                    // Cập nhật cache với thông tin đầy đủ
                    fullInfoCache.put(cacheKey, new CacheEntry<>(basicInfo));
                    basicInfoCache.remove(cacheKey); // Xóa khỏi cache cơ bản
                }
            } catch (Exception e) {
                // Bỏ qua lỗi khi tải trong nền
            }
        });
    }
    
    /**
     * Xóa các mục hết hạn khỏi cache
     */
    public static void cleanExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        
        // Xóa các mục hết hạn từ cache thông tin cơ bản
        basicInfoCache.entrySet().removeIf(entry -> 
                entry.getValue().getTimestamp() + CACHE_EXPIRATION_MS < currentTime);
        
        // Xóa các mục hết hạn từ cache thông tin đầy đủ
        fullInfoCache.entrySet().removeIf(entry -> 
                entry.getValue().getTimestamp() + CACHE_EXPIRATION_MS < currentTime);
    }
    
    /**
     * Xóa toàn bộ cache
     */
    public static void clearCache() {
        basicInfoCache.clear();
        fullInfoCache.clear();
    }
    
    /**
     * Tạo khóa cache từ serviceId và URL
     */
    private static String getCacheKey(int serviceId, String url) {
        return serviceId + ":" + url;
    }
    
    /**
     * Lớp đại diện cho một mục trong cache
     */
    private static class CacheEntry<T> {
        private final T data;
        private final long timestamp;
        
        public CacheEntry(T data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        public T getData() {
            return data;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS;
        }
    }
    
    /**
     * Đóng thread pool khi không cần thiết nữa
     */
    public static void shutdown() {
        backgroundExecutor.shutdown();
        try {
            if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            backgroundExecutor.shutdownNow();
        }
    }
} 