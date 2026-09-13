package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("空间汇总共享的时间切片；按左闭右开窗口复制并分组观测。repeatInterval 小于、等于或大于窗口长度时分别形成重叠、连续或留空窗口；落在间隙、右边界或 NULL 时间的观测不参与。")
public record SpatialTemporalSlicing(
        @JsonPropertyDescription("必填的 TIMESTAMP 来源字段名；NULL 时间不属于任何窗口。")
        String timeColumnName,
        @JsonPropertyDescription("必填的正整数窗口长度数量；FIXED_DURATION 与外层 intervalUnit 配合，CALENDAR 与 calendar.intervalUnit 配合。")
        long interval,
        @JsonPropertyDescription("FIXED_DURATION 必填的固定时长单位；DAYS 按 24 小时、WEEKS 按 7 天。CALENDAR 模式忽略并保留草稿值。")
        SpatialDurationUnit intervalUnit,
        @JsonPropertyDescription("可选的正整数相邻窗口起点步长；NULL 时步长等于窗口长度。FIXED_DURATION 与 repeatIntervalUnit 配合，CALENDAR 与 calendar.repeatIntervalUnit 配合。")
        Long repeatInterval,
        @JsonPropertyDescription("FIXED_DURATION 下与 repeatInterval 同时填写或同时为 NULL；CALENDAR 模式忽略并保留草稿值。")
        SpatialDurationUnit repeatIntervalUnit,
        @JsonPropertyDescription("可选的 ISO-8601 窗口对齐锚点；空白时使用 Unix Epoch。本地时间按 timeZone 解释；CALENDAR 模式要求本地时间不处于夏令时空隙或重叠且精度不超过微秒，FIXED_DURATION 以微秒精度使用解析结果。")
        String referenceTime,
        @JsonPropertyDescription("必填的有效 IANA Zone ID；解释无偏移 referenceTime，并在 CALENDAR 模式下决定自然日历边界和夏令时变化。")
        String timeZone,
        @JsonPropertyDescription("必填且不能与其他结果字段重名的非空 TIMESTAMP 窗口开始字段名。")
        String windowStartColumnName,
        @JsonPropertyDescription("必填且不能与其他结果字段重名的非空 TIMESTAMP 窗口结束字段名；观测恰好等于该时间时不属于窗口。")
        String windowEndColumnName,
        @JsonPropertyDescription("可选的 Canvas 4.39 时间语义对象；NULL 兼容旧定义并按 FIXED_DURATION 处理。对象存在但 mode=NULL 是无效草稿，CALENDAR 使用自然日历单位。")
        SpatialCalendarWindowOptions calendar
) {
    public SpatialTemporalSlicing(String timeColumnName, long interval, SpatialDurationUnit intervalUnit,
                                 Long repeatInterval, SpatialDurationUnit repeatIntervalUnit, String referenceTime,
                                 String timeZone, String windowStartColumnName, String windowEndColumnName) {
        this(timeColumnName, interval, intervalUnit, repeatInterval, repeatIntervalUnit, referenceTime, timeZone,
                windowStartColumnName, windowEndColumnName, null);
    }

    public boolean usesCalendar() {
        return calendar != null && calendar.mode() == SpatialCalendarWindowOptions.Mode.CALENDAR;
    }
}
