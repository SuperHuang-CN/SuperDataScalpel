package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.util.List;

public record ManagedImportPreviewResponse(
        TableIdentifier sourceTable,
        String suggestedCode,
        String suggestedName,
        String suggestedPhysicalTableName,
        boolean tableImportable,
        boolean importable,
        List<ManagedImportColumnResponse> columns,
        List<String> tableIssues,
        List<String> issues,
        List<String> warnings
) {

    public ManagedImportPreviewResponse {
        columns = List.copyOf(columns);
        tableIssues = List.copyOf(tableIssues);
        issues = List.copyOf(issues);
        warnings = List.copyOf(warnings);
    }
}
