package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("仅支持 BATCH 和 BOUNDED 来源的 Canvas 文件输出节点；把一个或多个逻辑表按严格格式写入 S3 受控相对目录。每项写入独立提交且没有跨目录事务，完成目录以 _SUCCESS 标识。")
public record FileOutputNodeDefinition(
        @JsonPropertyDescription("Canvas 节点稳定 ID，在同一任务定义内唯一；边、运行诊断和血缘通过该值引用节点。")
        String id,
        @JsonPropertyDescription("节点显示名称，用于画布和运行诊断；不作为节点引用标识。")
        String name,
        @JsonPropertyDescription("仅用于编辑器展示的节点坐标和尺寸，不参与执行语义。")
        CanvasNodeLayout layout,
        @JsonPropertyDescription("当前节点的业务配置；其中的输入、输出和操作参数共同决定执行语义。")
        FileOutputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.FILE_OUTPUT;
    }
}
