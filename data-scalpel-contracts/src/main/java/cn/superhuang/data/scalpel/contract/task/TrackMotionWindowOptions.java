package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("轨迹运动历史窗口配置。每个输入观测仍输出一行并保留来源字段；窗口包含当前观测，点值取最多 N 个位置、段统计取最多 N−1 个完整段、加速度统计取最多 N−2 个完整加速度。历史不足时使用已有观测。")

public record TrackMotionWindowOptions(
        @JsonPropertyDescription("包含当前观测的窗口位置数，范围 1～100。N=1 时相邻瞬时值仍计算，但段汇总为 NULL；N<3 时加速度汇总为 NULL。")
        Integer observationCount,
        @JsonPropertyDescription("同一轨迹片段内在时间之后依次升序比较的字段，NULL 在前；完整排序键必须唯一，否则运行失败。")
        List<String> orderByColumns,
        @JsonPropertyDescription("窗口输出项；选择任一指标组时必须包含该组全部固定成员，每个 kind、稳定 ID 和输出字段名都必须唯一。")
        List<TrackMotionWindowStatistic> statistics,
        @JsonPropertyDescription("DISTANCE 组各项的输出单位；GEODESIC 不允许 SOURCE_CRS_UNIT，PLANAR 仅在来源 CRS 轴单位可换算时使用线性单位。")
        SpatialDistanceUnit distanceUnit,
        @JsonPropertyDescription("DURATION 组以及 TOT_IDLE_TIME 的输出单位；DAYS 是固定 24 小时，WEEKS 是固定 7 天。")
        SpatialDurationUnit durationUnit,
        @JsonPropertyDescription("速度结果的单位。")
        SpatialSpeedUnit speedUnit,
        @JsonPropertyDescription("加速度结果的单位。")
        SpatialAccelerationUnit accelerationUnit,
        @JsonPropertyDescription("高程来源字段；空值表示读取 Point Z，此时 Geometry 必须为 XYZ/XYZM；非空时可为 XY Point，但字段必须存在。非有限高程在计算中按 NULL，原字段值不变。")
        String elevationColumnName,
        @JsonPropertyDescription("输入高程或 Point Z 的明确线性单位；不能使用 SOURCE_CRS_UNIT。")
        SpatialDistanceUnit inputElevationUnit,
        @JsonPropertyDescription("ELEVATION 组数值的线性输出单位；不用于无量纲坡度。")
        SpatialDistanceUnit elevationUnit,
        @JsonPropertyDescription("静止分类的非负时间阈值；一个段只有在距离严格小于 idleDistanceThreshold 且时长严格大于该值时才为静止，等值不算静止。")
        Double idleTimeThreshold,
        @JsonPropertyDescription("静止时长阈值的单位。")
        SpatialDurationUnit idleTimeThresholdUnit
) {
    public TrackMotionWindowOptions {
        orderByColumns = orderByColumns == null ? List.of() : List.copyOf(orderByColumns);
        statistics = statistics == null ? List.of() : List.copyOf(statistics);
    }
}
