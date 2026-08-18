package cn.superhuang.data.scalpel.dialect.model;

/** Describes how closely one physical table statistic reflects the value at collection time. */
public enum TableStatisticQuality {
    EXACT,
    ESTIMATED,
    UNAVAILABLE
}
