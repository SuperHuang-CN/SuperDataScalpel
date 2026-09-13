package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("格网统计类型：COUNT 统计参与点数且不使用来源字段；COUNT_FIELD 统计指定字段非 NULL 数且不去重；ANY 取任一非 NULL 字符串且不保证重跑稳定；SUM/MEAN/MIN/MAX 为数值聚合；RANGE 为 max-min；STDDEV 和 VARIANCE 为样本统计 n-1，样本不足时为 NULL。")
public enum SpatialBinStatisticKind {
    COUNT,
    COUNT_FIELD,
    ANY,
    SUM,
    MEAN,
    MIN,
    MAX,
    RANGE,
    STDDEV,
    VARIANCE
}
