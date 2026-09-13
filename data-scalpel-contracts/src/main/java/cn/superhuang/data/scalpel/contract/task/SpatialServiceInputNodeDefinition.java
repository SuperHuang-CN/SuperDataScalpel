package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("仅支持 BATCH 的 Canvas 空间服务输入节点；分页读取一个或多个已纳管 ArcGIS REST/WFS 要素资源并分别形成 BOUNDED 逻辑表，使用 Sedona 解析 Geometry。")
public record SpatialServiceInputNodeDefinition(
        @JsonPropertyDescription("Canvas 节点稳定 ID，在同一任务定义内唯一；边、运行诊断和血缘通过该值引用节点。")
        String id,
        @JsonPropertyDescription("节点显示名称，用于画布和运行诊断；不作为节点引用标识。")
        String name,
        @JsonPropertyDescription("仅用于编辑器展示的节点坐标和尺寸，不参与执行语义。")
        CanvasNodeLayout layout,
        @JsonPropertyDescription("当前节点的业务配置；其中的输入、输出和操作参数共同决定执行语义。")
        SpatialServiceInputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_SERVICE_INPUT;
    }
}
