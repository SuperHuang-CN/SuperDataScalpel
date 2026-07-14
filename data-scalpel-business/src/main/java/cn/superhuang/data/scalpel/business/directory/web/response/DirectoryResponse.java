package cn.superhuang.data.scalpel.business.directory.web.response;

import cn.superhuang.data.scalpel.business.directory.domain.Directory;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;

import java.time.Instant;
import java.util.UUID;

public record DirectoryResponse(
        UUID id,
        DirectoryScope scope,
        UUID parentId,
        String name,
        int sortOrder,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
    public static DirectoryResponse from(Directory directory) {
        return new DirectoryResponse(
                directory.getId(),
                directory.getScope(),
                directory.getParentId(),
                directory.getName(),
                directory.getSortOrder(),
                directory.getDescription(),
                directory.getCreatedAt(),
                directory.getUpdatedAt()
        );
    }
}
