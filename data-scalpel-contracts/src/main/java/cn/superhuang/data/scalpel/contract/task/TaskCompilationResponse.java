package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.UUID;

/** Stable Task Engine compilation response exposed through Admin. */
@JsonClassDescription("Task Engine 对一次任务定义编译的稳定结果；汇总阻断问题、逐节点结果和本次定义产生的血缘证据。")
public record TaskCompilationResponse(
        @JsonPropertyDescription("请求 UUID。")
        UUID requestId,
        @JsonPropertyDescription("编译结果任务类型；当前成功处理的请求固定为 CANVAS。")
        TaskType taskType,
        @JsonPropertyDescription("是否通过全部阻断性问题并完成节点编译；HTTP 200 不等于 valid=true，应同时读取 canvasIssues 与 nodeResults。")
        boolean valid,
        @JsonPropertyDescription("耗时，单位毫秒。")
        long durationMs,
        @JsonPropertyDescription("承载编译会话的 Task Engine Spark Application ID；这是编译服务进程标识，不是一次正式任务运行 ID，不可用时为空。")
        String sparkApplicationId,
        @JsonPropertyDescription("Canvas 整体协议、拓扑、模式和定义级问题；没有时为空列表。节点自身问题见 nodeResults。")
        List<CompilationIssue> canvasIssues,
        @JsonPropertyDescription("按编译拓扑返回的节点编译状态、输出 Schema、指标和安全问题；这是 Schema 编译结果，不表示节点读取或写入了真实数据。")
        List<NodeCompilationResult> nodeResults,
        @JsonPropertyDescription("编译期提取的资产级和字段级血缘证据；无法分析时包含状态和告警。")
        CanvasLineageCompilation lineage
) {
    public TaskCompilationResponse(
            UUID requestId,
            TaskType taskType,
            boolean valid,
            long durationMs,
            String sparkApplicationId,
            List<CompilationIssue> canvasIssues,
            List<NodeCompilationResult> nodeResults
    ) {
        this(requestId, taskType, valid, durationMs, sparkApplicationId, canvasIssues, nodeResults, null);
    }
}
