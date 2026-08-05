package cn.superhuang.data.scalpel.business.standard.web.response;

import java.util.List;
import java.util.UUID;

public record StandardDictionaryImportResultResponse(
        int createdDictionaryCount,
        int updatedDictionaryCount,
        int createdItemCount,
        int updatedItemCount,
        List<UUID> dictionaryIds
) {
    public StandardDictionaryImportResultResponse {
        dictionaryIds = List.copyOf(dictionaryIds);
    }
}
