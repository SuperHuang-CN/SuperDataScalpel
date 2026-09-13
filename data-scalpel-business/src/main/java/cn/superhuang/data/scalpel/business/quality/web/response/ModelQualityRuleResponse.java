package cn.superhuang.data.scalpel.business.quality.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.quality.domain.ModelQualityRule;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleDefinition;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "模型质量规则及其当前字段引用解释；规则定义本身不保存执行结果，实际检查由 SPARK_MODEL_QUALITY 任务完成。同一模型按名称和语义 key 分别判重，多数规则的阈值、边界和格式参数不参与语义 key。")
public record ModelQualityRuleResponse(
        @Schema(description = "质量规则稳定 UUID。")
        UUID id,
        @Schema(description = "模型 UUID。")
        UUID modelId,
        @Schema(description = "规则展示名称，在同一模型内忽略大小写唯一。")
        String name,
        @Schema(description = "规则用途或判定依据说明；未填写时为空。")
        String description,
        @Schema(description = "规则类型，由 definition.type 确定且创建后不可修改。")
        ModelQualityRuleType ruleType,
        @Schema(description = "规则未通过时的业务严重程度：CRITICAL、MAJOR 或 MINOR；质量失败不会把技术执行状态改为 FAILED。")
        ModelQualityRuleSeverity severity,
        @Schema(description = "是否参与后续质检；false 的规则在运行快照中以 RULE_DISABLED 跳过。模型字段或引用结构变化使规则失效时服务端会自动置为 false；结构恢复只清除失效标记，不会自动重新启用。")
        boolean enabled,
        @Schema(description = "规则因模型演进失效时的稳定原因码，例如 FIELD_MISSING、FIELD_TYPE_INCOMPATIBLE、CONDITION_VALUE_INCOMPATIBLE、DICTIONARY_UNBOUND、REFERENCE_MODEL_MISSING、REFERENCE_TARGET_FIELD_MISSING 或 REFERENCE_FIELD_TYPE_INCOMPATIBLE；当前结构兼容时为空。清空该值不会自动恢复 enabled。")
        String invalidCode,
        @Schema(description = "规则失效的可读原因；有效时为空。")
        String invalidReason,
        @Schema(description = "带 type 判别字段的完整多态规则定义；服务端会规范化字段顺序、范围边界和部分条件值。")
        ModelQualityRuleDefinition definition,
        @Schema(description = "definition 引用的当前模型源字段摘要；ROW_COUNT 没有字段引用，因此为空列表。")
        List<ModelQualityRuleFieldResponse> fields,
        @Schema(description = "REFERENCE_EXISTS 的目标模型及目标字段；其他规则为空，目标模型已删除时也为空并通过 invalidCode 解释。目标模型状态仅供展示，不决定规则能否保存；运行准备会另外检查目标模型绑定数据源是否可读。")
        ModelQualityRuleReferenceTargetResponse referenceTarget,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static ModelQualityRuleResponse from(
            ModelQualityRule rule,
            ModelQualityRuleDefinition definition,
            List<ModelQualityRuleFieldResponse> fields,
            ModelQualityRuleReferenceTargetResponse referenceTarget
    ) {
        return new ModelQualityRuleResponse(
                rule.getId(), rule.getModelId(), rule.getName(), rule.getDescription(), rule.getRuleType(),
                rule.getSeverity(), rule.isEnabled(), rule.getInvalidCode(), rule.getInvalidReason(),
                definition, fields, referenceTarget, rule.getCreatedAt(), rule.getUpdatedAt()
        );
    }
}
