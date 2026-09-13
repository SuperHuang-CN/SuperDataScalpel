package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间要素服务协议：ARCGIS_REST 为 ArcGIS Feature Service REST，WFS 为 OGC Web Feature Service；各协议的资源标识、分页和响应格式不同。")
public enum SpatialServiceProtocol {
    ARCGIS_REST,
    WFS
}
