package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("读取 DERIVE_COLUMNS 操作原始来源 Schema 中一个字段值；不能引用同一节点的全局或局部规则刚派生出的字段。")
public record ColumnExpression(
        @JsonPropertyDescription("必填的原始来源字段名，按当前逻辑表 Schema 精确解析；不支持嵌套路径或其他表字段。")
        String columnName
) implements CanvasExpression {
}
