package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("轨迹或驻留摘要统计类型。COUNT 为成员数且不使用字段；COUNT_FIELD 计非 NULL；ANY 返回任意非 NULL 样本且选择不稳定，允许类型由节点限制；SUM/MEAN/MIN/MAX/RANGE 忽略 NULL；STDDEV/VARIANCE 为样本统计；FIRST/LAST 按所属算法的完整排序键取首末观测值，即使该字段值为 NULL 也保留 NULL。")
public enum TrackSummaryStatisticKind {
    COUNT,
    COUNT_FIELD,
    ANY,
    SUM,
    MEAN,
    MIN,
    MAX,
    RANGE,
    STDDEV,
    VARIANCE,
    FIRST,
    LAST
}
