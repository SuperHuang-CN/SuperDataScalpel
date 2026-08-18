package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;

import java.util.UUID;

public record QualityRuleExecutionResult(
        UUID ruleId,
        String ruleName,
        ModelQualityRuleType ruleType,
        ModelQualityRuleSeverity severity,
        QualityRuleState state,
        long durationMs,
        QualityRuleMetric metric,
        QualitySampleResult sample
) {
    public QualityRuleExecutionResult {
        if (ruleId == null || ruleName == null || ruleName.isBlank() || ruleName.length() > 100
                || ruleType == null || severity == null || state == null || durationMs < 0
                || metric == null || sample == null) {
            throw new IllegalArgumentException("模型质检规则结果无效");
        }
        boolean nonRowLevel = ruleType == ModelQualityRuleType.ROW_COUNT
                || ruleType == ModelQualityRuleType.FRESHNESS;
        if (state == QualityRuleState.PASSED && sample.status() != QualitySampleStatus.NOT_FAILED
                || state == QualityRuleState.FAILED && nonRowLevel
                && sample.status() != QualitySampleStatus.NOT_APPLICABLE
                || state == QualityRuleState.FAILED && !nonRowLevel
                && sample.status() != QualitySampleStatus.DISABLED
                && sample.status() != QualitySampleStatus.AVAILABLE
                || sample.status() == QualitySampleStatus.AVAILABLE
                && (!(metric instanceof QualityRuleMetric.Violation violation)
                || sample.violationRows() != violation.violationCount())) {
            throw new IllegalArgumentException("模型质检规则状态与样本状态不一致");
        }
    }

    public QualityRuleExecutionResult(
            UUID ruleId, String ruleName, ModelQualityRuleType ruleType,
            ModelQualityRuleSeverity severity, QualityRuleState state,
            long durationMs, QualityRuleMetric metric
    ) {
        this(ruleId, ruleName, ruleType, severity, state, durationMs, metric,
                QualitySampleResult.state(state == QualityRuleState.PASSED
                        ? QualitySampleStatus.NOT_FAILED
                        : ruleType == ModelQualityRuleType.ROW_COUNT || ruleType == ModelQualityRuleType.FRESHNESS
                        ? QualitySampleStatus.NOT_APPLICABLE : QualitySampleStatus.DISABLED));
    }
}
