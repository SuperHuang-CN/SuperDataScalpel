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
        Long cursorLagMillis
) {
    public StreamingSourceProgress {
        sourceNodeId = ExecutionContractValidation.required(sourceNodeId, 100, "来源节点 ID");
        sourceSignature = ExecutionContractValidation.sha256(sourceSignature);
        committedOffset = ExecutionContractValidation.required(committedOffset, 4000, "已提交 Offset");
        if (windowEnd == null || rowCount < 0 || pollDurationMillis < 0 || pollTime == null
                || cursorLagMillis != null && cursorLagMillis < 0
                || windowStart != null && windowStart.isAfter(windowEnd)) {
            throw new IllegalArgumentException("流式来源进度无效");
        }
    }
}
