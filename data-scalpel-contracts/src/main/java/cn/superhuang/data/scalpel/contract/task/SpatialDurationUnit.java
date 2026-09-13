package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("固定时长单位：毫秒、秒、分钟、小时、固定 24 小时日和固定 7 天周；不按 IANA 时区推进日历日或日历周。")
public enum SpatialDurationUnit {
    MILLISECONDS,
    SECONDS,
    MINUTES,
    HOURS,
    DAYS,
    WEEKS
}
