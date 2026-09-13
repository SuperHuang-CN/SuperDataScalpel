package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("字段的非 NULL 原值未命中任何映射项时的动作：KEEP 保留原值；SET_NULL 写入 SQL NULL；SET_LITERAL 写入必填 unmatchedValue；ERROR 在真实执行遇到未匹配值时以不可重试约束错误失败。来源 SQL NULL 始终保留，不应用这些动作。")
public enum ValueMappingUnmatchedStrategy {
    KEEP,
    SET_NULL,
    SET_LITERAL,
    ERROR
}
