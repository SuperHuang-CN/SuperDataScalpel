package cn.superhuang.data.scalpel.business.quality.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleDefinition;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;

import java.util.List;

@Schema(description = "根据模型字段元数据确定性生成、且尚未存在同名或同语义规则的建议；不是模型推理结果，也不会自动保存。")
public record ModelQualityRuleSuggestionResponse(
        @Schema(description = "当前建议的语义 key，用于批量采纳。通常由规则类型和字段 UUID 组成；条件规则还包含操作符和值，字段比较包含比较符，引用存在包含目标模型和映射。阈值通常不属于 key；模型字段变化后应重新查询。")
        String key,
        @Schema(description = "建议创建的规则名称。")
        String name,
        @Schema(description = "产生建议的元数据依据，例如主键字段、非空字段、码表绑定、时间键或 Geometry 类型。")
        String reason,
        @Schema(description = "建议的规则类型。")
        ModelQualityRuleType ruleType,
        @Schema(description = "建议的严重程度。")
        ModelQualityRuleSeverity severity,
        @Schema(description = "建议的完整多态规则定义；采纳后可再编辑。")
        ModelQualityRuleDefinition definition,
        @Schema(description = "建议规则引用的模型字段摘要。")
        List<ModelQualityRuleFieldResponse> fields
) {
}
