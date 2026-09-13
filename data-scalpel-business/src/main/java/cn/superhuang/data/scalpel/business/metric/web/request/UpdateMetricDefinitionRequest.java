package cn.superhuang.data.scalpel.business.metric.web.request;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.metric.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
@Schema(description = "整体保存业务指标草稿口径，并通过指纹避免覆盖其他人的并发修改。")
public record UpdateMetricDefinitionRequest(
        @Schema(description = "完整指标业务口径、统计周期、空值规则、结果字段绑定和参考资源；保存草稿不会执行计算或发布。")
        @NotNull @Valid MetricDefinition definition,
        @Schema(description = "客户端读取到的草稿指纹；与当前草稿不一致时拒绝保存。")
        @NotBlank String expectedDraftFingerprint
) {}
