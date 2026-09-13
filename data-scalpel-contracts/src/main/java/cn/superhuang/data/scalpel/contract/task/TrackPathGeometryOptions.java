package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("有序线轨迹的路径几何参数。METHOD_PATH 在 PLANAR 下把原始顶点包装为 MultiLineString，在 GEODESIC 下沿 WGS84 最短测地线加密并处理日期线；LEGACY_VERTEX_LINE 输出旧版 LineString。")
public record TrackPathGeometryOptions(
        @JsonPropertyDescription("轨迹几何构建方式；null 按 METHOD_PATH。METHOD_PATH 输出 MultiLineString 并遵循 distanceMethod，LEGACY_VERTEX_LINE 直接连接原始顶点并输出 LineString。")
        TrackPathGeometryMode mode,
        @JsonPropertyDescription("GEODESIC METHOD_PATH 中相邻插值顶点允许的最大距离，必须为有限正数；插值只改变 Geometry，不增加观测数或影响统计。单片段最多生成 1,000,000 个顶点，超限失败而不截断。")
        Double maximumGeodesicSegmentLength,
        @JsonPropertyDescription("maximumGeodesicSegmentLength 的线性距离单位；不接受 SOURCE_CRS_UNIT。")
        SpatialDistanceUnit maximumGeodesicSegmentLengthUnit
) { }
