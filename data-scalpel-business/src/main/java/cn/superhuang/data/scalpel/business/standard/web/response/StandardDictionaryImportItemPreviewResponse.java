package cn.superhuang.data.scalpel.business.standard.web.response;

import java.util.List;

public record StandardDictionaryImportItemPreviewResponse(
        int rowNumber,
        String code,
        String name,
        String parentCode,
        int sortOrder,
        boolean enabled,
        String description,
        String action,
        List<String> issues
) {
    public StandardDictionaryImportItemPreviewResponse {
        issues = List.copyOf(issues);
    }
}
