package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.WorkflowDefinition;
import java.util.List;
import java.util.UUID;

@Schema(description = "一次 WORKFLOW 父运行使用的固定定义快照及各节点子运行投影。父运行状态由整张有向无环图汇总；未满足依赖或父运行提前终止的节点可能从未创建 TaskRun。")

public record WorkflowRunResponse(
        @Schema(description = "工作流父级 TaskRun。该查询只接受已存在且 taskType=WORKFLOW 的运行，因此不为空；父运行状态汇总整张工作流，不代表任一单独节点。")
        TaskRunResponse run,
        @Schema(description = "父运行创建时固定保存的工作流定义快照；任务后续修改不会改变本次节点和依赖图。")
        WorkflowDefinition definition,
        @Schema(description = "本次运行固化的工作流定义节点总数，等于 nodes 数量。")
        int totalNodes,
        @Schema(description = "已创建子运行且子运行状态为 SUCCESS 的节点数；等待、阻塞、跳过和其他终态均不计入。")
        long succeededNodes,
        @Schema(description = "是否至少有一个子运行处于 QUEUED、RUNNING、CANCEL_REQUESTED 或 STOP_REQUESTED；未创建子运行的 WAITING 节点不计入。")
        boolean hasActiveChildren,
        @Schema(description = "按工作流定义顺序返回的节点运行状态。")
        List<Node> nodes
) {
    @Schema(description = "工作流定义中的一个节点及本次父运行为它创建的子任务运行。")
    public record Node(
            @Schema(description = "工作流定义中的稳定节点 ID；不是数据库 UUID。")
            String id,
            @Schema(description = "该节点引用的任务 UUID 字符串。")
            String taskId,
            @Schema(description = "该节点引用任务当前的显示名称；引用任务已删除时为空，运行时身份仍可从 childRun 查看。")
            String taskName,
            @Schema(description = "节点投影状态。已创建子运行时等于完整 TaskRunStatus；未创建时为 WAITING 等待依赖、BLOCKED 因父运行失败/超时未执行、CANCELLED 随父运行取消、SKIPPED 父运行被跳过，或 FAILED 表示该节点在创建子运行前出错。")
            String status,
            @Schema(description = "本次父运行为该节点创建的子任务运行；尚未轮到执行或在创建前被阻断时为空。")
            TaskRunResponse childRun,
            @Schema(description = "节点尚未运行、被跳过或执行失败等状态的可读说明；没有补充信息时为空。")
            String message
    ) { }
}
