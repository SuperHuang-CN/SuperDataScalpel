package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Optional, reference-aligned track reset period, distinct from an adjacent-observation gap. */
@JsonClassDescription("轨迹分段的固定时间窗口；按间隔、参考时间和时区划分记录。")
public record TrackFixedTimeBoundary(
        @JsonPropertyDescription("固定时长或日历窗口的间隔数量。")
        Integer interval,
        @JsonPropertyDescription("当前数值的计量单位。")
        TrackTimeBoundaryUnit unit,
        @JsonPropertyDescription("窗口对齐锚点的 ISO-8601 偏移时间或本地时间；为空时使用 Unix Epoch，本地时间按 timeZone 解释且不能落在夏令时歧义区间。")
        String referenceTime,
        @JsonPropertyDescription("解释本地时间或日历边界的 IANA 时区。")
        String timeZone
) {
}
