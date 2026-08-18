package cn.superhuang.data.scalpel.business.directory.service;

public record DirectoryImportRow(
        String rowKey,
        String parentRowKey,
        String name,
        int sortOrder,
        String description
) {
}
