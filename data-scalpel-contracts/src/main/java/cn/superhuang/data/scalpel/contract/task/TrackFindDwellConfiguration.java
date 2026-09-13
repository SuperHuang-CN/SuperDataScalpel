package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("仅支持有界批处理的 Point 轨迹驻留分析。LEGACY_ADJACENT 把相邻距离未超阈值的连续观测聚为候选；REFERENCE_CENTER 以候选首点和冻结种子中心确定不重用的驻留范围，并支持驻留级或点级四种结果。单个超大轨迹片段仍有 Executor 内存和反复扫描风险。")

public record TrackFindDwellConfiguration(
        @JsonPropertyDescription("当前操作读取的上游 Canvas 逻辑表名；必须引用此前节点已经产生的可用输出表。")
        String sourceTableName,
        @JsonPropertyDescription("输入 Point Geometry 字段名；REFERENCE_CENTER 仅接受 XY。NULL/Empty 会阻断连续候选且不属于驻留；非 Point、非有限坐标或非法测地经纬度会失败。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("共同标识一条轨迹的 1～8 个字段，按声明顺序组成分组键；不同轨迹和边界片段互不共享驻留候选。")
        List<String> trackIdColumns,
        @JsonPropertyDescription("观测时间字段名；REFERENCE_CENTER 中 NULL 时间不参与且不输出，并在时间之后使用 rangeOptions.orderByColumns 形成唯一完整顺序。")
        String timeColumnName,
        @JsonPropertyDescription("距离计算方式，决定采用平面距离或测地线距离。")
        SpatialDistanceMethod distanceMethod,
        @JsonPropertyDescription("有限正数距离容差。LEGACY_ADJACENT 比较相邻观测；REFERENCE_CENTER 先比较候选首点，再用冻结的种子均值中心向前、向后扩展；距离等于阈值仍纳入。")
        double distanceThreshold,
        @JsonPropertyDescription("距离阈值的单位。")
        SpatialDistanceUnit distanceThresholdUnit,
        @JsonPropertyDescription("判定为驻留事件所需的最短持续时长，必须是有限正数；单位由 minimumDurationUnit 指定。")
        double minimumDuration,
        @JsonPropertyDescription("minimumDuration 的固定时长单位；REFERENCE_CENTER 中候选首末时长大于或等于阈值即可成为种子。")
        SpatialDurationUnit minimumDurationUnit,
        @JsonPropertyDescription("用于切分连续轨迹段的时间间隔、空间距离和固定时间边界；未配置的边界条件不参与切分。")
        TrackBoundaryConfiguration boundaries,
        @JsonPropertyDescription("每个聚合驻留计算的 0～32 个摘要项；MEAN_CENTERS/CONVEX_HULLS 使用，DWELL_FEATURES/ALL_FEATURES 忽略但保留草稿。固定 pointCount 不依赖此列表。")
        List<TrackSummaryStatistic> summaryStatistics,
        @JsonPropertyDescription("LEGACY_ADJACENT 的聚合 Geometry：CENTROID 为成员集合质心，CONVEX_HULL 为凸包且可能退化为点或线。REFERENCE_CENTER 由 rangeOptions.resultMode 决定并忽略该旧字段。")
        DwellGeometryKind outputGeometryKind,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("驻留 ID 输出字段名。ID 由轨迹键、边界片段和成员序号生成；相同输入与明确顺序下跨输出模式一致，但输入改变后不保证稳定，也不等于外部产品 ID。")
        String dwellIdColumnName,
        @JsonPropertyDescription("聚合驻留成员最早观测时间的输出字段名；点级输出不使用。")
        String startTimeColumnName,
        @JsonPropertyDescription("聚合驻留成员最晚观测时间的输出字段名；点级输出不使用。")
        String endTimeColumnName,
        @JsonPropertyDescription("聚合驻留的结束时间减开始时间输出字段名；REFERENCE_CENTER 使用 rangeOptions.durationUnit，LEGACY_ADJACENT 使用 minimumDurationUnit；点级输出不使用。")
        String durationColumnName,
        @JsonPropertyDescription("聚合驻留成员观测数的输出字段名；点级输出不使用。")
        String pointCountColumnName,
        @JsonPropertyDescription("聚合驻留 Geometry 输出字段名；MEAN_CENTERS 为 Point，CONVEX_HULLS 声明通用 Geometry 并可能退化为点或线；点级输出保留原 Geometry，不使用该字段。")
        String outputGeometryColumnName,
        @JsonPropertyDescription("驻留算法语义；null 为 LEGACY_ADJACENT，REFERENCE_CENTER 启用冻结种子中心算法和 rangeOptions 四种结果。")
        TrackDwellSemantics dwellSemantics,
        @JsonPropertyDescription("REFERENCE_CENTER 的排序、结果粒度、单位和点级标记字段；其他语义不执行该对象。")
        TrackDwellRangeOptions rangeOptions
) {
    public static final int MAX_TRACK_ID_COLUMNS = 8;
    public static final int MAX_SUMMARY_STATISTICS = 32;

    public TrackFindDwellConfiguration {
        trackIdColumns = trackIdColumns == null ? null : List.copyOf(trackIdColumns);
        summaryStatistics = summaryStatistics == null ? null : List.copyOf(summaryStatistics);
    }

    public boolean usesReferenceCenter() { return dwellSemantics == TrackDwellSemantics.REFERENCE_CENTER; }

    public TrackFindDwellConfiguration(String sourceTableName, String pointGeometryColumnName,
            List<String> trackIdColumns, String timeColumnName, SpatialDistanceMethod distanceMethod,
            double distanceThreshold, SpatialDistanceUnit distanceThresholdUnit, double minimumDuration,
            SpatialDurationUnit minimumDurationUnit, TrackBoundaryConfiguration boundaries,
            List<TrackSummaryStatistic> summaryStatistics, DwellGeometryKind outputGeometryKind,
            String outputTableName, String dwellIdColumnName, String startTimeColumnName, String endTimeColumnName,
            String durationColumnName, String pointCountColumnName, String outputGeometryColumnName) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod,
                distanceThreshold, distanceThresholdUnit, minimumDuration, minimumDurationUnit, boundaries,
                summaryStatistics, outputGeometryKind, outputTableName, dwellIdColumnName, startTimeColumnName,
                endTimeColumnName, durationColumnName, pointCountColumnName, outputGeometryColumnName, null, null);
    }
}
