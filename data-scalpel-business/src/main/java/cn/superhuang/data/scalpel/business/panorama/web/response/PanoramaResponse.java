package cn.superhuang.data.scalpel.business.panorama.web.response;

import java.util.UUID;
import java.time.Instant;
import java.time.LocalDateTime;
import cn.superhuang.data.scalpel.business.panorama.domain.*;
public record PanoramaResponse(UUID id, String name, String description, UUID directoryId,
        LocalDateTime captureTime, String captureOffset, Double latitude, Double longitude,
        PanoramaValueMode timeMode, PanoramaValueMode locationMode, long contentVersion,
        PanoramaProcessingStatus processingStatus, String processingError,
        PanoramaContentResponse currentContent, PanoramaContentResponse candidateContent,
        Instant createdAt, Instant updatedAt) {}
