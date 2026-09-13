package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;

@JsonClassDescription("Geometry 构造配置；保留来源表全部字段，并从 WKT、WKB、GeoJSON 或 X/Y 字段追加一个具体 EPSG + XY Geometry 字段。targetGeometry 的 CRS 直接赋给解析结果，输入坐标必须已经属于该 CRS；本节点不执行坐标转换。")

public record GeometryConstructConfiguration(
        @JsonPropertyDescription("要读取的上游 Canvas 逻辑表名；必须引用进入节点前已经存在的表。")
        String sourceTableName,
        @JsonPropertyDescription("追加 Geometry 后的新逻辑表名；必须与进入节点时已有的所有表名不同，原来源表仍保留。")
        String outputTableName,
        @JsonPropertyDescription("追加在全部来源字段之后的 Geometry 字段名；不能与来源表任何字段同名。")
        String outputColumnName,
        @JsonPropertyDescription("必填的严格判别来源；WKT/GEOJSON 读取 STRING，WKB 读取 BINARY，POINT_FROM_XY 读取两个数值字段。来源为 NULL 时结果为 NULL；格式损坏或实际 GeometryKind 不符会在运行时失败。")
        GeometryConstructSource source,
        @JsonPropertyDescription("输出 Geometry 的必填类型；必须声明具体 GeometryKind、XY 维度和 code>0 的 EPSG CRS。POINT_FROM_XY 只允许 POINT。解析来源的真实类型必须与声明 kind 一致，不能使用通用 GEOMETRY。")
        GeometryTypeDefinition targetGeometry
) {
}
