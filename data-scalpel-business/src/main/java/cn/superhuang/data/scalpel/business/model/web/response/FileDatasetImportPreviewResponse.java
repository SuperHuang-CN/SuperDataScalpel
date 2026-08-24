package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FileDatasetImportPreviewResponse(
        UUID fileDatasetId,
        String fileDatasetName,
        FileDatasetType fileDatasetType,
        UUID fileDatasetTableId,
        String sourceTableCode,
        String sourceTableName,
        FileDatasetParseStatus parseStatus,
        Instant sourceUpdatedAt,
        String suggestedCode,
        String suggestedName,
        String suggestedPhysicalTableName,
        boolean tableImportable,
        boolean importable,
        int unresolvedCount,
        List<FileDatasetImportColumnResponse> columns,
        List<String> tableIssues,
        List<String> issues,
        List<String> warnings
) {

    public FileDatasetImportPreviewResponse {
        columns = columns == null ? List.of() : List.copyOf(columns);
        tableIssues = tableIssues == null ? List.of() : List.copyOf(tableIssues);
        issues = issues == null ? List.of() : List.copyOf(issues);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
