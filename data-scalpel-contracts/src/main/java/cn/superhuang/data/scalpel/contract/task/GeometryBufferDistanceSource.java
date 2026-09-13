package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Geometry Buffer 的逐行距离来源。CONSTANT 使用固定数值；FIELD 读取数值字段；EXPRESSION 使用受控的确定性逐行数值表达式。")
public enum GeometryBufferDistanceSource {
    CONSTANT,
    FIELD,
    EXPRESSION
}
