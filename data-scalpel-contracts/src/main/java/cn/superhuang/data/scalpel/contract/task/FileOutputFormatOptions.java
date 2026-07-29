package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = FileOutputFormatOptions.Csv.class, name = "CSV"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.JsonLines.class, name = "JSON_LINES"),
        @JsonSubTypes.Type(value = FileOutputFormatOptions.Parquet.class, name = "PARQUET")
})
public sealed interface FileOutputFormatOptions permits
        FileOutputFormatOptions.Csv,
        FileOutputFormatOptions.JsonLines,
        FileOutputFormatOptions.Parquet {

    record Csv(
            boolean header,
            String delimiter,
            String quote,
            String escape,
            String nullValue
    ) implements FileOutputFormatOptions {
    }

    record JsonLines(boolean ignoreNullFields) implements FileOutputFormatOptions {
    }

    record Parquet() implements FileOutputFormatOptions {
    }
}
