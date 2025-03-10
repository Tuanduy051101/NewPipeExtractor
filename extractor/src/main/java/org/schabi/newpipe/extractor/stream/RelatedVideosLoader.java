package org.schabi.newpipe.extractor.stream;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lớp quản lý việc tải trước các video liên quan
 * Giúp chuẩn bị sẵn dữ liệu cho lần xem tiếp theo
 */
public class RelatedVideosLoader {
    private static final int MAX_RELATED_VIDEOS_TO_LOAD = 5; // Số lượng video liên quan tối đa để tải trước
    private static final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2);
    
    /**
     * Tải danh sách các video liên quan và tải trước thông tin cơ bản của chúng
     */
    public static void loadAndPreloadRelatedVideos(int serviceId, String videoUrl) {
        backgroundExecutor.submit(() -> {
            try {
                // Lấy danh sách các video liên quan
                final StreamInfo streamInfo = StreamInfo.getInfo(serviceId, videoUrl);
                final List<String> relatedVideoUrls = extractRelatedVideoUrls(streamInfo);
                
                // Tải trước thông tin cơ bản cho các video liên quan
                if (!relatedVideoUrls.isEmpty()) {
                    StreamInfoCache.preloadBasicInfo(
                            serviceId, 
                            relatedVideoUrls, 
                            NewPipe.getPreferredLocalization(), 
                            NewPipe.getPreferredContentCountry());
                }
            } catch (Exception e) {
                // Bỏ qua lỗi khi tải trước
            }
        });
    }
    
    /**
     * Tải trước thông tin cơ bản cho các video trong danh sách phát
     */
    public static void preloadPlaylistVideos(int serviceId, String playlistUrl) {
        backgroundExecutor.submit(() -> {
            try {
                // Lấy danh sách các video trong playlist
                final StreamingService service = NewPipe.getService(serviceId);
                final LinkHandlerFactory linkHandlerFactory = service.getStreamLHFactory();
                
                // Lấy thông tin playlist
                final ListExtractor.InfoItemsPage<?> playlistItems = 
                        service.getPlaylistExtractor(playlistUrl).getInitialPage();
                
                // Lấy URL của các video trong playlist
                final List<String> videoUrls = new ArrayList<>();
                for (InfoItem item : playlistItems.getItems()) {
                    if (item instanceof StreamInfoItem) {
                        videoUrls.add(((StreamInfoItem) item).getUrl());
                        
                        // Giới hạn số lượng video để tải trước
                        if (videoUrls.size() >= MAX_RELATED_VIDEOS_TO_LOAD) {
                            break;
                        }
                    }
                }
                
                // Tải trước thông tin cơ bản cho các video trong playlist
                if (!videoUrls.isEmpty()) {
                    StreamInfoCache.preloadBasicInfo(
                            serviceId, 
                            videoUrls, 
                            NewPipe.getPreferredLocalization(), 
                            NewPipe.getPreferredContentCountry());
                }
            } catch (Exception e) {
                // Bỏ qua lỗi khi tải trước
            }
        });
    }
    
    /**
     * Trích xuất URL của các video liên quan từ StreamInfo
     */
    private static List<String> extractRelatedVideoUrls(StreamInfo streamInfo) {
        final List<String> relatedVideoUrls = new ArrayList<>();
        
        if (streamInfo.getRelatedItems() != null) {
            for (InfoItem item : streamInfo.getRelatedItems()) {
                if (item instanceof StreamInfoItem) {
                    relatedVideoUrls.add(((StreamInfoItem) item).getUrl());
                    
                    // Giới hạn số lượng video để tải trước
                    if (relatedVideoUrls.size() >= MAX_RELATED_VIDEOS_TO_LOAD) {
                        break;
                    }
                }
            }
        }
        
        return relatedVideoUrls;
    }
    
    /**
     * Đóng thread pool khi không cần thiết nữa
     */
    public static void shutdown() {
        backgroundExecutor.shutdown();
    }
} 