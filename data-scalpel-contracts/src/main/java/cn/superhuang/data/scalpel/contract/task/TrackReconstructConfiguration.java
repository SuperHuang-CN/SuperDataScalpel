package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("有界批处理轨迹重建配置；过滤时间或 Geometry 为空的观测后，按轨迹标识和完整排序键把观测切分为片段，并为每个片段生成一行路径或活动区域及摘要。该节点不保留事件时间或 Watermark，组内排序仍受 collect_list 容量约束。")

public record TrackReconstructConfiguration(
        @JsonPropertyDescription("当前操作读取的上游 Canvas 逻辑表名；必须引用此前节点已经产生的可用输出表。")
        String sourceTableName,
        @JsonPropertyDescription("输入观测 Geometry 字段名。线轨迹要求 Point；面轨迹支持 Point、Polygon 或 MultiPolygon，NULL 和 Empty 观测会在分组前过滤。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("共同标识一条轨迹的 1～8 个字段，按声明顺序组成分组键；输出首先保留这些字段。")
        List<String> trackIdColumns,
        @JsonPropertyDescription("观测时间字段名；必须为时间类型且非空观测才参与重建，按其升序后再按 reconstruction.orderByColumns 排序。")
        String timeColumnName,
        @JsonPropertyDescription("距离及几何构造方法。PLANAR 使用来源 CRS 坐标和可换算单位；GEODESIC 仅接受 EPSG:4326 XY，并使用 WGS84 椭球。")
        SpatialDistanceMethod distanceMethod,
        @JsonPropertyDescription("时间 gap、空间距离 gap 和固定周期边界；与启用的表达式条件按 OR 切分，等于 gap 阈值不切分，未配置的条件不参与。")
        TrackBoundaryConfiguration boundaries,
        @JsonPropertyDescription("对每个最终轨迹片段计算的 0～32 个摘要统计项；共享到相邻片段的端点会分别参与两段统计。")
        List<TrackSummaryStatistic> summaryStatistics,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("结果 Geometry 字段名。旧路径输出 LineString；METHOD_PATH 输出 MultiLineString；面轨迹输出 MultiPolygon。")
        String outputGeometryColumnName,
        @JsonPropertyDescription("结果片段最早观测时间的输出字段名。")
        String startTimeColumnName,
        @JsonPropertyDescription("结果片段最晚观测时间的输出字段名。")
        String endTimeColumnName,
        @JsonPropertyDescription("保存片段成员观测数的输出字段名；共享端点会在相邻两个片段中各计一次。")
        String pointCountColumnName,
        @JsonPropertyDescription("排序、表达式拆分、连接段归属以及路径或活动区域选项。为空或 semantics=LEGACY_POINTS 时使用旧版 Point→LineString 路径；非空有序模式才启用其余新语义。")
        TrackReconstructOptions reconstruction
) {
    public static final int MAX_TRACK_ID_COLUMNS = 8;
    public static final int MAX_SUMMARY_STATISTICS = 32;

    public TrackReconstructConfiguration {
        trackIdColumns = trackIdColumns == null ? null : List.copyOf(trackIdColumns);
        summaryStatistics = summaryStatistics == null ? null : List.copyOf(summaryStatistics);
    }

    public TrackReconstructConfiguration(String sourceTableName, String pointGeometryColumnName,
            List<String> trackIdColumns, String timeColumnName, SpatialDistanceMethod distanceMethod,
            TrackBoundaryConfiguration boundaries, List<TrackSummaryStatistic> summaryStatistics,
            String outputTableName, String outputGeometryColumnName, String startTimeColumnName,
            String endTimeColumnName, String pointCountColumnName) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod,
                boundaries, summaryStatistics, outputTableName, outputGeometryColumnName,
                startTimeColumnName, endTimeColumnName, pointCountColumnName, null);
    }

    public boolean usesOrderedReconstruction() {
        return reconstruction != null && reconstruction.semantics() != TrackReconstructSemantics.LEGACY_POINTS;
    }

    public boolean usesMethodPath() {
        return usesOrderedReconstruction() && !usesAreaGeometry() && reconstruction.pathGeometry() != null
                && reconstruction.pathGeometry().mode() != TrackPathGeometryMode.LEGACY_VERTEX_LINE;
    }

    public boolean usesAreaGeometry() {
        return usesOrderedReconstruction() && reconstruction.areaGeometry() != null
                && reconstruction.areaGeometry().active();
    }
}
