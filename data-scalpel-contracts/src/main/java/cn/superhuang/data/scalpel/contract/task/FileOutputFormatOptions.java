package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FileOutputFormatOptions.Csv.class, name = "CSV"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.JsonLines.class, name = "JSON_LINES"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.Parquet.class, name = "PARQUET"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.Shapefile.class, name = "SHAPEFILE"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.GeoParquet.class, name = "GEOPARQUET"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.GeoJson.class, name = "GEOJSON")
})
@JsonClassDescription("文件输出的严格格式联合；type 决定唯一配置分支。CSV、JSON_LINES 和普通 PARQUET 是可含多个 part 文件的 Spark 目录数据集且不接受未序列化 Geometry；SHAPEFILE 与 GEOJSON 生成单套 Driver 制品；GEOPARQUET 生成带空间 Footer 的分布式目录数据集。")
public sealed interface FileOutputFormatOptions permits
        FileOutputFormatOptions.Csv,
        FileOutputFormatOptions.JsonLines,
        FileOutputFormatOptions.Parquet,
        FileOutputFormatOptions.Shapefile,
        FileOutputFormatOptions.GeoParquet,
        FileOutputFormatOptions.GeoJson {

    @JsonClassDescription("CSV 目录数据集格式；使用 UTF-8，文件数、名称和行顺序由 Spark 分区与 Writer 决定。来源不能保留 Geometry；需要单文件、字段选择或类型转换时须在上游另行处理。")

    record Csv(
            @JsonPropertyDescription("每个 CSV part 文件首行是否写入字段名表头。")
            boolean header,
            @JsonPropertyDescription("CSV 字段之间使用的必填单字符分隔符。")
            String delimiter,
            @JsonPropertyDescription("CSV 包裹字段值使用的必填单字符引号。")
            String quote,
            @JsonPropertyDescription("CSV 转义特殊字符使用的必填单字符。")
            String escape,
            @JsonPropertyDescription("CSV 中表示 SQL NULL 的必填文本；允许使用空字符串，此时 NULL 与真实空字符串在文件文本中可能无法区分。")
            String nullValue
    ) implements FileOutputFormatOptions {
    }

    @JsonClassDescription("JSON Lines 目录数据集格式；使用 UTF-8，每条记录写为一行 JSON 对象，文件数和行顺序由 Spark 分区决定。它不是 GeoJSON FeatureCollection，来源不能保留 Geometry。")

    record JsonLines(
            @JsonPropertyDescription("生成 JSON 时是否省略值为 NULL 的字段。")
            boolean ignoreNullFields
    ) implements FileOutputFormatOptions {
    }

    @JsonClassDescription("普通 Parquet 目录数据集格式；固定使用 Snappy 压缩并允许多个 part 文件，不写 GeoParquet 空间元数据，因此来源不能保留 Geometry。空间数据应选择 GEOPARQUET。")

    record Parquet() implements FileOutputFormatOptions {
    }

    @JsonClassDescription("Shapefile 单套制品格式；Driver 逐行写出，不按 Spark 分区拆包。来源必须 BOUNDED，所选 Geometry 必须为可解析的 EPSG + XY；不会自动投影 CRS、降维、修复拓扑或拆 GeometryCollection。NULL Geometry 写 Null Shape，Empty 或运行时类型不符会失败。")

    record Shapefile(
            @JsonPropertyDescription("Shapefile 文件基础名；1 至 64 个中文、字母、数字、下划线或连字符，首字符必须是中文、字母或数字。")
            String baseName,
            @JsonPropertyDescription("必填的 Shapefile 输出打包形态：ZIP 单个压缩包，COMPONENT_DIRECTORY 独立组件目录。")
            ShapefilePackageMode packageMode,
            @JsonPropertyDescription("要写入 Shapefile 的必填 Geometry 来源字段名。")
            String geometryColumnName,
            @JsonPropertyDescription("必填目标 Shape 类型。POINT 只接收 Point；MULTIPOINT 接收 Point/MultiPoint；POLYLINE 接收 LineString/MultiLineString；POLYGON 接收 Polygon/MultiPolygon。通用 GEOMETRY 在运行时逐行检查。")
            ShapefileShapeType targetShapeType,
            @JsonPropertyDescription("按数组顺序写入 DBF 的属性映射，必须包含 1 至 255 项。来源字段不能重复且不能是 Geometry；目标名按大小写不敏感唯一。未列出的非 Geometry 来源字段不会写入 DBF。")
            java.util.List<ShapefileAttributeMapping> attributeMappings
    ) implements FileOutputFormatOptions {
        public Shapefile {
            attributeMappings = attributeMappings == null
                    ? null : java.util.List.copyOf(attributeMappings);
        }
    }

    @JsonClassDescription("GeoParquet 1.1.0 目录数据集格式；恰好保留一个 EPSG + XY Geometry 字段，以 WKB 写入并为每个 part 保存显式 PROJJSON 空间元数据。允许多个 part，不自动变换 CRS、降维、修复拓扑或选择 Geometry。NULL Geometry 可写，Empty 或非有限坐标运行时失败。")

    record GeoParquet(
            @JsonPropertyDescription("来源表中唯一 Geometry 字段的名称；表中恰好只能有一个 Geometry 且必须选择它。其他字段按原名、顺序和 Spark/Parquet 类型写出。")
            String geometryColumnName,
            @JsonPropertyDescription("必填的 GeoParquet 列压缩编码：SNAPPY 或 ZSTD。")
            GeoParquetCompressionCodec compression,
            @JsonPropertyDescription("必填 covering 模式。NONE 不增加逐行边界列但仍保留 part Footer 整体 bbox；ROW_BBOX 在物理 Schema 末尾增加 {geometryColumnName}_bbox struct 并写 covering 元数据，来源已有同名字段时失败。")
            GeoParquetCoveringMode coveringMode
    ) implements FileOutputFormatOptions {
    }

    @JsonClassDescription("单文件 RFC 7946 GeoJSON FeatureCollection；Driver 逐行生成 {baseName}.geojson，结果达到 1.8GB 时失败。来源必须 BOUNDED 且恰好一个 EPSG:4326 + XY Geometry；不自动转换 CRS、降维或修复拓扑，BINARY 属性不支持。")

    record GeoJson(
            @JsonPropertyDescription("GeoJSON 文件基础名；1 至 64 个中文、字母、数字、下划线或连字符，首字符必须是中文、字母或数字。")
            String baseName,
            @JsonPropertyDescription("来源表中唯一 Geometry 字段的名称；每行写入 Feature.geometry，不再出现在 properties。NULL 写 JSON null，Empty、越界经纬度、非有限坐标或损坏 Geometry 会失败。")
            String geometryColumnName,
            @JsonPropertyDescription("可选 Feature 顶层 id 来源字段；null 表示不输出，空字符串无效。只支持 STRING 或整数类型；字段仍同时保留在 properties，值为 NULL 时仅省略该 Feature 的 id。LONG 可能超出 JavaScript 安全整数范围。")
            String idColumnName,
            @JsonPropertyDescription("是否从每个 Feature.properties 省略值为 NULL 的非 Geometry 字段；false 时以 JSON null 保留。")
            boolean ignoreNullProperties
    ) implements FileOutputFormatOptions {
    }
}
