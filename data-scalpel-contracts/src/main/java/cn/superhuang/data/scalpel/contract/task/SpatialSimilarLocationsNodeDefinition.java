package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Canvas 查找相似位置节点定义；输出全部参考位置和按属性相似度选出的候选位置。")
public record SpatialSimilarLocationsNodeDefinition(
        @JsonPropertyDescription("Canvas 节点稳定 ID，在同一任务定义内唯一；边、运行诊断和血缘通过该值引用节点。")
        String id,
        @JsonPropertyDescription("节点显示名称，用于画布和运行诊断；不作为节点引用标识。")
        String name,
        @JsonPropertyDescription("仅用于编辑器展示的节点坐标和尺寸，不参与执行语义。")
        CanvasNodeLayout layout,
        @JsonPropertyDescription("当前节点的查找相似位置业务配置。")
        SpatialSimilarLocationsConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_SIMILAR_LOCATIONS;
    }
}
