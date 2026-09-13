package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("普通 Join 或 Stream Join 的一个字段匹配条件；比较左表字段与右表字段，多个条件由所属配置使用 AND 组合。普通 Spark 等号不把两个 SQL NULL 视为相等。")
public record JoinCondition(
        @JsonPropertyDescription("左表中参与匹配的字段名；必须存在。普通 Join 禁止 Geometry 条件，其他类型能否比较由 Spark Analyzer 判断。")
        String leftColumnName,
        @JsonPropertyDescription("必填比较运算符；当前只支持 EQUALS，对应 Spark SQL 普通等号，不是 NULL-safe equality。")
        JoinOperator operator,
        @JsonPropertyDescription("右表中参与匹配的字段名；必须存在。普通 Join 禁止 Geometry 条件，其他类型能否比较由 Spark Analyzer 判断。")
        String rightColumnName
) {
}
