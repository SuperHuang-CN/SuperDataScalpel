package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Explicit time semantics; existing fixed-duration units remain available as an inactive draft. */
@JsonClassDescription("Canvas 4.39 时间窗口语义；FIXED_DURATION 保持按微秒换算的旧窗口，CALENDAR 按 IANA 时区中的自然日、周、月、年或固定小单位推进，月末、闰年和夏令时会影响实际持续时间。")
public record SpatialCalendarWindowOptions(
        @JsonPropertyDescription("必填的时间语义：FIXED_DURATION 使用 SpatialTemporalSlicing 外层固定单位并忽略本对象两个 Unit；CALENDAR 使用本对象的自然日历单位。")
        Mode mode,
        @JsonPropertyDescription("CALENDAR 必填的窗口长度单位；MILLISECONDS 至 HOURS 按固定时长，DAYS/WEEKS/MONTHS/YEARS 按 timeZone 中的日历推进。FIXED_DURATION 忽略。")
        Unit intervalUnit,
        @JsonPropertyDescription("CALENDAR 在 repeatInterval 非 NULL 时必填的窗口起点步长单位；repeatInterval 为 NULL 时默认使用 interval 和 intervalUnit。FIXED_DURATION 忽略。")
        Unit repeatIntervalUnit
) {
    @JsonClassDescription("时间切片模式：FIXED_DURATION 按固定微秒长度和步长；CALENDAR 按时区日历边界推进。")
    public enum Mode { FIXED_DURATION, CALENDAR }
    @JsonClassDescription("日历模式单位；MILLISECONDS/SECONDS/MINUTES/HOURS 为固定时长，DAYS/WEEKS/MONTHS/YEARS 按所选 IANA 时区的日历规则推进。")
    public enum Unit { MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }
}
