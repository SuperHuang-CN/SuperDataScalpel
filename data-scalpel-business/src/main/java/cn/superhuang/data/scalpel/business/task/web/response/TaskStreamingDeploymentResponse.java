package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentActualState;
import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentDesiredState;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingDeployment;
import cn.superhuang.data.scalpel.business.task.domain.StreamingDeploymentExecutionMode;
import cn.superhuang.data.scalpel.contract.execution.StreamingSourceKind;
import cn.superhuang.data.scalpel.contract.execution.StreamingCheckpointMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "流式任务当前部署、Checkpoint、来源游标和各输出查询的运行状态。")

public record TaskStreamingDeploymentResponse(
        @Schema(description = "流式部署 UUID，用于关联运行、执行事件、Checkpoint 来源和诊断；当前任务级启动、停止接口仍使用 taskId。")
        UUID id,
        @Schema(description = "本次响应固定引用的任务定义版本；定义更新后旧运行仍保留原版本。")
        int definitionVersion,
        @Schema(description = "创建部署时固定使用的计算引擎 UUID，始终非空；后续任务改绑引擎不会改写此历史部署。")
        UUID computeEngineId,
        @Schema(description = "最近一次承载该部署的任务运行 UUID；部署从未开始尝试时为空，停止或失败后仍保留该运行引用。")
        UUID currentRunId,
        @Schema(description = "平台生成并固化的 Checkpoint 命名空间前缀；客户端应视为不透明标识，不能自行拼接对象存储路径。")
        String checkpointKeyPrefix,
        @Schema(description = "Checkpoint 代次，从 1 开始。Spark Streaming JAR 对同一定义执行 FRESH 时递增并隔离旧状态；Canvas 每个定义版本使用自己的部署，当前没有独立重置接口。")
        int checkpointGeneration,
        @Schema(description = "本次部署的 Checkpoint 模式：FRESH 使用隔离的新状态，CONTINUE 复用 checkpointSourceDeploymentId 所指部署的兼容状态。")
        StreamingCheckpointMode checkpointStartMode,
        @Schema(description = "CONTINUE 跨定义版本新建 JAR 部署时记录的来源部署 UUID；同定义版本直接复用原部署或 FRESH/Canvas 时为空。")
        UUID checkpointSourceDeploymentId,
        @Schema(description = "REAL 正式部署或 TRIAL 受限试运行；任务状态接口只返回 REAL，其他运行详情可能嵌入 TRIAL 部署。")
        StreamingDeploymentExecutionMode executionMode,
        @Schema(description = "控制面期望部署最终达到的状态：RUNNING 或 STOPPED；它表达命令目标，不证明 Dispatcher 已经完成操作。")
        StreamingDeploymentDesiredState desiredState,
        @Schema(description = "平台根据执行事件保存的实际状态：STARTING、RUNNING、STOPPING、STOPPED 或 FAILED；应结合 desiredState 判断是否仍在收敛。失败处理可能把 desiredState 同时改为 STOPPED。")
        StreamingDeploymentActualState actualState,
        @Schema(description = "流式后端应用标识；尚未启动时为空。")
        String applicationId,
        @Schema(description = "执行引擎提供的跟踪页面地址；不可用时为空。")
        String trackingUrl,
        @Schema(description = "执行尝试序号；尚未开始尝试时为空。")
        Integer attempt,
        @Schema(description = "部署首次确认开始运行的时间，ISO-8601 UTC 时间戳；尚未启动成功时为空。")
        Instant startedAt,
        @Schema(description = "控制面接受停止请求的时间，ISO-8601 UTC 时间戳；未请求停止时为空，不表示后端已经停止。")
        Instant stopRequestedAt,
        @Schema(description = "部署确认进入 STOPPED 状态的时间，ISO-8601 UTC 时间戳；STARTING、RUNNING、STOPPING 或 FAILED 时为空，失败时间见 lastErrorAt。")
        Instant stoppedAt,
        @Schema(description = "最近一次确认部署运行或接受流式进度事件的时间，ISO-8601 UTC 时间戳；尚未获得运行证据时为空，长时间无新进度不自动等同于任务失败。")
        Instant lastProgressAt,
        @Schema(description = "最近一次记录部署级安全错误的时间，ISO-8601 UTC 时间戳；无错误时为空。")
        Instant lastErrorAt,
        @Schema(description = "最近一次安全错误摘要；无错误时为空。")
        String lastError,
        @Schema(description = "实时 Canvas 当前部署唯一无界输入的节点 UUID；SPARK_STREAMING_JAR 或 Canvas 无可识别来源身份时为空。")
        UUID sourceNodeId,
        @Schema(description = "不含凭据的实时来源定义 SHA-256 摘要，用于判断旧 Checkpoint 是否与当前来源兼容；尚无来源进度时为空。")
        String sourceSignature,
        @Schema(description = "来源最近成功提交的 Offset 安全摘要；格式随 sourceKind 变化，客户端应视为不透明诊断值，尚无提交时为空。")
        String committedOffset,
        @Schema(description = "JDBC_INCREMENTAL 最近读取窗口的排除起点，ISO-8601 UTC 时间戳；首次无下界读取、TDENGINE_TMQ 或尚无进度时为空。")
        Instant windowStart,
        @Schema(description = "JDBC_INCREMENTAL 最近读取窗口的包含终点，ISO-8601 UTC 时间戳；TDENGINE_TMQ 或尚无进度时为空。")
        Instant windowEnd,
        @Schema(description = "最近一次来源进度事件报告的输入行数；尚无进度时为空，0 表示本轮没有输入。")
        Long rowCount,
        @Schema(description = "最近一次来源轮询耗时，单位毫秒。")
        Long pollDurationMillis,
        @Schema(description = "最近一次来源轮询完成时间，ISO-8601 UTC 时间戳；尚无来源进度时为空。")
        Instant pollTime,
        @Schema(description = "来源最新可用位置与已提交位置之间的估算时间延迟，单位毫秒；无法估算时为空，不能直接当作统一业务新鲜度。")
        Long cursorLagMillis,
        @Schema(description = "流式来源类型：JDBC_INCREMENTAL 时间游标增量读取，TDENGINE_TMQ 通过 TMQ 消费 VGroup；尚无来源进度时为空。")
        StreamingSourceKind sourceKind,
        @Schema(description = "TDengine TMQ 最近观测到的 VGroup 数量；其他来源为空。")
        Integer vGroupCount,
        @Schema(description = "TDENGINE_TMQ 最近微批消费 Offset 跨度的安全统计；其他来源或尚无进度时为空。")
        Long batchOffsetSpan,
        @Schema(description = "用户作业通过 SDK 上报的最新状态和指标；未上报时为空。")
        UserJobObservabilityResponse userJobObservability,
        @Schema(description = "各输出节点对应的流式查询状态。")
        List<TaskStreamingQueryResponse> queries
) {
    public static TaskStreamingDeploymentResponse from(
            TaskStreamingDeployment deployment,
            cn.superhuang.data.scalpel.business.task.domain.TaskRun run,
            List<TaskStreamingQueryResponse> queries
    ) {
        return new TaskStreamingDeploymentResponse(
                deployment.getId(), deployment.getDefinitionVersion(), deployment.getComputeEngineId(),
                deployment.getCurrentRunId(), deployment.getCheckpointKeyPrefix(),
                deployment.getCheckpointGeneration(), deployment.getCheckpointStartMode(),
                deployment.getCheckpointSourceDeploymentId(),
                deployment.getExecutionMode(),
                deployment.getDesiredState(), deployment.getActualState(),
                run == null ? null : run.getBackendApplicationId(),
                run == null ? null : run.getTrackingUrl(),
                run == null ? null : run.getAttempt(),
                deployment.getStartedAt(), deployment.getStopRequestedAt(), deployment.getStoppedAt(),
                deployment.getLastProgressAt(), deployment.getLastErrorAt(), deployment.getLastError(),
                deployment.getSourceNodeId(), deployment.getSourceSignature(),
                deployment.getLastCommittedOffset(), deployment.getLastWindowStart(),
                deployment.getLastWindowEnd(), deployment.getLastWindowRowCount(),
                deployment.getLastPollDurationMillis(), deployment.getLastPollAt(),
                deployment.getCursorLagMillis(), deployment.getSourceKind(),
                deployment.getLastVGroupCount(), deployment.getLastBatchOffsetSpan(),
                TaskRunResponse.observabilityFrom(run),
                List.copyOf(queries)
        );
    }
}
