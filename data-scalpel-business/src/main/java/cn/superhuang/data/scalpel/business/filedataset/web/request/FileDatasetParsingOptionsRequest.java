package cn.superhuang.data.scalpel.business.filedataset.web.request;

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
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Spreadsheet.class, name = "SPREADSHEET"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Parquet.class, name = "PARQUET"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Avro.class, name = "AVRO"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.Shapefile.class, name = "SHAPEFILE"),
        @JsonSubTypes.Type(value = FileDatasetParsingOptionsRequest.FileGdb.class, name = "FILE_GDB")
})
public sealed interface FileDatasetParsingOptionsRequest permits
        FileDatasetParsingOptionsRequest.Csv,
        FileDatasetParsingOptionsRequest.Text,
        FileDatasetParsingOptionsRequest.Json,
        FileDatasetParsingOptionsRequest.JsonLines,
        FileDatasetParsingOptionsRequest.Spreadsheet,
        FileDatasetParsingOptionsRequest.Parquet,
        FileDatasetParsingOptionsRequest.Avro,
        FileDatasetParsingOptionsRequest.Shapefile,
        FileDatasetParsingOptionsRequest.FileGdb {

    FileDatasetParsingOptionsKind kind();

    record Csv(
            @NotBlank @Size(max = 40) String charset,
            @NotEmpty @Size(max = 8) String fieldDelimiter,
            @NotNull FileRecordDelimiter recordDelimiter,
            @Size(max = 1) String quoteCharacter,
            @Size(max = 1) String escapeCharacter,
            @NotNull Boolean firstRowHeader
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.CSV;
        }
    }

    record Text(
            @NotBlank @Size(max = 40) String charset,
            @NotNull FileRecordDelimiter recordDelimiter
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.TEXT;
        }
    }

    record Json(
            @NotBlank @Size(max = 40) String charset,
            @Size(max = 500) String rootPointer
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.JSON;
        }
    }

    record JsonLines(
            @NotBlank @Size(max = 40) String charset,
            @NotNull FileRecordDelimiter recordDelimiter
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.JSON_LINES;
        }
    }

    record Spreadsheet(
            @Size(max = 128) String sheetName,
            @NotNull @Min(0) @Max(10000) Integer headerRowIndex,
            @NotNull @Min(0) @Max(10001) Integer dataStartRowIndex
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.SPREADSHEET;
        }
    }

    record Parquet() implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.PARQUET;
        }
    }

    record Avro() implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.AVRO;
        }
    }

    record Shapefile(
            @NotBlank @Size(max = 40) String charset,
            @Size(max = 255) String layerName
    ) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.SHAPEFILE;
        }
    }

    record FileGdb(@Size(max = 255) String layerName) implements FileDatasetParsingOptionsRequest {
        @Override
        public FileDatasetParsingOptionsKind kind() {
            return FileDatasetParsingOptionsKind.FILE_GDB;
        }
    }
}
