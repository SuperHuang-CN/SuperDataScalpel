package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("REFERENCE_CENTER 驻留的排序与结果选项。MEAN_CENTERS/CONVEX_HULLS 每个驻留一行；DWELL_FEATURES 仅输出驻留成员原行；ALL_FEATURES 输出所有非空时间原行并标记归属。")

public record TrackDwellRangeOptions(
        @JsonPropertyDescription("驻留结果形态。两个聚合模式输出轨迹 ID、驻留 ID、起止、时长、成员数、平均相邻距离、摘要和 Geometry；两个点级模式保留来源字段并追加驻留 ID 与布尔标记。")
        TrackDwellResultMode resultMode,
        @JsonPropertyDescription("在时间字段之后依次升序比较的字段，NULL 在前；完整排序键必须唯一，否则运行失败。排序决定候选连续性以及 FIRST/LAST。")
        List<String> orderByColumns,
        @JsonPropertyDescription("聚合驻留 durationColumnName 的输出单位；不改变 minimumDuration 的输入单位。点级结果不使用。")
        SpatialDurationUnit durationUnit,
        @JsonPropertyDescription("聚合驻留中相邻成员按完整顺序形成的 N−1 个段的平均距离输出字段名；它不是成员到中心的平均半径。点级结果不使用。")
        String meanDistanceColumnName,
        @JsonPropertyDescription("meanDistanceColumnName 的距离单位；必须能按所选 PLANAR/GEODESIC 方法换算。点级结果不使用。")
        SpatialDistanceUnit meanDistanceUnit,
        @JsonPropertyDescription("点级结果追加的非空 Boolean 字段名；驻留成员为 true，ALL_FEATURES 中非成员和 NULL/Empty Geometry 行为 false。聚合结果不使用。")
        String dwellFlagColumnName
) {
    public TrackDwellRangeOptions {
        orderByColumns = orderByColumns == null ? List.of() : List.copyOf(orderByColumns);
    }
}
