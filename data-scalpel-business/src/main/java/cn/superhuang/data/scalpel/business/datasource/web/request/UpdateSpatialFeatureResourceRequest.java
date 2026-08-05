package cn.superhuang.data.scalpel.business.datasource.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSpatialFeatureResourceRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull Boolean enabled
) {
}
