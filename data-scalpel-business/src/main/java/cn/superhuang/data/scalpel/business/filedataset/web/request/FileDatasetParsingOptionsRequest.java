package cn.superhuang.data.scalpel.business.filedataset.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParsingOptionsKind;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileRecordDelimiter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Csv.class, name = "CSV"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Text.class, name = "TEXT"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Json.class, name = "JSON"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.JsonLines.class, name = "JSON_LINES"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.GeoJson.class, name = "GEOJSON"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.GeoJsonLines.class, name = "GEOJSONL"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.GeoParquet.class, name = "GEOPARQUET"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.GeoPackage.class, name = "GPKG"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Spreadsheet.class, name = "SPREADSHEET"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Parquet.class, name = "PARQUET"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Avro.class, name = "AVRO"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Gdb.class, name = "GDB"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Shp.class, name = "SHP")
})
@Schema(description = "创建或更新文件数据集表时提交的格式专用解析选项；kind 必须与文件数据集类型对应。")
public sealed interface FileDatasetParsingOptionsRequest permits
        FileDatasetParsingOptionsRequest.Csv,
        FileDatasetParsingOptionsRequest.Text,
        FileDatasetParsingOptionsRequest.Json,
        FileDatasetParsingOptionsRequest.JsonLines,
        FileDatasetParsingOptionsRequest.GeoJson,
        FileDatasetParsingOptionsRequest.GeoJsonLines,
        FileDatasetParsingOptionsRequest.GeoParquet,
        FileDatasetParsingOptionsRequest.GeoPackage,
        FileDatasetParsingOptionsRequest.Spreadsheet,
        FileDatasetParsingOptionsRequest.Parquet,
        FileDatasetParsingOptionsRequest.Avro,
        FileDatasetParsingOptionsRequest.Gdb,
        FileDatasetParsingOptionsRequest.Shp {

    FileDatasetParsingOptionsKind kind();

    @Schema(description = "CSV 解析请求选项；声明字符集、字段与记录分隔、引号、转义及首行表头语义。")

    record Csv(
            @Schema(description = "解析 CSV 字节所用的 Java 字符集名称，例如 UTF-8 或 GB18030。")
            @NotBlank @Size(max = 40) String charset,
            @Schema(description = "字段分隔符。")
            @NotEmpty @Size(max = 8) String fieldDelimiter,
            @Schema(description = "记录分隔符：AUTO 自动识别 LF、CRLF 或 CR，LF 为 \\n，CRLF 为 \\r\\n，CR 为 \\r。")
            @NotNull FileRecordDelimiter recordDelimiter,
            @Schema(description = "CSV 引号字符；为空表示不启用引号处理。")
            @Size(max = 1) String quoteCharacter,
            @Schema(description = "CSV 转义字符；为空表示不启用额外转义。")
            @Size(max = 1) String escapeCharacter,
            @Schema(description = "首行是否作为字段名；false 时由系统生成字段名。")
            @NotNull Boolean firstRowHeader
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.CSV;
        }
    }

    @Schema(description = "纯文本解析请求选项；每条记录按声明字符集和记录分隔符读取为一行文本。")

    record Text(
            @Schema(description = "解析纯文本字节所用的 Java 字符集名称，例如 UTF-8 或 GB18030。")
            @NotBlank @Size(max = 40) String charset,
            @Schema(description = "记录分隔符：AUTO 自动识别 LF、CRLF 或 CR，LF 为 \\n，CRLF 为 \\r\\n，CR 为 \\r。")
            @NotNull FileRecordDelimiter recordDelimiter
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.TEXT;
        }
    }

    @Schema(description = "JSON 文档解析请求选项；可用 JSON Pointer 选择作为单条记录的对象或作为多条记录的数组节点。")

    record Json(
            @Schema(description = "解析 JSON 字节所用的 Java 字符集名称，通常为 UTF-8。")
            @NotBlank @Size(max = 40) String charset,
            @Schema(description = "以 / 开头的 JSON Pointer，目标必须是对象或数组；对象按一条记录处理，数组元素逐条处理。为空或纯空白时选择根节点。")
            @Size(max = 500) String rootPointer
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.JSON;
        }
    }

    @Schema(description = "逐行 JSON 解析请求选项；每条由记录分隔符划分的内容必须是一个 JSON 值。")

    record JsonLines(
            @Schema(description = "解析逐行 JSON 字节所用的 Java 字符集名称，通常为 UTF-8。")
            @NotBlank @Size(max = 40) String charset,
            @Schema(description = "记录分隔符：AUTO 自动识别 LF、CRLF 或 CR，LF 为 \\n，CRLF 为 \\r\\n，CR 为 \\r。")
            @NotNull FileRecordDelimiter recordDelimiter
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.JSON_LINES;
        }
    }

    @Schema(description = "GeoJSON 解析请求选项；声明文件中坐标采用的 EPSG 坐标参考系。")

    record GeoJson(
            @Schema(description = "坐标参考系 EPSG 数字编码。")
            @NotNull @Min(1) Integer epsgCode
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GEOJSON;
        }
    }

    @Schema(description = "逐行 GeoJSON 解析请求选项；声明每行 Geometry 或 Feature 坐标采用的 EPSG 坐标参考系。")

    record GeoJsonLines(
            @Schema(description = "坐标参考系 EPSG 数字编码。")
            @NotNull @Min(1) Integer epsgCode
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GEOJSONL;
        }
    }

    @Schema(description = "GeoParquet 解析请求选项；结构和空间元数据直接从文件读取，无额外参数。")

    record GeoParquet() implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GEOPARQUET;
        }
    }

    @Schema(description = "GeoPackage 解析请求选项；具体表在后续表级选择中确定，无文件级附加参数。")

    record GeoPackage() implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GPKG;
        }
    }

    @Schema(description = "电子表格解析请求选项；声明零基表头行和紧随其后的数据起始行。")

    record Spreadsheet(
            @Schema(description = "电子表格表头行的零基索引。")
            @NotNull @Min(0) @Max(10000) Integer headerRowIndex,
            @Schema(description = "电子表格数据起始行的零基索引，必须在表头行之后。")
            @NotNull @Min(0) @Max(10001) Integer dataStartRowIndex
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.SPREADSHEET;
        }
    }

    @Schema(description = "Parquet 解析请求选项；列结构直接从文件 Schema 读取，无额外参数。")

    record Parquet() implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.PARQUET;
        }
    }

    @Schema(description = "Avro 对象容器文件解析请求选项；记录结构直接从内嵌 Schema 读取，无额外参数。")

    record Avro() implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.AVRO;
        }
    }

    @Schema(description = "File Geodatabase 解析请求选项；文件 WKT 无法识别 EPSG 时可提供回退编码，文件已声明可识别 EPSG 时优先使用文件值。")

    record Gdb(
            @Schema(description = "可选 EPSG 回退编码，仅在 GDB 图层 WKT 中没有可识别 EPSG 标识时使用。")
            @Min(1) Integer epsgCode
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GDB;
        }
    }

    @Schema(description = "Shapefile 解析请求选项；控制 DBF 字符集识别与后备编码，并可指定 EPSG 坐标系。")

    record Shp(
            @Schema(description = "强制用于 DBF 属性表的字符集；为空时自动检测。")
            @Size(max = 40) String dbfCharsetOverride,
            @Schema(description = "无法从 DBF 元数据识别编码时使用的后备字符集。")
            @NotBlank @Size(max = 40) String dbfFallbackCharset,
            @Schema(description = "可选 EPSG 回退编码，仅在 .prj 等 Shapefile 空间参考元数据没有可识别 EPSG 标识时使用。")
            @Min(1) Integer epsgCode
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.SHP;
        }
    }

}
