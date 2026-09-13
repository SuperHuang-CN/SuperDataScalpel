package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Canvas Trace Proximity Events 节点定义；从一个或多个起始实体沿时空邻近事件逐层传播。")
public record TraceProximityEventsNodeDefinition(
        @JsonPropertyDescription("Canvas 节点稳定 ID，在同一任务定义内唯一。")
        String id,
        @JsonPropertyDescription("节点显示名称，用于画布和运行诊断。")
        String name,
        @JsonPropertyDescription("仅用于编辑器展示的节点坐标和尺寸。")
        CanvasNodeLayout layout,
        @JsonPropertyDescription("邻近传播追踪配置。")
        TraceProximityEventsConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TRACE_PROXIMITY_EVENTS;
    }
}
