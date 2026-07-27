package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record MetadataTable(
        String tableName,
        DatabaseObjectType objectType,
        List<CanvasColumnSchema> columns
) {
}
