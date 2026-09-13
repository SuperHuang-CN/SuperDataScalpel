package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Canvas Snap Tracks 节点定义；将时间轨迹点按路网连接和方向吸附到网络线。")
public record SnapTracksNodeDefinition(
        @JsonPropertyDescription("Canvas 节点稳定 ID，在同一任务定义内唯一。")
        String id,
        @JsonPropertyDescription("节点显示名称，用于画布和运行诊断。")
        String name,
        @JsonPropertyDescription("仅用于编辑器展示的节点坐标和尺寸。")
        CanvasNodeLayout layout,
        @JsonPropertyDescription("轨迹路网吸附配置。")
        SnapTracksConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SNAP_TRACKS;
    }
}
