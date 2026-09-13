package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("仅支持有界批处理的轨迹运动统计配置；按轨迹标识、分段边界和时间顺序为 Point 观测追加运动字段。LEGACY_LAG 按固定历史偏移计算旧指标，OBSERVATION_WINDOW 同时提供相邻观测值和包含当前观测的滚动窗口统计。")

public record TrackMotionStatisticsConfiguration(
        @JsonPropertyDescription("当前操作读取的上游 Canvas 逻辑表名；必须引用此前节点已经产生的可用输出表。")
        String sourceTableName,
        @JsonPropertyDescription("输入 Point Geometry 字段名；GEODESIC 要求 EPSG:4326 XY。观测窗口模式会保留 NULL/Empty Geometry 行，但该行空间指标为 NULL，且不会跨过它连接更早有效点。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("共同标识一条轨迹的 1～8 个字段，按声明顺序组成分组键；不同轨迹的排序和窗口互不影响。")
        List<String> trackIdColumns,
        @JsonPropertyDescription("观测时间字段名；NULL 时间在观测窗口模式中不参与且不输出，相同时间须由 windowOptions.orderByColumns 形成唯一完整排序键。")
        String timeColumnName,
        @JsonPropertyDescription("距离计算方式，决定采用平面距离或测地线距离。")
        SpatialDistanceMethod distanceMethod,
        @JsonPropertyDescription("相邻时间/距离 gap 与固定周期边界；任一条件成立就重置历史窗口，未配置的条件不参与。")
        TrackBoundaryConfiguration boundaries,
        @JsonPropertyDescription("LEGACY_LAG 使用的历史偏移，范围 1～100；例如 1 表示与前一个观测比较。OBSERVATION_WINDOW 模式忽略但保留该旧字段。")
        int historyPoints,
        @JsonPropertyDescription("静止分类的非负距离阈值。旧 IDLE 使用不大于阈值；OBSERVATION_WINDOW 要求段距离严格小于该值，并同时满足时间阈值。未选择静止指标组时不使用。")
        Double idleDistanceThreshold,
        @JsonPropertyDescription("静止距离阈值单位。")
        SpatialDistanceUnit idleDistanceThresholdUnit,
        @JsonPropertyDescription("LEGACY_LAG 模式追加的 1～16 个逐观测指标；OBSERVATION_WINDOW 忽略但保留该旧草稿。")
        List<TrackMotionMetric> metrics,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("运动统计语义；null 为 LEGACY_LAG，OBSERVATION_WINDOW 使用 windowOptions 和完整排序键。切换模式不会删除另一套配置。")
        TrackMotionSemantics motionSemantics,
        @JsonPropertyDescription("OBSERVATION_WINDOW 模式的窗口、统计项和单位；其他模式不执行该对象。")
        TrackMotionWindowOptions windowOptions
) {
    public static final int MAX_TRACK_ID_COLUMNS = 8;
    public static final int MAX_HISTORY_POINTS = 100;
    public static final int MAX_METRICS = 16;

    public TrackMotionStatisticsConfiguration {
        trackIdColumns = trackIdColumns == null ? null : List.copyOf(trackIdColumns);
        metrics = metrics == null ? null : List.copyOf(metrics);
    }

    public boolean usesObservationWindow() { return motionSemantics == TrackMotionSemantics.OBSERVATION_WINDOW; }

    public TrackMotionStatisticsConfiguration(String sourceTableName, String pointGeometryColumnName,
            List<String> trackIdColumns, String timeColumnName, SpatialDistanceMethod distanceMethod,
            TrackBoundaryConfiguration boundaries, int historyPoints, Double idleDistanceThreshold,
            SpatialDistanceUnit idleDistanceThresholdUnit, List<TrackMotionMetric> metrics, String outputTableName) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod, boundaries,
                historyPoints, idleDistanceThreshold, idleDistanceThresholdUnit, metrics, outputTableName, null, null);
    }
}
