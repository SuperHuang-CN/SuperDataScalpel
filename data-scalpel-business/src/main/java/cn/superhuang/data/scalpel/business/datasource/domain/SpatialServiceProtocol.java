package cn.superhuang.data.scalpel.business.datasource.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Read-only spatial feature service protocols managed by the datasource domain. */
@Schema(description = "只读空间要素服务协议：ARCGIS_REST ArcGIS REST 服务，WFS OGC Web Feature Service")
public enum SpatialServiceProtocol {
    ARCGIS_REST,
    WFS
}
