package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("ArcGIS 风格的有界 Point 轨迹路网吸附配置；按时间次序、线连接关系、可选方向和距离联合选择匹配线，不执行逐点最近线替代。")
public record SnapTracksConfiguration(
        @JsonPropertyDescription("包含时间观测点的有界 Canvas 逻辑表名。")
        String pointTableName,
        @JsonPropertyDescription("点表中带完整 CRS 的 XY Point Geometry 字段名。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("共同标识一条轨迹的 1～8 个非 Geometry 字段。")
        List<String> trackIdColumns,
        @JsonPropertyDescription("点表中的 TIMESTAMP 即时时间字段；NULL 时间观测不参与分析。")
        String timeColumnName,
        @JsonPropertyDescription("同轨迹同时间的稳定次序字段；完整排序键必须唯一。")
        List<String> orderByColumns,
        @JsonPropertyDescription("包含可通行网络线的有界 Canvas 逻辑表名。")
        String lineTableName,
        @JsonPropertyDescription("线表中带完整 CRS 的 XY LineString Geometry 字段名；首尾点方向须与 from/to 节点一致。")
        String lineGeometryColumnName,
        @JsonPropertyDescription("网络线唯一标识字段名；运行时必须非空且唯一。")
        String lineIdColumnName,
        @JsonPropertyDescription("网络线起点节点标识字段名；类型须与终点节点一致。")
        String fromNodeColumnName,
        @JsonPropertyDescription("网络线终点节点标识字段名；类型须与起点节点一致。")
        String toNodeColumnName,
        @JsonPropertyDescription("观测点到网络线的有限正搜索距离。")
        Double searchDistance,
        @JsonPropertyDescription("搜索距离单位。")
        SpatialDistanceUnit searchDistanceUnit,
        @JsonPropertyDescription("PLANAR 要求投影 CRS；GEODESIC 仅支持 EPSG:4326 XY。")
        SpatialDistanceMethod distanceMethod,
        @JsonPropertyDescription("可选相邻时间 gap、相邻距离 gap 和固定时间周期切分；三者按 OR 生效。")
        TrackBoundaryConfiguration boundaries,
        @JsonPropertyDescription("可选网络线方向值映射；null 表示所有网络线双向可通行。")
        SnapTracksDirectionMatching directionMatching,
        @JsonPropertyDescription("0～32 个要投影到结果的网络线属性；数组顺序决定结果字段顺序。")
        List<SnapTracksLineField> lineFields,
        @JsonPropertyDescription("返回全部观测或仅返回匹配观测。")
        SnapTracksOutputMode outputMode,
        @JsonPropertyDescription("吸附结果 Canvas 逻辑表名。")
        String outputTableName,
        @JsonPropertyDescription("结果追加的吸附 Point Geometry 字段；未匹配观测在 ALL_FEATURES 模式保留原 Point。")
        String snappedGeometryColumnName,
        @JsonPropertyDescription("结果追加的匹配网络线唯一 ID 字段名。")
        String matchedLineIdColumnName,
        @JsonPropertyDescription("结果追加的匹配状态 STRING 字段名，值为 M 或 U。")
        String matchStatusColumnName,
        @JsonPropertyDescription("结果追加的原始点 X 坐标 DOUBLE 字段名。")
        String originalXColumnName,
        @JsonPropertyDescription("结果追加的原始点 Y 坐标 DOUBLE 字段名。")
        String originalYColumnName,
        @JsonPropertyDescription("结果追加的匹配点 X 坐标 DOUBLE 字段名；未匹配时为 null。")
        String matchXColumnName,
        @JsonPropertyDescription("结果追加的匹配点 Y 坐标 DOUBLE 字段名；未匹配时为 null。")
        String matchYColumnName,
        @JsonPropertyDescription("结果追加的原点到匹配点距离 DOUBLE 字段名，固定以米表示；未匹配时为 null。")
        String matchDistanceColumnName
) {
    public static final int MAX_TRACK_ID_COLUMNS = 8;
    public static final int MAX_LINE_FIELDS = 32;
    public static final int MAX_CANDIDATES_PER_OBSERVATION = 32;

    public SnapTracksConfiguration {
        trackIdColumns = trackIdColumns == null ? null : List.copyOf(trackIdColumns);
        orderByColumns = orderByColumns == null ? null : List.copyOf(orderByColumns);
        lineFields = lineFields == null ? null : List.copyOf(lineFields);
    }
}
