package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("条件非空规则的条件操作符：EQ/NE 与一个非空常量相等或不等；IN/NOT_IN 属于或不属于非空常量列表；IS_NULL/IS_NOT_NULL 判断 NULL；IS_EMPTY/IS_NOT_EMPTY 仅对 STRING 判断是否恰好为空字符串。EQ、NE、IN、NOT_IN 均不匹配 NULL，空字符串不会自动去除空白。")
public enum QualityConditionOperator {
    EQ,
    NE,
    IN,
    NOT_IN,
    IS_NULL,
    IS_NOT_NULL,
    IS_EMPTY,
    IS_NOT_EMPTY
}
