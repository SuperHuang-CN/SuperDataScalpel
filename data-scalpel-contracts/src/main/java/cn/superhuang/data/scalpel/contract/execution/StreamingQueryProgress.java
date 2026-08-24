package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

/** Sanitized latest progress for one output query; Spark raw progress JSON never crosses module boundaries. */
public record StreamingQueryProgress(
        String outputNodeId,
        String outputWriteId,
        long batchId,
        long inputRows,
        double inputRowsPerSecond,
        double processedRowsPerSecond,
        long batchDurationMillis,
        Instant progressAt
) {
    public StreamingQueryProgress {
        outputNodeId = ExecutionContractValidation.required(outputNodeId, 100, "输出节点 ID");
        outputWriteId = ExecutionContractValidation.optional(outputWriteId, 100, "输出写入 ID");
        if (outputWriteId != null) {
            try {
                UUID.fromString(outputWriteId);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("输出写入 ID 必须为 UUID", exception);
            }
        }
        if (batchId < -1 || inputRows < 0 || inputRowsPerSecond < 0 || processedRowsPerSecond < 0
                || batchDurationMillis < 0 || progressAt == null) {
            throw new IllegalArgumentException("流式查询进度无效");
        }
    }

    public StreamingQueryProgress(
            String outputNodeId, long batchId, long inputRows, double inputRowsPerSecond,
            double processedRowsPerSecond, long batchDurationMillis, Instant progressAt
    ) {
        this(outputNodeId, null, batchId, inputRows, inputRowsPerSecond,
                processedRowsPerSecond, batchDurationMillis, progressAt);
    }
}
