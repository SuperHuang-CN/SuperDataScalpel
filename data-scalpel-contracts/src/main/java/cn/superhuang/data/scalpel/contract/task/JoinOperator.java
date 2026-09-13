package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Join 字段比较运算符；当前只有 EQUALS，使用 Spark SQL 普通等号语义。NULL 不与任何值匹配，两个 NULL 也不匹配；不支持范围、表达式或 OR。")
public enum JoinOperator {
    EQUALS
}
