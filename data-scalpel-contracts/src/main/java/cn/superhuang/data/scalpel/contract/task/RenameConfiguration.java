package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record RenameConfiguration(
        String sourceTableName,
        String outputTableName,
        List<RenameColumnMapping> columnMappings
) {
}
