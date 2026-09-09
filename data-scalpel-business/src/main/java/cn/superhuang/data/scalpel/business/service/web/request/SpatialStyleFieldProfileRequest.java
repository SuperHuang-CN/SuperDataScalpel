package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SpatialStyleFieldProfileRequest(
        @NotBlank String fieldCode,
        @NotNull ProfileType profileType,
        @Min(1) @Max(50) Integer limit,
        ClassificationMethod classificationMethod,
        @Min(3) @Max(9) Integer classCount
) {
    public enum ProfileType {
        UNIQUE_VALUES,
        CLASS_BREAKS
    }

    public enum ClassificationMethod {
        EQUAL_INTERVAL,
        QUANTILE
    }
}
