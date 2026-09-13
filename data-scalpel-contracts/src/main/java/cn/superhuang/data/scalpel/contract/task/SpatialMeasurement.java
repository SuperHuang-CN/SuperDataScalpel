package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SpatialMeasurement.Area.class, name = "AREA"),
        @JsonSubTypes.Type(value = SpatialMeasurement.Length.class, name = "LENGTH"),
        @JsonSubTypes.Type(value = SpatialMeasurement.Perimeter.class, name = "PERIMETER"),
        @JsonSubTypes.Type(value = SpatialMeasurement.Distance.class, name = "DISTANCE"),
        @JsonSubTypes.Type(value = SpatialMeasurement.X.class, name = "X"),
        @JsonSubTypes.Type(value = SpatialMeasurement.Y.class, name = "Y")
})
@JsonClassDescription("空间量测多态配置项，以 kind 为判别字段。所有输入必须是带完整 EPSG CRS 的 XY Geometry；任一所需 Geometry 为 NULL 时输出 NULL。结果追加为 nullable DOUBLE，不改变来源字段或行数；不同项相互独立且不能引用同节点新生成的结果字段。")
public sealed interface SpatialMeasurement permits
        SpatialMeasurement.Area,
        SpatialMeasurement.Length,
        SpatialMeasurement.Perimeter,
        SpatialMeasurement.Distance,
        SpatialMeasurement.X,
        SpatialMeasurement.Y {

    String outputColumnName();

    @JsonClassDescription("AREA：计算 Polygon 或 MultiPolygon 的面积。PLANAR 原始结果为来源 CRS 坐标单位的平方；SPHEROID 原始结果为平方米。Canvas 4.50 可显式选择面积输出单位。")

    record Area(
            @JsonPropertyDescription("面积来源字段名，只接受 POLYGON 或 MULTIPOLYGON；NULL 时面积结果为 NULL。")
            String geometryColumnName,
            @JsonPropertyDescription("必填面积模式。PLANAR 使用来源 CRS 坐标单位平方，EPSG:4326 时为角度平方并产生警告；SPHEROID 使用 WGS84 椭球，单位为平方米。")
            SpatialMeasureMode mode,
            @JsonPropertyDescription("追加的 nullable DOUBLE 面积字段名，不能与来源字段或其他量测输出重名；Schema 不附加单位元数据。")
            String outputColumnName,
            @JsonPropertyDescription("Canvas 4.50 起可选的显式面积输出单位。缺失或 null 保持旧结果：PLANAR 为来源 CRS 坐标单位平方，SPHEROID 为平方米。投影 PLANAR 可可靠换算，地理 PLANAR 的角度平方不能换算为固定面积单位。")
            SpatialAreaUnit outputUnit
    ) implements SpatialMeasurement {
        public Area(String geometryColumnName, SpatialMeasureMode mode, String outputColumnName) {
            this(geometryColumnName, mode, outputColumnName, null);
        }
    }

    @JsonClassDescription("LENGTH：计算 LineString 或 MultiLineString 的长度。PLANAR 原始结果为来源 CRS 坐标单位；SPHEROID 原始结果为米。Canvas 4.50 可显式选择距离输出单位。")

    record Length(
            @JsonPropertyDescription("长度来源字段名，只接受 LINESTRING 或 MULTILINESTRING；NULL 时长度结果为 NULL。")
            String geometryColumnName,
            @JsonPropertyDescription("必填长度模式。PLANAR 使用来源 CRS 坐标单位，EPSG:4326 时为角度并产生警告；SPHEROID 使用 WGS84 椭球，单位为米。")
            SpatialMeasureMode mode,
            @JsonPropertyDescription("追加的 nullable DOUBLE 长度字段名，不能与来源字段或其他量测输出重名；Schema 不附加单位元数据。")
            String outputColumnName,
            @JsonPropertyDescription("Canvas 4.50 起可选的显式长度输出单位。缺失或 null 保持旧结果：PLANAR 为来源 CRS 坐标单位，SPHEROID 为米。地理 PLANAR 只允许 SOURCE_CRS_UNIT。")
            SpatialDistanceUnit outputUnit
    ) implements SpatialMeasurement {
        public Length(String geometryColumnName, SpatialMeasureMode mode, String outputColumnName) {
            this(geometryColumnName, mode, outputColumnName, null);
        }
    }

    @JsonClassDescription("PERIMETER：计算 Polygon 或 MultiPolygon 的周长。PLANAR 原始结果为来源 CRS 坐标单位；SPHEROID 原始结果为米。Canvas 4.50 可显式选择距离输出单位。")

    record Perimeter(
            @JsonPropertyDescription("周长来源字段名，只接受 POLYGON 或 MULTIPOLYGON；NULL 时周长结果为 NULL。")
            String geometryColumnName,
            @JsonPropertyDescription("必填周长模式。PLANAR 使用来源 CRS 坐标单位，EPSG:4326 时为角度并产生警告；SPHEROID 使用 WGS84 椭球，单位为米。")
            SpatialMeasureMode mode,
            @JsonPropertyDescription("追加的 nullable DOUBLE 周长字段名，不能与来源字段或其他量测输出重名；Schema 不附加单位元数据。")
            String outputColumnName,
            @JsonPropertyDescription("Canvas 4.50 起可选的显式周长输出单位。缺失或 null 保持旧结果：PLANAR 为来源 CRS 坐标单位，SPHEROID 为米。地理 PLANAR 只允许 SOURCE_CRS_UNIT。")
            SpatialDistanceUnit outputUnit
    ) implements SpatialMeasurement {
        public Perimeter(String geometryColumnName, SpatialMeasureMode mode, String outputColumnName) {
            this(geometryColumnName, mode, outputColumnName, null);
        }
    }

    @JsonClassDescription("DISTANCE：逐行计算两个 Geometry 字段之间的最短距离。PLANAR 要求两侧 CRS 和维度完全一致；SPHEROID 要求两侧均为 EPSG:4326 XY。Canvas 4.50 可显式选择距离输出单位。")

    record Distance(
            @JsonPropertyDescription("左侧来源 Geometry 字段名，可为当前支持的任意 GeometryKind；任一侧为 NULL 时距离结果为 NULL。")
            String leftGeometryColumnName,
            @JsonPropertyDescription("右侧来源 Geometry 字段名，可为当前支持的任意 GeometryKind；PLANAR 时必须与左侧 CRS、维度一致，SPHEROID 时必须同为 EPSG:4326 XY。")
            String rightGeometryColumnName,
            @JsonPropertyDescription("必填距离模式。PLANAR 使用来源 CRS 坐标单位，EPSG:4326 时为角度并产生警告；SPHEROID 使用 WGS84 椭球测地距离，单位为米。")
            SpatialMeasureMode mode,
            @JsonPropertyDescription("追加的 nullable DOUBLE 距离字段名，不能与来源字段或其他量测输出重名；Schema 不附加单位元数据。")
            String outputColumnName,
            @JsonPropertyDescription("Canvas 4.50 起可选的显式距离输出单位。缺失或 null 保持旧结果：PLANAR 为来源 CRS 坐标单位，SPHEROID 为米。地理 PLANAR 只允许 SOURCE_CRS_UNIT。")
            SpatialDistanceUnit outputUnit
    ) implements SpatialMeasurement {
        public Distance(
                String leftGeometryColumnName,
                String rightGeometryColumnName,
                SpatialMeasureMode mode,
                String outputColumnName
        ) {
            this(leftGeometryColumnName, rightGeometryColumnName, mode, outputColumnName, null);
        }
    }

    @JsonClassDescription("X：提取 Point 在其当前 CRS 下的原始 X 坐标。只接受 POINT，不转换为经度或米，也不使用 mode。")

    record X(
            @JsonPropertyDescription("坐标来源字段名，只接受 POINT；NULL 时 X 结果为 NULL。")
            String geometryColumnName,
            @JsonPropertyDescription("追加的 nullable DOUBLE X 坐标字段名，不能与来源字段或其他量测输出重名；数值单位沿用当前 CRS 坐标轴。")
            String outputColumnName
    ) implements SpatialMeasurement {
    }

    @JsonClassDescription("Y：提取 Point 在其当前 CRS 下的原始 Y 坐标。只接受 POINT，不转换为纬度或米，也不使用 mode。")

    record Y(
            @JsonPropertyDescription("坐标来源字段名，只接受 POINT；NULL 时 Y 结果为 NULL。")
            String geometryColumnName,
            @JsonPropertyDescription("追加的 nullable DOUBLE Y 坐标字段名，不能与来源字段或其他量测输出重名；数值单位沿用当前 CRS 坐标轴。")
            String outputColumnName
    ) implements SpatialMeasurement {
    }
}
