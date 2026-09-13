package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("最近要素匹配配置；Canvas 4.30 可选择旧 KNN 或真实距离路径，Canvas 4.48 可为真实距离路径显式开放非点 WGS84 Geometry，并可声明来源身份与连接线输出。")
public record SpatialNearestMatching(
        @JsonPropertyDescription("匹配语义；NULL 按 EXACT_DISTANCE 处理。EXACT_DISTANCE 使用真实距离、完整同距候选恢复和身份校验；LEGACY_KNN 保持旧 ST_KNN 候选截断语义，并忽略本对象的其他执行配置。")
        SpatialNearestMatchSemantics semantics,
        @JsonPropertyDescription("EXACT_DISTANCE 必填且不能为 Geometry 的来源身份字段名；运行数据中的值必须非 NULL 且唯一，使 Geometry 相同的来源行仍保持独立。LEGACY_KNN 忽略。")
        String sourceIdColumnName,
        @JsonPropertyDescription("EXACT_DISTANCE 可选的独立连接线表配置；NULL 或 enabled 不为 true 时不输出。LEGACY_KNN 忽略。")
        SpatialNearestConnectionLines connectionLines,
        @JsonPropertyDescription("EXACT_DISTANCE 的 WGS84 Geometry 范围；缺失或 NULL 按旧版 POINT_ONLY，只接受 Point。GEOMETRY 从 Canvas 4.48 开始支持 Point/MultiPoint/LineString/MultiLineString/Polygon/MultiPolygon，并使用真实最近位置而非质心。LEGACY_KNN 忽略执行设置，但显式 GEOMETRY 仍要求 4.48。")
        SpatialNearestGeodesicGeometryMode geodesicGeometryMode
) {
    public SpatialNearestMatching(
            SpatialNearestMatchSemantics semantics,
            String sourceIdColumnName,
            SpatialNearestConnectionLines connectionLines
    ) {
        this(semantics, sourceIdColumnName, connectionLines, null);
    }

    public SpatialNearestGeodesicGeometryMode effectiveGeodesicGeometryMode() {
        return geodesicGeometryMode == null
                ? SpatialNearestGeodesicGeometryMode.POINT_ONLY
                : geodesicGeometryMode;
    }

    public boolean allowsGeodesicGeometry() {
        return effectiveGeodesicGeometryMode() == SpatialNearestGeodesicGeometryMode.GEOMETRY;
    }
}
