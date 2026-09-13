package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("结构化字段谓词运算符。EQUALS/NOT_EQUALS 和四种大小比较各需一个值；IN/NOT_IN 需 1 至 100 个值；IS_NULL/IS_NOT_NULL 不带值；CONTAINS/STARTS_WITH/ENDS_WITH 各需一个 STRING 值。Geometry 只支持空值判断，SQL NULL 按 Spark 三值逻辑处理。")
public enum FilterOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_THAN_OR_EQUALS,
    LESS_THAN,
    LESS_THAN_OR_EQUALS,
    IN,
    NOT_IN,
    IS_NULL,
    IS_NOT_NULL,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH
}
