package cn.superhuang.datascalpel.taskengine.contract;



import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Csv.class, name = "CSV"),
        @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Text.class, name = "TEXT"),
        @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Json.class, name = "JSON"),
        @JsonSubTypes.Type(value = RuntimeFileParsingOptions.JsonLines.class, name = "JSON_LINES"),
        @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Spreadsheet.class, name = "SPREADSHEET"),
        @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Parquet.class, name = "PARQUET"),
        @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Avro.class, name = "AVRO"),
        @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Gdb.class, name = "GDB"),
        @JsonSubTypes.Type(value = RuntimeFileParsingOptions.Shp.class, name = "SHP")
})
public sealed interface RuntimeFileParsingOptions extends java.io.Serializable permits
        RuntimeFileParsingOptions.Csv,
        RuntimeFileParsingOptions.Text,
        RuntimeFileParsingOptions.Json,
        RuntimeFileParsingOptions.JsonLines,
        RuntimeFileParsingOptions.Spreadsheet,
        RuntimeFileParsingOptions.Parquet,
        RuntimeFileParsingOptions.Avro,
        RuntimeFileParsingOptions.Gdb,
        RuntimeFileParsingOptions.Shp {

    record Csv(
            String charset,
            String fieldDelimiter,
            FileRecordDelimiter recordDelimiter,
            String quoteCharacter,
            String escapeCharacter,
            boolean firstRowHeader
    ) implements RuntimeFileParsingOptions {
    }

    record Text(String charset, FileRecordDelimiter recordDelimiter) implements RuntimeFileParsingOptions {
    }

    record Json(String charset, String rootPointer) implements RuntimeFileParsingOptions {
    }

    record JsonLines(String charset, FileRecordDelimiter recordDelimiter) implements RuntimeFileParsingOptions {
    }

    record Spreadsheet(int headerRowIndex, int dataStartRowIndex) implements RuntimeFileParsingOptions {
    }

    record Parquet() implements RuntimeFileParsingOptions {
    }

    record Avro() implements RuntimeFileParsingOptions {
    }

    record Gdb() implements RuntimeFileParsingOptions {
    }

    record Shp(String dbfCharsetOverride, String dbfFallbackCharset) implements RuntimeFileParsingOptions {
    }
}
