package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.UUID;

public record MetadataModel(
        UUID id,
        String code,
        String name,
        int schemaVersion,
        MetadataModelStatus status,
        MetadataModelPhysicalTableMode physicalTableMode,
        UUID dataSourceId,
        String catalogName,
        String schemaName,
        String physicalTableName,
        List<CanvasColumnSchema> columns,
        List<MetadataUniqueKey> uniqueKeys,
        List<MetadataModelField> fields
) {
    public MetadataModel {
        columns = columns == null ? List.of() : List.copyOf(columns);
        uniqueKeys = uniqueKeys == null ? List.of() : List.copyOf(uniqueKeys);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public MetadataModel(
            UUID id,
            String code,
            String name,
            int schemaVersion,
            MetadataModelStatus status,
            MetadataModelPhysicalTableMode physicalTableMode,
            UUID dataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            List<CanvasColumnSchema> columns
    ) {
        this(id, code, name, schemaVersion, status, physicalTableMode, dataSourceId,
                catalogName, schemaName, physicalTableName, columns, List.of(), List.of());
    }

    public MetadataModel(
            UUID id,
            String code,
            String name,
            int schemaVersion,
            MetadataModelStatus status,
            MetadataModelPhysicalTableMode physicalTableMode,
            UUID dataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            List<CanvasColumnSchema> columns,
            List<MetadataUniqueKey> uniqueKeys
    ) {
        this(id, code, name, schemaVersion, status, physicalTableMode, dataSourceId,
                catalogName, schemaName, physicalTableName, columns, uniqueKeys, List.of());
    }
}
