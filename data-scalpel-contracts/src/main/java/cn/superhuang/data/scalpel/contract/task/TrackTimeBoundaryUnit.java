package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

/** Sub-day units are elapsed durations; days, weeks, months and years are calendar periods. */
@JsonClassDescription("固定时间边界单位。毫秒至小时是经过时长；DAYS、WEEKS、MONTHS、YEARS 按 referenceTime 和 IANA timeZone 推进日历周期，可能受夏令时影响。")
public enum TrackTimeBoundaryUnit {
    MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS
}
