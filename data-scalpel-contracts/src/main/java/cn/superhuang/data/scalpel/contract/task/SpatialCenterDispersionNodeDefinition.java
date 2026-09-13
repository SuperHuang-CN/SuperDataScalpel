package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("Canvas 空间中心与离散分析节点定义，仅支持 BATCH、BOUNDED 和投影 EPSG XY Geometry。节点按组在 Executor 内收集有效观测：普通独立分析每组最多 100000 个要素和 100 万顶点；包含 CENTRAL_FEATURE 时每组最多 5000 个要素和 100 万顶点，超限安全失败。")
public record SpatialCenterDispersionNodeDefinition(
        @JsonPropertyDescription("Canvas 节点稳定 ID，在同一任务定义内唯一；边、运行诊断和血缘通过该值引用节点。")
        String id,
        @JsonPropertyDescription("节点显示名称，用于画布和运行诊断；不作为节点引用标识。")
        String name,
        @JsonPropertyDescription("仅用于编辑器展示的节点坐标和尺寸，不参与执行语义。")
        CanvasNodeLayout layout,
        @JsonPropertyDescription("当前节点的业务配置；其中的输入、输出和操作参数共同决定执行语义。")
        SpatialCenterDispersionConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_CENTER_DISPERSION;
    }
}
