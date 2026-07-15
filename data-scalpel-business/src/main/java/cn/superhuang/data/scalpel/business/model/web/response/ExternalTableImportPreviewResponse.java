package cn.superhuang.data.scalpel.business.model.web.response;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.util.List;

public record ExternalTableImportPreviewResponse(
        TableIdentifier table,
        boolean importable,
        List<ExternalTableImportColumnResponse> columns,
        List<String> issues
) {

    public ExternalTableImportPreviewResponse {
        columns = List.copyOf(columns);
        issues = List.copyOf(issues);
    }
}
