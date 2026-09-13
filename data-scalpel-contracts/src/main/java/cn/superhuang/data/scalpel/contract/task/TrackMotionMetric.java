package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TrackMotionMetric.Distance.class, name = "DISTANCE"),
        @JsonSubTypes.Type(value = TrackMotionMetric.ElevationChange.class, name = "ELEVATION_CHANGE"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Duration.class, name = "DURATION"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Speed.class, name = "SPEED"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Acceleration.class, name = "ACCELERATION"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Bearing.class, name = "BEARING"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Slope.class, name = "SLOPE"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Idle.class, name = "IDLE")
})
@JsonClassDescription("LEGACY_LAG 的逐观测轨迹运动指标；kind 决定输出距离、高程变化、时长、速度、加速度、方位角、百分比坡度或静止标记。前 historyPoints 个无足够历史的结果为 NULL。")
public sealed interface TrackMotionMetric permits
        TrackMotionMetric.Distance, TrackMotionMetric.ElevationChange,
        TrackMotionMetric.Duration, TrackMotionMetric.Speed,
        TrackMotionMetric.Acceleration, TrackMotionMetric.Bearing,
        TrackMotionMetric.Slope, TrackMotionMetric.Idle {

    String metricId();

    String outputColumnName();

    @JsonClassDescription("旧版距离指标：计算当前 Point 与向前 historyPoints 个观测 Point 的距离。")

    record Distance(
            @JsonPropertyDescription("当前运动指标项的稳定 ID。")
            String metricId,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("输出数值采用的单位。")
            SpatialDistanceUnit outputUnit
    )
            implements TrackMotionMetric {
    }

    @JsonClassDescription("旧版高程变化指标：用当前 Point Z 减去向前 historyPoints 个观测的 Point Z；要求 XYZ/XYZM。")

    record ElevationChange(
            @JsonPropertyDescription("当前运动指标项的稳定 ID。")
            String metricId,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("输出数值采用的单位。")
            SpatialDistanceUnit outputUnit
    )
            implements TrackMotionMetric {
    }

    @JsonClassDescription("旧版时长指标：计算当前时间与向前 historyPoints 个观测时间的差。")

    record Duration(
            @JsonPropertyDescription("当前运动指标项的稳定 ID。")
            String metricId,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("输出数值采用的单位。")
            SpatialDurationUnit outputUnit
    )
            implements TrackMotionMetric {
    }

    @JsonClassDescription("旧版速度指标：用 lag 距离除以 lag 时长；要求平面 CRS 可换算为米，或使用测地距离。")

    record Speed(
            @JsonPropertyDescription("当前运动指标项的稳定 ID。")
            String metricId,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("输出数值采用的单位。")
            SpatialSpeedUnit outputUnit
    )
            implements TrackMotionMetric {
    }

    @JsonClassDescription("旧版加速度指标：用当前 lag 速度与前一行 lag 速度之差除以当前 lag 时长。")

    record Acceleration(
            @JsonPropertyDescription("当前运动指标项的稳定 ID。")
            String metricId,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("输出数值采用的单位。")
            SpatialAccelerationUnit outputUnit
    )
            implements TrackMotionMetric {
    }

    @JsonClassDescription("旧版方位角指标：计算向前 historyPoints 个 Point 到当前 Point 的平面方位角，单位固定 DEGREES。")

    record Bearing(
            @JsonPropertyDescription("当前运动指标项的稳定 ID。")
            String metricId,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("固定填写 DEGREES；结果为从北方向顺时针的角度。")
            String outputUnit
    )
            implements TrackMotionMetric {
    }

    @JsonClassDescription("旧版坡度指标：Point Z 高差除以水平距离后乘 100，要求 XYZ/XYZM，单位固定 PERCENT。")

    record Slope(
            @JsonPropertyDescription("当前运动指标项的稳定 ID。")
            String metricId,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("固定填写 PERCENT。")
            String outputUnit
    )
            implements TrackMotionMetric {
    }

    @JsonClassDescription("旧版静止指标：lag 距离不大于 idleDistanceThreshold 时为 true；不使用时间阈值。")

    record Idle(
            @JsonPropertyDescription("当前运动指标项的稳定 ID。")
            String metricId,
            @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
            String outputColumnName,
            @JsonPropertyDescription("必须为 null；IDLE 输出可空 Boolean，不配置单位。")
            Void outputUnit
    )
            implements TrackMotionMetric {
    }
}
