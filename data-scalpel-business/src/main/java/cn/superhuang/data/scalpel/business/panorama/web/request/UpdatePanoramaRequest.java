package cn.superhuang.data.scalpel.business.panorama.web.request;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.UUID;
import cn.superhuang.data.scalpel.business.panorama.domain.PanoramaValueMode;
public record UpdatePanoramaRequest(@NotBlank @Size(max = 255) String name, @Size(max = 10000) String description,
        UUID directoryId, @NotNull @Min(0) Long expectedContentVersion,
        @NotNull PanoramaValueMode timeMode, LocalDateTime captureTime, @Size(max = 10) String captureOffset,
        @NotNull PanoramaValueMode locationMode,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude) {}
