package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Set;

@JsonClassDescription("供 Canvas 编译使用的 JDBC 表或视图元数据快照；包含物理位置、字段、候选唯一键和索引覆盖信息。")
public record MetadataTable(
        @JsonPropertyDescription("编译元数据快照中的表标识；通常为物理表名，在同一数据源命名空间内唯一。")
        String tableName,
        @JsonPropertyDescription("元数据对象类型，例如表或视图。")
        DatabaseObjectType objectType,
        @JsonPropertyDescription("数据库表或视图的列 Schema，按物理列顺序排列。")
        List<CanvasColumnSchema> columns,
        @JsonPropertyDescription("可用于唯一定位记录的候选键列表。")
        List<MetadataUniqueKey> uniqueKeys,
        @JsonPropertyDescription("至少被一个数据库索引覆盖的物理列名集合；不表达索引内顺序或唯一性。")
        Set<String> indexedColumns,
        @JsonPropertyDescription("数据库 Catalog；不适用时为空。")
        String catalogName,
        @JsonPropertyDescription("数据库 Schema；不适用时为空。")
        String schemaName,
        @JsonPropertyDescription("数据库物理表名。")
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
