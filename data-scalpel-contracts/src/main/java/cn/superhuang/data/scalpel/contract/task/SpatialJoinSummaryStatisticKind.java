package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("一对一空间连接对匹配连接记录执行的数值统计。所有统计忽略 NULL；STDDEV 使用 Spark 样本标准差。")
public enum SpatialJoinSummaryStatisticKind {
    SUM,
    MIN,
    MAX,
    MEAN,
    STDDEV
}
