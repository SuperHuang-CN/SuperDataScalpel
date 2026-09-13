package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("仅支持 BATCH 的 Canvas 普通 Join 节点；按等值条件连接两个上游逻辑表，保留已有表并追加投影后的结果表。流任务必须使用受限的 STREAM_JOIN，空间关系必须使用空间连接节点。")
public record JoinNodeDefinition(
        @JsonPropertyDescription("Canvas 节点稳定 ID，在同一任务定义内唯一；边、运行诊断和血缘通过该值引用节点。")
        String id,
        @JsonPropertyDescription("节点显示名称，用于画布和运行诊断；不作为节点引用标识。")
        String name,
        @JsonPropertyDescription("仅用于编辑器展示的节点坐标和尺寸，不参与执行语义。")
        CanvasNodeLayout layout,
        @JsonPropertyDescription("当前节点的业务配置；其中的输入、输出和操作参数共同决定执行语义。")
        JoinConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JOIN;
    }
}
