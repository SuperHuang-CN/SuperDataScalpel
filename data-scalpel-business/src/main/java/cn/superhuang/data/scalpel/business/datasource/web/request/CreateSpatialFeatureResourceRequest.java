package cn.superhuang.data.scalpel.business.datasource.web.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSpatialFeatureResourceRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 1000) String remoteIdentifier,
        @Min(1) @Max(99_999_999) Integer outputEpsgCode
) {
}
