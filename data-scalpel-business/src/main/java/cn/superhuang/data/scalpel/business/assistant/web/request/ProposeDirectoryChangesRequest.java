package cn.superhuang.data.scalpel.business.assistant.web.request;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;

import java.util.List;
import java.util.UUID;

public record ProposeDirectoryChangesRequest(
        DirectoryScope scope,
        String summary,
        List<Operation> operations
) {
    public record Operation(
            DirectoryChangeOperationType type,
            String ref,
            UUID id,
            UUID parentId,
            String parentRef,
            String name,
            Integer sortOrder,
            String description
    ) {
    }
}
