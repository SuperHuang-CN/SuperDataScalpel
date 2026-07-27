package cn.superhuang.data.scalpel.business.filedataset.web.response;

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
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Spreadsheet.class, name = "SPREADSHEET"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Parquet.class, name = "PARQUET"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Avro.class, name = "AVRO"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Gdb.class, name = "GDB"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsResponse.Shp.class, name = "SHP")
})
public sealed interface FileDatasetParsingOptionsResponse permits
        FileDatasetParsingOptionsResponse.Csv,
        FileDatasetParsingOptionsResponse.Text,
        FileDatasetParsingOptionsResponse.Json,
        FileDatasetParsingOptionsResponse.JsonLines,
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
            case FileDatasetParsingOptionsRequest.Spreadsheet value -> new Spreadsheet(
                    value.headerRowIndex(), value.dataStartRowIndex()
            );
            case FileDatasetParsingOptionsRequest.Parquet ignored -> new Parquet();
            case FileDatasetParsingOptionsRequest.Avro ignored -> new Avro();
            case FileDatasetParsingOptionsRequest.Gdb ignored -> new Gdb();
            case FileDatasetParsingOptionsRequest.Shp value -> new Shp(
                    value.dbfCharsetOverride(), value.dbfFallbackCharset()
            );
        };
    }

    record Csv(
            String charset,
            String fieldDelimiter,
            FileRecordDelimiter recordDelimiter,
            String quoteCharacter,
            String escapeCharacter,
            boolean firstRowHeader
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.CSV;
        }
    }

    record Text(String charset, FileRecordDelimiter recordDelimiter) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.TEXT;
        }
    }

    record Json(String charset, String rootPointer) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.JSON;
        }
    }

    record JsonLines(String charset, FileRecordDelimiter recordDelimiter) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.JSON_LINES;
        }
    }

    record Spreadsheet(int headerRowIndex, int dataStartRowIndex) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.SPREADSHEET;
        }
    }

    record Parquet() implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.PARQUET;
        }
    }

    record Avro() implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.AVRO;
        }
    }

    record Gdb() implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.GDB;
        }
    }

    record Shp(
            String dbfCharsetOverride,
            String dbfFallbackCharset
    ) implements FileDatasetParsingOptionsResponse {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.SHP;
        }
    }

}
