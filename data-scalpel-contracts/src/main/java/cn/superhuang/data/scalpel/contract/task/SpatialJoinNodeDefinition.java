package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("Canvas 空间 Join 节点定义，仅支持 BATCH。左表作为目标要素，右表作为连接要素；支持 INNER，以及 Canvas 4.56 起保留全部目标要素的 LEFT。Canvas 4.57 可显式声明一对多；4.58 起支持汇总匹配项或确定性保留一项的一对一。空间条件必填，可叠加属性等值条件，并可显式投影、排除、改名和排序输出字段；不支持 RIGHT、FULL、流式、距离或时间连接。")
public record SpatialJoinNodeDefinition(
        @JsonPropertyDescription("Canvas 节点稳定 ID，在同一任务定义内唯一；边、运行诊断和血缘通过该值引用节点。")
        String id,
        @JsonPropertyDescription("节点显示名称，用于画布和运行诊断；不作为节点引用标识。")
        String name,
        @JsonPropertyDescription("仅用于编辑器展示的节点坐标和尺寸，不参与执行语义。")
        CanvasNodeLayout layout,
        @JsonPropertyDescription("当前节点的业务配置；其中的输入、输出和操作参数共同决定执行语义。")
        SpatialJoinConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_JOIN;
    }
}
