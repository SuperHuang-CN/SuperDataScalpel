package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("质量规则类型：NOT_NULL 非空；UNIQUE 非空组合唯一；VALUE_RANGE 取值范围；STRING_LENGTH 字符串长度；DICTIONARY_MEMBERSHIP 标准字典成员；ROW_COUNT 全表最小行数；FRESHNESS 最大时间值新鲜度；GEOMETRY_VALID 空间几何有效；GEOMETRY_NON_EMPTY 非空几何；FORMAT_PATTERN 字符串整串格式；CONDITIONAL_NOT_NULL 条件成立时非空；FIELD_COMPARISON 两字段逐行比较；REFERENCE_EXISTS 组合外键式引用存在。")
public enum ModelQualityRuleType {
    NOT_NULL,
    UNIQUE,
    VALUE_RANGE,
    STRING_LENGTH,
    DICTIONARY_MEMBERSHIP,
    ROW_COUNT,
    FRESHNESS,
    GEOMETRY_VALID,
    GEOMETRY_NON_EMPTY,
    FORMAT_PATTERN,
    CONDITIONAL_NOT_NULL,
    FIELD_COMPARISON,
    REFERENCE_EXISTS
}
