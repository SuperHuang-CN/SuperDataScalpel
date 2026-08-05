package cn.superhuang.data.scalpel.business.standard.web.response;

import java.util.List;

public record StandardDictionaryImportPreviewResponse(
        String fileName,
        int formatVersion,
        boolean importable,
        String previewDigest,
        List<String> issues,
        List<StandardDictionaryImportDictionaryPreviewResponse> dictionaries
) {
    public StandardDictionaryImportPreviewResponse {
        issues = List.copyOf(issues);
        dictionaries = List.copyOf(dictionaries);
    }
}
