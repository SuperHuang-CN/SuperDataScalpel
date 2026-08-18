package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload.SkippedQualityRuleSnapshot;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;

import java.util.List;

public record ModelQualityExecutionResult(
        QualityConclusion conclusion,
        Long totalRules,
        Long passedRules,
        Long failedRules,
        Long skippedRules,
        Long checkedRows,
        List<QualityRuleExecutionResult> ruleResults,
        List<SkippedQualityRuleSnapshot> skippedRuleResults,
        QualityRuleTechnicalFailure technicalFailure
) {
    public ModelQualityExecutionResult {
        ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);
        skippedRuleResults = skippedRuleResults == null ? List.of() : List.copyOf(skippedRuleResults);
        long actualPassed = ruleResults.stream()
                .filter(result -> result != null && result.state() == QualityRuleState.PASSED).count();
        long actualFailed = ruleResults.stream()
                .filter(result -> result != null && result.state() == QualityRuleState.FAILED).count();
        if (conclusion == null) {
            if (totalRules != null || passedRules != null || failedRules != null || skippedRules != null
                    || checkedRows != null && checkedRows < 0
                    || technicalFailure == null && !ruleResults.isEmpty()) {
                throw new IllegalArgumentException("模型质检技术失败结果无效");
            }
        } else if (totalRules == null || passedRules == null || failedRules == null || skippedRules == null
                || checkedRows == null || totalRules < 1 || passedRules < 0 || failedRules < 0
                || skippedRules < 0 || checkedRows < 0 || technicalFailure != null
                || totalRules != passedRules + failedRules + skippedRules
                || ruleResults.size() != passedRules + failedRules
                || skippedRuleResults.size() != skippedRules || passedRules + failedRules == 0
                || actualPassed != passedRules || actualFailed != failedRules
                || conclusion == QualityConclusion.PASSED && failedRules != 0
                || conclusion == QualityConclusion.FAILED && failedRules == 0) {
            throw new IllegalArgumentException("模型质检执行结果汇总无效");
        }
    }

    public ModelQualityExecutionResult(
            QualityConclusion conclusion,
            long totalRules,
            long passedRules,
            long failedRules,
            long skippedRules,
            long checkedRows,
            List<QualityRuleExecutionResult> ruleResults,
            List<SkippedQualityRuleSnapshot> skippedRuleResults
    ) {
        this(conclusion, Long.valueOf(totalRules), Long.valueOf(passedRules), Long.valueOf(failedRules),
                Long.valueOf(skippedRules), Long.valueOf(checkedRows), ruleResults, skippedRuleResults, null);
    }

    public static ModelQualityExecutionResult technicalFailure(
            Long checkedRows,
            List<QualityRuleExecutionResult> completedRules,
            List<SkippedQualityRuleSnapshot> skippedRules,
            QualityRuleTechnicalFailure failure
    ) {
        return new ModelQualityExecutionResult(
                null, null, null, null, null, checkedRows, completedRules, skippedRules, failure);
    }
}
