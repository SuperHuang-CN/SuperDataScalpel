package cn.superhuang.data.scalpel.business.standard.web.response;

import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionaryItem;

import java.time.Instant;
import java.util.UUID;

public record StandardDictionaryItemResponse(
        UUID id,
        UUID dictionaryId,
        UUID parentId,
        String code,
        String name,
        int sortOrder,
        boolean enabled,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static StandardDictionaryItemResponse from(StandardDictionaryItem item) {
        return new StandardDictionaryItemResponse(
                item.getId(),
                item.getDictionaryId(),
                item.getParentId(),
                item.getCode(),
                item.getName(),
                item.getSortOrder(),
                item.isEnabled(),
                item.getDescription(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
