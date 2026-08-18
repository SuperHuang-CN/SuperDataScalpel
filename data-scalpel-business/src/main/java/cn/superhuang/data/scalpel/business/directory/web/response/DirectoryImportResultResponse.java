package cn.superhuang.data.scalpel.business.directory.web.response;

public record DirectoryImportResultResponse(
        int totalCount,
        int createdCount,
        int updatedCount,
        int unchangedCount
) {
}
