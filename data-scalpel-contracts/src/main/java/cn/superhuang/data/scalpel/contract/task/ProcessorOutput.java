package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Controls whether a simple processor replaces its source logical table or appends a new one. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "mode")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProcessorOutput.ReplaceSource.class, name = "REPLACE_SOURCE"),
        @JsonSubTypes.Type(value = ProcessorOutput.CreateNewTable.class, name = "CREATE_NEW_TABLE")
})
public sealed interface ProcessorOutput permits ProcessorOutput.ReplaceSource, ProcessorOutput.CreateNewTable {

    String outputTableName();

    record ReplaceSource(String outputTableName) implements ProcessorOutput {
    }

    record CreateNewTable(String outputTableName) implements ProcessorOutput {
    }
}
