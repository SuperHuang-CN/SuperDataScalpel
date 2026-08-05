package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayerInputPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateModelWarehouseLayerRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,31}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Pattern(regexp = "#[0-9A-Fa-f]{6}", message = "颜色必须是 #RRGGBB 格式")
        String color,
        @Min(0) @Max(9999) int sortOrder,
        @Size(max = 32)
        @Pattern(
                regexp = "[a-z][a-z0-9_]{0,30}_",
                message = "模型编码前缀必须以小写字母开头、以下划线结尾，且只能包含小写字母、数字和下划线"
        )
        String modelCodePrefix,
        ModelWarehouseLayerInputPolicy inputLayerPolicy,
        List<UUID> allowedInputLayerIds
) {
}
