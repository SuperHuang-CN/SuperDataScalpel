package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("相邻观测 gap 和固定周期边界配置。时间 gap、距离 gap、固定周期及启用的表达式彼此按 OR 生效；gap 只有严格超过阈值才切分，固定周期始终不共享端点。")
public record TrackBoundaryConfiguration(
        @JsonPropertyDescription("相邻有效观测允许的最大时间间隔，必须为有限正数并与单位同时提供；实际间隔严格大于该值时切分，等于阈值不切分。")
        Double maximumTimeGap,
        @JsonPropertyDescription("maximumTimeGap 的固定时长单位；DAYS 为固定 24 小时、WEEKS 为固定 7 天，不按时区日历推进。")
        SpatialDurationUnit maximumTimeGapUnit,
        @JsonPropertyDescription("相邻有效观测允许的最大空间距离，必须为有限正数并与单位同时提供；实际距离严格大于该值时切分。距离对象是原始观测 Geometry，不受后续缓冲影响。")
        Double maximumDistanceGap,
        @JsonPropertyDescription("maximumDistanceGap 的距离单位；必须能按所选 PLANAR 或 GEODESIC 方法换算。")
        SpatialDistanceUnit maximumDistanceGapUnit,
        @JsonPropertyDescription("可选参考时间对齐周期；跨周期的观测必定切分且不共享端点，与相邻时间/距离 gap 分别生效。")
        TrackFixedTimeBoundary fixedTimeBoundary
) {
    public TrackBoundaryConfiguration(Double maximumTimeGap, SpatialDurationUnit maximumTimeGapUnit,
                                      Double maximumDistanceGap, SpatialDistanceUnit maximumDistanceGapUnit) {
        this(maximumTimeGap, maximumTimeGapUnit, maximumDistanceGap, maximumDistanceGapUnit, null);
    }
}
