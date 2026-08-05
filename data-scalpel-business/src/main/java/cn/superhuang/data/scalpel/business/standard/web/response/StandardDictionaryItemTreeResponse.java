package cn.superhuang.data.scalpel.business.standard.web.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StandardDictionaryItemTreeResponse(
        UUID id,
        UUID parentId,
        String code,
        String name,
        int sortOrder,
        boolean enabled,
        boolean effectiveEnabled,
        String description,
        List<StandardDictionaryItemTreeResponse> children,
        Instant createdAt,
        Instant updatedAt
) {
    public StandardDictionaryItemTreeResponse {
        children = List.copyOf(children);
    }
}
