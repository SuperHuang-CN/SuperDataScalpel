package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Geometry 序列化格式。WKT 产生 STRING；WKB 产生 BINARY，不转换成十六进制文本；GEOJSON 产生单个 Geometry 的 STRING，仅允许 EPSG:4326，不包含 Feature、properties 或 FeatureCollection。WKT/WKB 不携带平台可信 CRS，重新构造 Geometry 时仍须显式声明 CRS。")
public enum GeometrySerializationFormat {
    WKT,
    WKB,
    GEOJSON
}
