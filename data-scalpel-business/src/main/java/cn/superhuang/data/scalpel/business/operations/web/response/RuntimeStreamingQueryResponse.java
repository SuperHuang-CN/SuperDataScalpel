package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.task.domain.*;
import java.time.Instant;
import java.util.UUID;
@Schema(description = "一个 Spark Structured Streaming Query 的最近运行进度。")
public record RuntimeStreamingQueryResponse(
        @Schema(description = "平台保存的流式查询 UUID。")
        UUID id,
        @Schema(description = "对应流式输出节点的名称。")
        String name,
        @Schema(description = "平台保存的查询状态：STARTING 启动中、RUNNING 运行中、STOPPING 停止中、STOPPED 已停止、FAILED 异常终止。")
        StreamingQueryState state,
        @Schema(description = "Spark Structured Streaming 最近完成或正在处理的微批次序号；尚无进度时为空。")
        Long batchId,
        @Schema(description = "Spark 最近一次上报进度所对应微批次的输入行数；尚无进度时为空，0 只表示该批次没有输入。")
        Long inputRows,
        @Schema(description = "最近进度窗口的输入速率，单位行/秒。")
        Double inputRowsPerSecond,
        @Schema(description = "最近进度窗口的处理速率，单位行/秒。")
        Double processedRowsPerSecond,
        @Schema(description = "最近一个微批次的总处理耗时，单位毫秒；尚无进度时为空。")
        Long batchDurationMillis,
        @Schema(description = "Spark 最近一次进度事件的时间，ISO-8601 UTC 时间戳；尚无进度时为空。")
        Instant lastProgressAt
) {
    public static RuntimeStreamingQueryResponse from(TaskStreamingQuery q) {
        return new RuntimeStreamingQueryResponse(q.getId(), q.getOutputNodeName(), q.getState(), q.getLatestBatchId(),
                q.getLatestInputRows(), q.getInputRowsPerSecond(), q.getProcessedRowsPerSecond(), q.getBatchDurationMillis(), q.getLastProgressAt());
    }
}
