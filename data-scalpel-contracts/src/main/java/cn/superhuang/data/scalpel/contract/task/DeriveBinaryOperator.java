package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("DERIVE_COLUMNS 二元算术运算：ADD 加、SUBTRACT 减、MULTIPLY 乘、DIVIDE 除、MODULO 取余。平台不维护第二套类型矩阵，操作数兼容性、返回类型、除零和溢出由 Spark Analyzer 与 ANSI 执行语义决定。")
public enum DeriveBinaryOperator {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    MODULO
}
