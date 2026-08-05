package cn.superhuang.data.scalpel.business.filedataset.web.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateFileDatasetTableSpatialReferenceRequest(
        @NotBlank @Pattern(regexp = "(?i)EPSG") String authority,
        @Min(1) int code
) {
}
