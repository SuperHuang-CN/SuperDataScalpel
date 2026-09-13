package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("一个 Canvas 节点的编译状态、解析后的输入输出 Schema 和节点级问题。")
public record NodeCompilationResult(
        @JsonPropertyDescription("被编译 Canvas 节点的稳定 ID，与任务定义中的 node.id 一致。")
        String nodeId,
        @JsonPropertyDescription("该节点的编译状态，区分成功、失败或因上游失败而跳过。")
        NodeCompilationState state,
        @JsonPropertyDescription("当前节点声明的输入逻辑表列表。")
        List<CanvasTableSchema> inputTables,
        @JsonPropertyDescription("当前节点生成的输出逻辑表列表。")
        List<CanvasTableSchema> outputTables,
        @JsonPropertyDescription("编译或校验问题列表。")
        List<CompilationIssue> issues
) {
}
