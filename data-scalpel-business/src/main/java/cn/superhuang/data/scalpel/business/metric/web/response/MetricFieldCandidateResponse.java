package cn.superhuang.data.scalpel.business.metric.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import java.util.UUID;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
@Schema(description = "指定模型中可供指标绑定选择的字段摘要。")
public record MetricFieldCandidateResponse(
        @Schema(description = "模型字段 UUID。")
        UUID id,
        @Schema(description = "模型字段稳定技术编码。")
        String code,
        @Schema(description = "模型字段显示名称。")
        String name,
        @Schema(description = "字段的数据库无关平台类型，用于判断能否作为数值、周期、维度或条件字段。")
        PlatformDataType fieldType
) {}
