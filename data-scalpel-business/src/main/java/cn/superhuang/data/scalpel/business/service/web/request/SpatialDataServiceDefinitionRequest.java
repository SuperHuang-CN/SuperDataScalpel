package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "空间数据服务绑定的单个已发布空间模型。")

public record SpatialDataServiceDefinitionRequest(
        @Schema(description = "提供物理空间表、Geometry 字段和 CRS 的已发布模型 UUID。")
        @NotNull UUID modelId
) {
}
