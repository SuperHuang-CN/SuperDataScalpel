package cn.superhuang.data.scalpel.contract.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Immutable PostGIS table snapshot used to publish one GeoServer feature layer. */
public record SpatialServiceDefinition(
        @JsonPropertyDescription("空间服务部署协议版本，服务引擎必须明确支持该版本。")
        @Min(1) int protocolVersion,
        @JsonPropertyDescription("数据库 Catalog 名；不支持时为空。")
        String catalog,
        @JsonPropertyDescription("数据库 Schema 名；不支持时为空。")
        @NotBlank String schema,
        @JsonPropertyDescription("数据库物理表名。")
        @NotBlank String table,
        @JsonPropertyDescription("空间服务读取的 Geometry 物理列名。")
        @NotBlank String geometryColumn,
        @JsonPropertyDescription("已确认的空间几何子类型。")
        @NotNull GeometryKind geometryKind,
        @JsonPropertyDescription("坐标参考系 EPSG 数字编码。")
        @Min(1) int epsg,
        @JsonPropertyDescription("空间要素的稳定唯一键物理列。")
        @NotBlank String primaryKeyColumn,
        @JsonPropertyDescription("对外发布的稳定图层名称。")
        @NotBlank String publishedName,
        @JsonPropertyDescription("空间图层面向用户展示的标题；可与稳定 publishedName 不同。")
        @NotBlank String title
) {
}
