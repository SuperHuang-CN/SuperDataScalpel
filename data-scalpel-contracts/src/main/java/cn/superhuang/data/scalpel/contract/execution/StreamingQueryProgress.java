package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.UUID;

/** Sanitized latest progress for one output query; Spark raw progress JSON never crosses module boundaries. */
public record StreamingQueryProgress(
        @JsonPropertyDescription("下游输出节点标识。")
        String outputNodeId,
        @JsonPropertyDescription("该流查询对应的稳定输出写入 ID。")
        String outputWriteId,
        @JsonPropertyDescription("Structured Streaming 微批次 ID。")
        long batchId,
        @JsonPropertyDescription("本微批次读取的输入行数。")
        long inputRows,
        @JsonPropertyDescription("Spark 报告的输入速率，单位行/秒。")
        double inputRowsPerSecond,
        @JsonPropertyDescription("Spark 报告的处理速率，单位行/秒。")
        double processedRowsPerSecond,
        @JsonPropertyDescription("本微批次处理耗时，单位毫秒。")
        long batchDurationMillis,
        @JsonPropertyDescription("Spark 生成该查询进度快照的时间。")
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
