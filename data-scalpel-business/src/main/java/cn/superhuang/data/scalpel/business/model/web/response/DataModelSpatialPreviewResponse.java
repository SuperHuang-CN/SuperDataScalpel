package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "模型的 PostgreSQL/PostGIS 动态空间预览能力与安全限制；不返回 Geometry 数据")
public record DataModelSpatialPreviewResponse(
        @Schema(description = "当前模型和物理表是否支持空间瓦片预览") boolean supported,
        @Schema(description = "能力判断结果或不可预览原因") String message,
        @Schema(description = "模型中的 Geometry 字段及逐字段预览条件") List<GeometryField> geometryFields,
        @Schema(description = "地图图片请求 bbox 与服务端动态渲染使用的坐标系，当前为 EPSG:3857。") String displayCrs,
        @Schema(description = "浏览器地图初始 WGS84 经纬度范围 [west,south,east,north]，当前为固定业务默认范围；它不使用 displayCrs，也不会扫描全表 extent。") List<Double> initialBounds,
        @Schema(description = "空间预览请求和响应的服务端安全上限") Limits limits
) {
    public DataModelSpatialPreviewResponse {
        geometryFields = geometryFields == null ? List.of() : List.copyOf(geometryFields);
        initialBounds = List.copyOf(initialBounds);
    }

    @Schema(description = "一个 Geometry 字段的来源 CRS、索引和预览可用性")
    public record GeometryField(
            @Schema(description = "模型字段编码") String code,
            @Schema(description = "字段显示名称") String name,
            @Schema(description = "Geometry 子类型") GeometryKind kind,
            @Schema(description = "字段来源坐标参考系") CrsReference sourceCrs,
            @Schema(description = "物理列是否已有可用 GiST 或 SP-GiST 空间索引；平台不会自动创建") boolean spatialIndexAvailable,
            @Schema(description = "无空间索引时使用的模型物理统计估算行数；未刷新时为空") Long estimatedRowCount,
            @Schema(description = "是否需要先刷新模型物理统计才能判断无索引小表是否允许预览") boolean physicalStatisticsRefreshRequired,
            @Schema(description = "该字段当前是否允许受限空间预览") boolean previewAllowed,
            @Schema(description = "字段级能力判断或不可预览原因") String message
    ) {
    }

    @Schema(description = "空间预览渲染安全上限")
    public record Limits(
            @Schema(description = "请求图片最小宽度，单位像素") int minimumWidth,
            @Schema(description = "请求图片最大宽度，单位像素") int maximumWidth,
            @Schema(description = "请求图片最小高度，单位像素") int minimumHeight,
            @Schema(description = "请求图片最大高度，单位像素") int maximumHeight,
            @Schema(description = "单次视口最多读取和渲染的要素数") int maximumFeatures,
            @Schema(description = "单次渲染允许处理的最大坐标点数") int maximumCoordinates,
            @Schema(description = "单次渲染允许读取的 WKB 累计字节数") long maximumWkbBytes
    ) {
    }
}
