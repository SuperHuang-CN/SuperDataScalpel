package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "模型质量任务的当前定义及可执行规则摘要。")

public record ModelQualityTaskDefinitionResponse(
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "是否已经保存目标模型和样本上限定义；true 不保证当前存在可执行规则或已经满足发布条件。")
        boolean configured,
        @Schema(description = "当前定义版本；尚未配置时为空。")
        Integer version,
        @Schema(description = "执行质量检查的目标模型及固定 Schema 版本；未配置时为空。")
        TaskModelReferenceResponse targetModel,
        @Schema(description = "每条规则最多保留的失败样本行数，范围 0 到 1000；0 表示不保存失败样本。未配置定义时返回默认值 100。")
        int failureSampleLimit,
        @Schema(description = "查询时目标模型上启用、未失效且当前引用模型数据源和码表依赖可用的规则数量；规则独立维护，因此该值可在定义版本不变时变化。该查询不打开目标模型的数据源连接。")
        long executableRuleCount,
        @Schema(description = "查询时目标模型上不会执行的规则数量，包括已停用、已失效、引用模型数据源不可用或码表停用/缺失的规则。")
        long skippedRuleCount,
        @Schema(description = "查询时不会执行的规则及原因；没有时为空列表。")
        List<SkippedRule> skippedRules,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public ModelQualityTaskDefinitionResponse {
        skippedRules = skippedRules == null ? List.of() : List.copyOf(skippedRules);
    }

    public static ModelQualityTaskDefinitionResponse unconfigured(UUID taskId) {
        return new ModelQualityTaskDefinitionResponse(taskId, false, null, null, 100, 0, 0, List.of(), null);
    }

    @Schema(description = "因当前模型或规则能力限制而不会执行的质量规则。")

    public record SkippedRule(
            @Schema(description = "质量规则 UUID。")
            UUID ruleId,
            @Schema(description = "质量规则名称。")
            String ruleName,
            @Schema(description = "跳过或拒绝该项的具体原因。")
            String reason
    ) {
    }
}
