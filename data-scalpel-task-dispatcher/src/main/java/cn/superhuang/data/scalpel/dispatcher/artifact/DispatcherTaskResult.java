package cn.superhuang.data.scalpel.dispatcher.artifact;

import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.UserJobObservabilitySnapshot;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload.SkippedQualityRuleSnapshot;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import cn.superhuang.data.scalpel.contract.quality.ViolationMetric;
import java.math.BigDecimal;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

public record DispatcherTaskResult(
        Integer schemaVersion,
        UUID executionId,
        UUID runId,
        Integer attempt,
        State state,
        Instant startedAt,
        Instant endedAt,
        Long durationMs,
        Long affectedRows,
        List<NodeResult> nodeResults,
        ExecutionTaskType taskType,
        QualityResult qualityResult,
        UserJobObservabilitySnapshot userJobObservability,
        Error error
) {
    public DispatcherTaskResult {
        nodeResults = nodeResults == null ? List.of() : List.copyOf(nodeResults);
    }

    public record QualityResult(
            QualityConclusion conclusion,
            Long totalRules,
            Long passedRules,
            Long failedRules,
            Long skippedRules,
            Long checkedRows,
            List<QualityRuleResult> ruleResults,
            List<SkippedQualityRuleSnapshot> skippedRuleResults,
            QualityRuleTechnicalFailure technicalFailure
    ) {
        public QualityResult {
            ruleResults = ruleResults == null ? List.of() : List.copyOf(ruleResults);
            skippedRuleResults = skippedRuleResults == null ? List.of() : List.copyOf(skippedRuleResults);
        }
    }

    public record QualityRuleTechnicalFailure(
            UUID ruleId,
            String ruleName,
            ModelQualityRuleType ruleType,
            ModelQualityRuleSeverity severity,
            long durationMs,
            UUID diagnosticId
    ) {
    }

    public enum QualityRuleState { PASSED, FAILED }

    public record QualityRuleResult(
            UUID ruleId,
            String ruleName,
            ModelQualityRuleType ruleType,
            ModelQualityRuleSeverity severity,
            QualityRuleState state,
            long durationMs,
            QualityMetric metric,
            QualitySample sample
    ) {
        public QualityRuleResult(
                UUID ruleId, String ruleName, ModelQualityRuleType ruleType,
                ModelQualityRuleSeverity severity, QualityRuleState state,
                long durationMs, QualityMetric metric
        ) {
            this(ruleId, ruleName, ruleType, severity, state, durationMs, metric, null);
        }
    }

    public enum QualitySampleStatus { NOT_FAILED, NOT_APPLICABLE, DISABLED, AVAILABLE }

    public record QualitySample(
            QualitySampleStatus status,
            Long sampledRows,
            Long violationRows,
            Boolean truncated,
            Long sizeBytes,
            String sha256,
            Boolean rowLocatable,
            List<QualitySampleColumn> columns
    ) {
        public QualitySample {
            columns = columns == null ? List.of() : List.copyOf(columns);
        }
    }

    public record QualitySampleColumn(
            UUID fieldId,
            String code,
            String name,
            PlatformTypeDefinition type,
            boolean primaryKey,
            boolean diagnostic
    ) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = ViolationMetricResult.class, name = "VIOLATION"),
            @JsonSubTypes.Type(value = RowCountMetricResult.class, name = "ROW_COUNT"),
            @JsonSubTypes.Type(value = FreshnessMetricResult.class, name = "FRESHNESS")
    })
    public sealed interface QualityMetric permits
            ViolationMetricResult, RowCountMetricResult, FreshnessMetricResult {
    }

    public record ViolationMetricResult(long violationCount, BigDecimal violationPercent,
                                        ViolationMetric toleranceMetric, BigDecimal toleranceValue)
            implements QualityMetric {
    }

    public record RowCountMetricResult(long actualRows, long minimumRows) implements QualityMetric {
    }

    public record FreshnessMetricResult(Instant maximumValue, Long actualDelayMinutes,
                                        long maximumDelayMinutes) implements QualityMetric {
    }

    public enum State {
        ACCEPTED, RUNNING, CANCEL_REQUESTED, SUCCESS, FAILED, TIMED_OUT, CANCELLED;

        public boolean terminal() {
            return this == SUCCESS || this == FAILED || this == TIMED_OUT || this == CANCELLED;
        }
    }

    public enum NodeState { SUCCESS, FAILED }

    public record NodeResult(
            String nodeId,
            String nodeType,
            String nodeName,
            NodeState state,
            ExecutionFailurePhase phase,
            Instant startedAt,
            Instant endedAt,
            Long durationMs,
            Long rowsWritten,
            Metrics metrics,
            String message,
            Error error
    ) { }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SnapshotSyncMetrics.class, name = "SNAPSHOT_SYNC")
    })
    public sealed interface Metrics permits SnapshotSyncMetrics {
    }

    public record SnapshotSyncMetrics(
            long sourceRows,
            long targetRows,
            long insertedRows,
            long updatedRows,
            long deletedRows,
            long unchangedRows,
            long retainedTargetOnlyRows
    ) implements Metrics {
        long rowsWritten() {
            return insertedRows + updatedRows + deletedRows;
        }
    }

    public record Error(
            String code,
            String message,
            ExecutionErrorCategory category,
            boolean retryable,
            String nodeId,
            String nodeType,
            String nodeName,
            ExecutionFailurePhase phase,
            String sqlState,
            UUID diagnosticId
    ) { }
}
