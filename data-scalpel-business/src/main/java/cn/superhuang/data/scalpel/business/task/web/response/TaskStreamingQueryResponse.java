package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.StreamingQueryState;
import cn.superhuang.data.scalpel.business.task.domain.StreamingSinkType;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingQuery;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "流式部署中的一个受管 Structured Streaming 查询状态。Canvas 按输出写入建立查询；流式 JAR 按用户作业注册的查询描述建立查询。")

public record TaskStreamingQueryResponse(
        @Schema(description = "流式输出查询 UUID，用于在部署内定位该独立查询状态。")
        UUID id,
        @Schema(description = "Canvas 时为输出节点 UUID；SPARK_STREAMING_JAR 时为用户作业注册的稳定 queryId。")
        UUID outputNodeId,
        @Schema(description = "Canvas 输出写入定义 UUID，用于区分同一节点的多个写入；SPARK_STREAMING_JAR 查询为空。")
        UUID outputWriteId,
        @Schema(description = "Canvas 输出节点名称，或 SPARK_STREAMING_JAR 注册的逻辑查询名称。")
        String outputNodeName,
        @Schema(description = "流式输出目标类型：JDBC 写入数据库，KAFKA 写入主题，CUSTOM 由用户 Spark JAR 自行处理。")
        StreamingSinkType sinkType,
        @Schema(description = "该查询独立使用的 Checkpoint 键；客户端应视为不透明诊断标识，不能自行拼接存储地址。")
        String checkpointKey,
        @Schema(description = "独立流式查询状态：STARTING 启动中、RUNNING 运行中、STOPPING 停止中、STOPPED 已停止或 FAILED 失败。")
        StreamingQueryState state,
        @Schema(description = "Spark 最近报告的微批次序号，通常从 0 开始；兼容执行器可能报告 -1，尚无进度事件时为空。")
        Long latestBatchId,
        @Schema(description = "最近一次进度事件对应微批次的输入行数；尚无进度时为空，0 只表示该批没有输入。")
        Long latestInputRows,
        @Schema(description = "最近进度事件的平均输入速率，单位行/秒；尚无进度时为空。")
        Double inputRowsPerSecond,
        @Schema(description = "最近进度事件的平均处理速率，单位行/秒；尚无进度时为空。")
        Double processedRowsPerSecond,
        @Schema(description = "最近一次进度事件中该微批次的总处理耗时，单位毫秒；尚无进度时为空。")
        Long batchDurationMillis,
        @Schema(description = "最近收到流式进度的时间，ISO-8601 UTC 时间戳；尚无进度时为空。")
        Instant lastProgressAt,
        @Schema(description = "最近一次查询错误时间，ISO-8601 UTC 时间戳；无错误时为空。")
        Instant lastErrorAt,
        @Schema(description = "最近一次安全错误摘要；无错误时为空。")
        String lastError
) {
    public static TaskStreamingQueryResponse from(TaskStreamingQuery query) {
        return new TaskStreamingQueryResponse(
                query.getId(), query.getOutputNodeId(), query.getOutputWriteId(), query.getOutputNodeName(),
                query.getSinkType(), query.getCheckpointKey(), query.getState(),
                query.getLatestBatchId(), query.getLatestInputRows(),
                query.getInputRowsPerSecond(), query.getProcessedRowsPerSecond(),
                query.getBatchDurationMillis(), query.getLastProgressAt(),
                query.getLastErrorAt(), query.getLastError()
        );
    }
}
