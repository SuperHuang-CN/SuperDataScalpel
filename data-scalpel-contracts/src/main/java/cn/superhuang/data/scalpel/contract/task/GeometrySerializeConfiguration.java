package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("逐行 Geometry 序列化配置，支持批处理和流处理。保留原 Geometry 及全部来源字段，并追加一个普通 STRING 或 BINARY 字段；NULL 输入返回 NULL且不改变行数。节点只改变表示形式，不转换 CRS、不修复或简化 Geometry，也不生成 GeoJSON Feature/FeatureCollection。")
public record GeometrySerializeConfiguration(
        @JsonPropertyDescription("要序列化的上游 Canvas 逻辑表名，必须精确引用此前节点已经产生的可用输出表；其他上游表继续传播但不参与序列化。")
        String sourceTableName,
        @JsonPropertyDescription("追加序列化结果使用的 Canvas 逻辑表名，必须与当前所有上游表名不同；不会替换来源表。")
        String outputTableName,
        @JsonPropertyDescription("要序列化的来源 Geometry 字段名，必须具有完整的受支持 Geometry 定义；原 Geometry 字段及其 kind、CRS、dimension 保持不变。")
        String geometryColumnName,
        @JsonPropertyDescription("追加的标量字段名，不能与来源字段重名。WKT/GEOJSON 输出 nullable STRING，WKB 输出 nullable BINARY；nullable 与来源 Geometry 字段一致。")
        String outputColumnName,
        @JsonPropertyDescription("必填序列化格式。WKT 输出文本但不携带可信 CRS；WKB 输出原始二进制而不是十六进制文本且不携带平台可信 CRS；GEOJSON 输出单个 Geometry JSON 文本并要求来源为 EPSG:4326。")
        GeometrySerializationFormat format
) {
}
