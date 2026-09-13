package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Group By Proximity 时间邻近阈值单位。毫秒到周按固定时长计算；月和年按 Spark 会话时区中的日历区间计算。")
public enum SpatialGroupByProximityTemporalUnit {
    MILLISECONDS,
    SECONDS,
    MINUTES,
    HOURS,
    DAYS,
    WEEKS,
    MONTHS,
    YEARS
}
