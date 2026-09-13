package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("字段类型转换失败策略：FAIL 使用 ANSI 普通 cast 或严格日期时间函数，遇到不可转换真实值时整个节点失败；SET_NULL 使用容错转换，失败位置输出 SQL NULL。两者都保留记录，不存在跳过行语义。")
public enum CastFailureStrategy {
    FAIL,
    SET_NULL
}
