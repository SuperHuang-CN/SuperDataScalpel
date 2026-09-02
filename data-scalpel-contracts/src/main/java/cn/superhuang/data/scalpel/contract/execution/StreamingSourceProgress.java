package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;

/** Sanitized progress of the unique unbounded input; raw Spark progress never crosses this boundary. */
public record StreamingSourceProgress(
        String sourceNodeId,
        String sourceSignature,
        String committedOffset,
        Instant windowStart,
        Instant windowEnd,
        long rowCount,
        long pollDurationMillis,
        Instant pollTime,
        Long cursorLagMillis,
        StreamingSourceKind sourceKind,
        Integer vGroupCount,
        Long batchOffsetSpan
) {
    public StreamingSourceProgress {
        sourceKind = sourceKind == null ? StreamingSourceKind.JDBC_INCREMENTAL : sourceKind;
        sourceNodeId = ExecutionContractValidation.required(sourceNodeId, 100, "来源节点 ID");
        sourceSignature = ExecutionContractValidation.sha256(sourceSignature);
        committedOffset = ExecutionContractValidation.required(committedOffset, 4000, "已提交 Offset");
        if (sourceKind == StreamingSourceKind.JDBC_INCREMENTAL && windowEnd == null
                || rowCount < 0 || pollDurationMillis < 0 || pollTime == null
                || cursorLagMillis != null && cursorLagMillis < 0
                || windowStart != null && windowEnd != null && windowStart.isAfter(windowEnd)
                || vGroupCount != null && vGroupCount < 1
                || batchOffsetSpan != null && batchOffsetSpan < 0
                || sourceKind == StreamingSourceKind.TDENGINE_TMQ
                && (vGroupCount == null || batchOffsetSpan == null)) {
            throw new IllegalArgumentException("流式来源进度无效");
        }
    }

    public StreamingSourceProgress(
            String sourceNodeId,
            String sourceSignature,
            String committedOffset,
            Instant windowStart,
            Instant windowEnd,
            long rowCount,
            long pollDurationMillis,
            Instant pollTime,
            Long cursorLagMillis
    ) {
        this(sourceNodeId, sourceSignature, committedOffset, windowStart, windowEnd,
                rowCount, pollDurationMillis, pollTime, cursorLagMillis,
                StreamingSourceKind.JDBC_INCREMENTAL, null, null);
    }
}
