package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@JsonClassDescription("可编译和执行的任务定义封装；任务类型、Canvas 内容与批处理或流处理模式必须保持一致。")
public record TaskDefinition(
        @JsonPropertyDescription("任务类型；当前 Canvas 定义使用 CANVAS，类型必须与 definition 的契约一致。")
        @NotNull TaskType type,
        @JsonPropertyDescription("任务 Canvas 的节点、边、资源绑定和协议版本完整定义。")
        @NotNull @Valid CanvasDefinition definition,
        @JsonPropertyDescription("任务执行模式：BATCH 按批次结束，STREAMING 持续消费并运行。")
        CanvasExecutionMode executionMode
) {
    public TaskDefinition {
        executionMode = executionMode == null ? CanvasExecutionMode.BATCH : executionMode;
    }

    public TaskDefinition(TaskType type, CanvasDefinition definition) {
        this(type, definition, CanvasExecutionMode.BATCH);
    }
}
