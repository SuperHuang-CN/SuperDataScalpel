package cn.superhuang.data.scalpel.business.model.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "引用当前模型或字段的指标摘要")
public record DataModelReferenceMetricResponse(
        @Schema(description = "指标 UUID") UUID id,
        @Schema(description = "指标显示名称") String name,
        @Schema(description = "指标唯一编码") String code
) {
}
