package cn.superhuang.data.scalpel.business.directory.web.response;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;

import java.util.List;
import java.util.UUID;

/** A tree node with direct and descendant resource counts for the current scope. */
public record DirectoryTreeNodeResponse(
        UUID id,
        DirectoryScope scope,
        UUID parentId,
        String name,
        int sortOrder,
        String description,
        long directResourceCount,
        long resourceCount,
        List<DirectoryTreeNodeResponse> children
) {
}
