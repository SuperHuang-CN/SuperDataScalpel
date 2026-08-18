package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.Set;

public record MetadataTable(
        String tableName,
        DatabaseObjectType objectType,
        List<CanvasColumnSchema> columns,
        List<MetadataUniqueKey> uniqueKeys,
        Set<String> indexedColumns,
        String catalogName,
        String schemaName,
        String physicalTableName
) {
    public MetadataTable {
        columns = columns == null ? List.of() : List.copyOf(columns);
        uniqueKeys = uniqueKeys == null ? List.of() : List.copyOf(uniqueKeys);
        indexedColumns = indexedColumns == null ? Set.of() : Set.copyOf(indexedColumns);
    }

    public MetadataTable(
            String tableName,
            DatabaseObjectType objectType,
            List<CanvasColumnSchema> columns,
            List<MetadataUniqueKey> uniqueKeys
    ) {
        this(tableName, objectType, columns, uniqueKeys, Set.of(), null, null, tableName);
    }

    public MetadataTable(String tableName, DatabaseObjectType objectType, List<CanvasColumnSchema> columns) {
        this(tableName, objectType, columns, List.of(), Set.of(), null, null, tableName);
    }

    public MetadataTable(
            String tableName,
            DatabaseObjectType objectType,
            List<CanvasColumnSchema> columns,
            List<MetadataUniqueKey> uniqueKeys,
            Set<String> indexedColumns
    ) {
        this(tableName, objectType, columns, uniqueKeys, indexedColumns, null, null, tableName);
    }
}
