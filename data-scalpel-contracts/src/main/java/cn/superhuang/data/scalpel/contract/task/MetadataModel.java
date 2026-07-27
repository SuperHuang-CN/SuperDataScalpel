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
        List<CanvasColumnSchema> columns
) {
}
