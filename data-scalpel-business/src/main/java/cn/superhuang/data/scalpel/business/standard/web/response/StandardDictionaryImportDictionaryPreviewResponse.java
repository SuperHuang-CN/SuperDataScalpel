package cn.superhuang.data.scalpel.business.standard.web.response;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

import java.util.List;
import java.util.UUID;

public record StandardDictionaryImportDictionaryPreviewResponse(
        int rowNumber,
        UUID existingId,
        Integer expectedVersion,
        String code,
        String name,
        PlatformDataType valueType,
        boolean enabled,
        String description,
        String action,
        List<String> issues,
        List<StandardDictionaryImportItemPreviewResponse> items
) {
    public StandardDictionaryImportDictionaryPreviewResponse {
        issues = List.copyOf(issues);
        items = List.copyOf(items);
    }
}
