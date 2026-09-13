package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;

/** Sanitized progress of the unique unbounded input; raw Spark progress never crosses this boundary. */
public record StreamingSourceProgress(
        @JsonPropertyDescription("当前流式部署唯一无界输入节点的 Canvas 节点 ID。")
        String sourceNodeId,
        @JsonPropertyDescription("不含凭据的实时来源定义指纹，用于关联同一来源进度。")
        String sourceSignature,
        @JsonPropertyDescription("当前已提交的来源 Offset 安全摘要。")
        String committedOffset,
        @JsonPropertyDescription("JDBC 增量来源本轮读取时间窗口的排除起点，ISO-8601 UTC 时间戳；首次无下界读取及 TDengine TMQ 来源为空。")
        Instant windowStart,
        @JsonPropertyDescription("JDBC 增量来源本轮读取时间窗口的包含终点，ISO-8601 UTC 时间戳；TDengine TMQ 来源为空。")
        Instant windowEnd,
        @JsonPropertyDescription("本轮来源读取窗口处理的输入行数。")
        long rowCount,
        @JsonPropertyDescription("最近一次来源轮询耗时，单位毫秒。")
        long pollDurationMillis,
        @JsonPropertyDescription("最近一次来源轮询完成时间，ISO-8601 UTC 时间戳。")
        Instant pollTime,
        @JsonPropertyDescription("来源最新可用位置与已提交位置之间的估算时间延迟，单位毫秒。")
        Long cursorLagMillis,
        @JsonPropertyDescription("实时来源类型：JDBC_INCREMENTAL 表示按时间游标增量读取 JDBC，TDENGINE_TMQ 表示通过 TMQ 消费 VGroup。")
        StreamingSourceKind sourceKind,
        @JsonPropertyDescription("TDengine TMQ 来源涉及的 VGroup 数量；其他来源为空。")
        Integer vGroupCount,
        @JsonPropertyDescription("本微批次消费 Offset 跨度的安全统计。")
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
