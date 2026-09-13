package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间 Dissolve 标量统计类型。COUNT_FIELD 统计来源字段非 NULL 数；ANY 取任一非 NULL 字符串且不保证重跑稳定；其余类型对数值字段使用 Spark 样本统计语义。")
public enum SpatialAggregateStatisticKind {
    COUNT_FIELD,
    SUM,
    MEAN,
    MIN,
    MAX,
    RANGE,
    STDDEV,
    VARIANCE,
    ANY
}
