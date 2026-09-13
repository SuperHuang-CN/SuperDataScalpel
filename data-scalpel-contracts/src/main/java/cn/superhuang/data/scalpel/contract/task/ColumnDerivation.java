package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("使用一个受控 Canvas 表达式创建或覆盖字段；表达式只读取进入 DERIVE_COLUMNS 节点时的原始来源 Schema。已有目标字段在原位置替换，新目标字段按有效规则顺序追加。")
public record ColumnDerivation(
        @JsonPropertyDescription("必填的目标字段名，在同一来源表的全局与局部有效规则中唯一。流任务可以新增字段，但不能覆盖来源事件时间字段。")
        String targetColumnName,
        @JsonPropertyDescription("必填的受控表达式树；字段引用必须来自当前节点原始来源 Schema。单节点所有表达式合计最多 512 个节点，单棵嵌套深度最多 16。")
        CanvasExpression expression
) {
}
