package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record MetadataTable(
        String tableName,
        DatabaseObjectType objectType,
        List<CanvasColumnSchema> columns,
        List<MetadataUniqueKey> uniqueKeys
) {
    public MetadataTable {
        columns = columns == null ? List.of() : List.copyOf(columns);
        uniqueKeys = uniqueKeys == null ? List.of() : List.copyOf(uniqueKeys);
    }

    public MetadataTable(String tableName, DatabaseObjectType objectType, List<CanvasColumnSchema> columns) {
        this(tableName, objectType, columns, List.of());
    }
}
