package cn.superhuang.data.scalpel.business.quality.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunExecutionErrorResponse;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import cn.superhuang.data.scalpel.contract.quality.ViolationMetric;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ModelQualityOverviewResponse(
        UUID modelId,
        RecentRun latestRun,
        EffectiveResult latestEffectiveResult,
        ResultDetailStatus resultDetailStatus,
        String resultDetailMessage,
        List<RuleResult> ruleResults
) {
    public ModelQualityOverviewResponse {
        ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);
    }

    public enum ResultDetailStatus { AVAILABLE, UNAVAILABLE, INVALID, NOT_AVAILABLE }

    public enum RuleState { PASSED, FAILED, SKIPPED }

    public enum SampleStatus { NOT_FAILED, NOT_APPLICABLE, DISABLED, AVAILABLE }

    public enum MetricKind { VIOLATION, ROW_COUNT, FRESHNESS }

    public record RecentRun(
            UUID runId,
            UUID taskId,
            String taskName,
            TaskRunTriggerType triggerType,
            TaskRunStatus status,
            Instant queuedAt,
            Instant startedAt,
            Instant endedAt,
            String message,
            TaskRunExecutionErrorResponse executionError
    ) {
    }

    public record EffectiveResult(
            UUID runId,
            UUID taskId,
            String taskName,
            QualityConclusion conclusion,
            long totalRules,
            long passedRules,
            long failedRules,
            long skippedRules,
            long checkedRows,
            Instant ruleSnapshotAt,
            Instant queuedAt,
            Instant endedAt
    ) {
    }

    public record RuleResult(
            UUID ruleId,
            String ruleName,
            ModelQualityRuleType ruleType,
            ModelQualityRuleSeverity severity,
            RuleState state,
            Long durationMs,
            Metric metric,
            String skipCode,
            String skipReason,
            Sample sample
    ) {
    }

    public record Metric(
            MetricKind kind,
            Long violationCount,
            BigDecimal violationPercent,
            ViolationMetric toleranceMetric,
            BigDecimal toleranceValue,
            Long actualRows,
            Long minimumRows,
            Instant maximumValue,
            Long actualDelayMinutes,
            Long maximumDelayMinutes
    ) {
        public static Metric violation(
                long violationCount,
                BigDecimal violationPercent,
                ViolationMetric toleranceMetric,
                BigDecimal toleranceValue
        ) {
            return new Metric(MetricKind.VIOLATION, violationCount, violationPercent,
                    toleranceMetric, toleranceValue, null, null, null, null, null);
        }

        public static Metric rowCount(long actualRows, long minimumRows) {
            return new Metric(MetricKind.ROW_COUNT, null, null, null, null,
                    actualRows, minimumRows, null, null, null);
        }

        public static Metric freshness(Instant maximumValue, Long actualDelayMinutes, long maximumDelayMinutes) {
            return new Metric(MetricKind.FRESHNESS, null, null, null, null,
                    null, null, maximumValue, actualDelayMinutes, maximumDelayMinutes);
        }
    }

    public record Sample(
            SampleStatus status,
            Long sampledRows,
            Long violationRows,
            Boolean truncated,
            Boolean rowLocatable
    ) {
    }
}
