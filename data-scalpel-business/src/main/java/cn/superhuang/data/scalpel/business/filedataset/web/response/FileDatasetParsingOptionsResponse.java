package cn.superhuang.data.scalpel.business.filedataset.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParsingOptionsKind;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileRecordDelimiter;
import cn.superhuang.data.scalpel.business.filedataset.web.request.FileDatasetParsingOptionsRequest;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Csv.class, name = "CSV"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Text.class, name = "TEXT"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Json.class, name = "JSON"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.JsonLines.class, name = "JSON_LINES"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.GeoJson.class, name = "GEOJSON"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.GeoJsonLines.class, name = "GEOJSONL"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.GeoParquet.class, name = "GEOPARQUET"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.GeoPackage.class, name = "GPKG"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Spreadsheet.class, name = "SPREADSHEET"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Parquet.class, name = "PARQUET"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Avro.class, name = "AVRO"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Gdb.class, name = "GDB"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Shp.class, name = "SHP")
})
@Schema(description = "文件数据集表当前生效的格式专用解析选项；kind 决定返回的具体结构。")
public sealed interface FileDatasetParsingOptionsResponse permits
        FileDatasetParsingOptionsResponse.Csv,
        FileDatasetParsingOptionsResponse.Text,
        FileDatasetParsingOptionsResponse.Json,
        FileDatasetParsingOptionsResponse.JsonLines,
        FileDatasetParsingOptionsResponse.GeoJson,
        FileDatasetParsingOptionsResponse.GeoJsonLines,
        FileDatasetParsingOptionsResponse.GeoParquet,
        FileDatasetParsingOptionsResponse.GeoPackage,
        FileDatasetParsingOptionsResponse.Spreadsheet,
        FileDatasetParsingOptionsResponse.Parquet,
        FileDatasetParsingOptionsResponse.Avro,
        FileDatasetParsingOptionsResponse.Gdb,
        FileDatasetParsingOptionsResponse.Shp {

    FileDatasetParsingOptionsKind kind();

    static FileDatasetParsingOptionsResponse from(FileDatasetParsingOptionsRequest request) {
        return switch (request) {
            case FileDatasetParsingOptionsRequest.Csv value -> new Csv(
                    value.charset(), value.fieldDelimiter(), value.recordDelimiter(), value.quoteCharacter(),
                    value.escapeCharacter(), value.firstRowHeader()
            );
            case FileDatasetParsingOptionsRequest.Text value -> new Text(value.charset(), value.recordDelimiter());
            case FileDatasetParsingOptionsRequest.Json value -> new Json(value.charset(), value.rootPointer());
            case FileDatasetParsingOptionsRequest.JsonLines value -> new JsonLines(value.charset(), value.recordDelimiter());
            case FileDatasetParsingOptionsRequest.GeoJson value -> new GeoJson(value.epsgCode());
            case FileDatasetParsingOptionsRequest.GeoJsonLines value -> new GeoJsonLines(value.epsgCode());
            case FileDatasetParsingOptionsRequest.GeoParquet ignored -> new GeoParquet();
            case FileDatasetParsingOptionsRequest.GeoPackage ignored -> new GeoPackage();
            case FileDatasetParsingOptionsRequest.Spreadsheet value -> new Spreadsheet(
                    value.headerRowIndex(), value.dataStartRowIndex()
            );
            case FileDatasetParsingOptionsRequest.Parquet ignored -> new Parquet();
            case FileDatasetParsingOptionsRequest.Avro ignored -> new Avro();
            case FileDatasetParsingOptionsRequest.Gdb value -> new Gdb(value.epsgCode());
            case FileDatasetParsingOptionsRequest.Shp value -> new Shp(
                    value.dbfCharsetOverride(), value.dbfFallbackCharset(), value.epsgCode()
            );
        };
    }

    @Schema(description = "当前生效的 CSV 解析选项，包括字符集、字段与记录分隔、引号、转义及表头设置。")

    record Csv(
            @Schema(description = "解析文件内容使用的字符集名称，例如 UTF-8 或 GB18030。")
            String charset,
            @Schema(description = "字段分隔符。")
            String fieldDelimiter,
            @Schema(description = "记录分隔符：AUTO 自动识别 LF、CRLF 或 CR，LF 为 \\n，CRLF 为 \\r\\n，CR 为 \\r。")
            FileRecordDelimiter recordDelimiter,
            @Schema(description = "CSV 引号字符；为空表示不启用引号处理。")
            String quoteCharacter,
            @Schema(description = "CSV 转义字符；为空表示不启用额外转义。")
            String escapeCharacter,
            @Schema(description = "是否首行表头。")
            boolean firstRowHeader
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.CSV;
        }
    }

    @Schema(description = "当前生效的纯文本解析选项，包括字符集和记录分隔方式。")

    record Text(
            @Schema(description = "解析文本内容使用的字符集名称，例如 UTF-8 或 GB18030。")
            String charset,
            @Schema(description = "记录分隔符：AUTO 自动识别 LF、CRLF 或 CR，LF 为 \\n，CRLF 为 \\r\\n，CR 为 \\r。")
            FileRecordDelimiter recordDelimiter
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.TEXT;
        }
    }

    @Schema(description = "当前生效的 JSON 文档解析选项，包括字符集和记录集合所在的 JSON Pointer。")

    record Json(
            @Schema(description = "解析 JSON 文本使用的字符集名称，例如 UTF-8。")
            String charset,
            @Schema(description = "以 / 开头的 JSON Pointer，目标对象按一条记录处理，目标数组的元素逐条处理；为空或纯空白表示根节点。")
            String rootPointer
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.JSON;
        }
    }

    @Schema(description = "当前生效的逐行 JSON 解析选项，包括字符集和记录分隔方式。")

    record JsonLines(
            @Schema(description = "解析逐行 JSON 文本使用的字符集名称，例如 UTF-8。")
            String charset,
            @Schema(description = "记录分隔符：AUTO 自动识别 LF、CRLF 或 CR，LF 为 \\n，CRLF 为 \\r\\n，CR 为 \\r。")
            FileRecordDelimiter recordDelimiter
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.JSON_LINES;
        }
    }

    @Schema(description = "当前生效的 GeoJSON 解析选项，记录输入坐标采用的 EPSG 坐标参考系。")

    record GeoJson(
            @Schema(description = "坐标参考系 EPSG 数字编码。")
            int epsgCode
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GEOJSON;
        }
    }

    @Schema(description = "当前生效的逐行 GeoJSON 解析选项，记录输入坐标采用的 EPSG 坐标参考系。")

    record GeoJsonLines(
            @Schema(description = "坐标参考系 EPSG 数字编码。")
            int epsgCode
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GEOJSONL;
        }
    }

    @Schema(description = "当前生效的 GeoParquet 解析选项；结构和空间元数据从文件读取，无额外参数。")

    record GeoParquet() implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GEOPARQUET;
        }
    }

    @Schema(description = "当前生效的 GeoPackage 文件级解析选项；具体表由数据集表记录区分。")

    record GeoPackage() implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GPKG;
        }
    }

    @Schema(description = "当前生效的电子表格解析选项，包括零基表头行和数据起始行。")

    record Spreadsheet(
            @Schema(description = "电子表格表头行的零基索引。")
            int headerRowIndex,
            @Schema(description = "电子表格数据起始行的零基索引，必须在表头行之后。")
            int dataStartRowIndex
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.SPREADSHEET;
        }
    }

    @Schema(description = "当前生效的 Parquet 解析选项；列结构从文件 Schema 读取，无额外参数。")

    record Parquet() implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.PARQUET;
        }
    }

    @Schema(description = "当前生效的 Avro 对象容器文件解析选项；记录结构从内嵌 Schema 读取。")

    record Avro() implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.AVRO;
        }
    }

    @Schema(description = "当前生效的 File Geodatabase 解析选项；可在图层 WKT 无法识别 EPSG 时提供回退坐标系。")

    record Gdb(
            @Schema(description = "可选 EPSG 回退编码，仅在 GDB 图层 WKT 中没有可识别 EPSG 标识时使用；文件值可识别时优先使用文件值。")
            Integer epsgCode
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GDB;
        }
    }

    @Schema(description = "当前生效的 Shapefile 解析选项，包括 DBF 字符集策略和文件空间参考无法识别时使用的 EPSG 回退编码。")

    record Shp(
            @Schema(description = "强制用于 DBF 属性表的字符集；为空时自动检测。")
            String dbfCharsetOverride,
            @Schema(description = "无法从 DBF 元数据识别编码时使用的后备字符集。")
            String dbfFallbackCharset,
            @Schema(description = "可选 EPSG 回退编码，仅在 .prj 等 Shapefile 空间参考元数据没有可识别 EPSG 标识时使用；文件值可识别时优先使用文件值。")
            Integer epsgCode
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.SHP;
        }
    }

}
