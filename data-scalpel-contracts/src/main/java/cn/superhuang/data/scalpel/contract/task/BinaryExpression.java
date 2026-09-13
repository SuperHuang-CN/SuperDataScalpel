package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("对左右两个子表达式应用 ADD、SUBTRACT、MULTIPLY、DIVIDE 或 MODULO 算术运算；不提供比较或逻辑运算，操作数和结果类型由实际 Spark 表达式与 Analyzer 判断。")
public record BinaryExpression(
        @JsonPropertyDescription("必填的受控算术运算符：ADD、SUBTRACT、MULTIPLY、DIVIDE 或 MODULO。")
        DeriveBinaryOperator operator,
        @JsonPropertyDescription("必填的左操作数表达式。")
        CanvasExpression left,
        @JsonPropertyDescription("必填的右操作数表达式。除零、溢出和类型不兼容等真实行为遵循 Spark ANSI 语义。")
        CanvasExpression right
) implements CanvasExpression {
}
