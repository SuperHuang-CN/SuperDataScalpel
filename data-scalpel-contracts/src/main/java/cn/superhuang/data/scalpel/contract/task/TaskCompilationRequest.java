package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialSpec;

/** Stable Admin-to-Task-Engine compilation request contract. */
@JsonClassDescription("Admin 提交给 Task Engine 的稳定编译契约；携带完整任务定义、编译所需元数据快照和可选节点试运行范围。")
public record TaskCompilationRequest(
        @JsonPropertyDescription("调用方生成的编译请求 UUID，用于关联诊断和取消；同一 Task Engine 实例中不能与仍在活动的编译重复，已结束后可再次使用但不提供结果缓存或幂等保证。")
        @NotNull UUID requestId,
        @JsonPropertyDescription("要校验和编译的完整任务定义；当前 task.type 只接受 CANVAS，executionMode 决定批或流式编译规则。")
        @NotNull @Valid TaskDefinition task,
        @JsonPropertyDescription("编译所需的数据源、模型和文件表元数据快照；Task Engine 按该快照解析 Schema，不从 Admin 数据库补查当前状态。")
        @NotNull MetadataSnapshot metadataSnapshot,
        @JsonPropertyDescription("可选 Canvas 节点试运行编译范围；仅 BATCH 模式支持，存在时只编译目标节点及其上游闭包，本接口本身仍不读取真实业务数据。")
        CanvasTrialSpec canvasTrial
) {
    public TaskCompilationRequest(UUID requestId, TaskDefinition task, MetadataSnapshot metadataSnapshot) {
        this(requestId, task, metadataSnapshot, null);
    }
}
