package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;

import java.util.UUID;

/** Safe rule identity for a technical failure; it deliberately contains no field values. */
public record QualityRuleTechnicalFailure(
        UUID ruleId,
        String ruleName,
        ModelQualityRuleType ruleType,
        ModelQualityRuleSeverity severity,
        long durationMs,
        UUID diagnosticId
) {
    public QualityRuleTechnicalFailure {
        if (ruleId == null || ruleName == null || ruleName.isBlank() || ruleName.length() > 100
                || ruleType == null || severity == null || durationMs < 0 || diagnosticId == null) {
            throw new IllegalArgumentException("模型质检技术失败规则上下文无效");
        }
    }
}
