package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("Canvas 中从一个节点输出到另一个节点输入的有向连线。")
public record CanvasEdgeDefinition(
        @JsonPropertyDescription("Canvas 连线稳定 ID，在同一任务定义内唯一；不是数据库 UUID。")
        String id,
        @JsonPropertyDescription("连线起点节点的 id；必须引用当前任务定义中存在且允许输出的节点。")
        String sourceNodeId,
        @JsonPropertyDescription("连线终点节点的 id；必须引用当前任务定义中存在且允许输入的节点。")
        String targetNodeId
) {
}
