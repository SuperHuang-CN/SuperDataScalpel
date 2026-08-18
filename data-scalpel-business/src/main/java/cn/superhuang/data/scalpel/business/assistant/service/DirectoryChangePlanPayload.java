package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DirectoryChangePlanPayload(
        DirectoryScope scope,
        String summary,
        List<CreateOperation> creates,
        List<UpdateOperation> updates,
        List<DeleteOperation> deletes
) {
    public DirectoryChangePlanPayload {
        creates = List.copyOf(creates);
        updates = List.copyOf(updates);
        deletes = List.copyOf(deletes);
    }

    public int operationCount() {
        return creates.size() + updates.size() + deletes.size();
    }

    public record CreateOperation(
            String ref,
            UUID parentId,
            String parentRef,
            String name,
            int sortOrder,
            String description,
            String targetPath
    ) {
    }

    public record UpdateOperation(
            UUID id,
            UUID parentId,
            String parentRef,
            String name,
            int sortOrder,
            String description,
            Instant expectedUpdatedAt,
            CurrentState current,
            String targetPath
    ) {
    }

    public record DeleteOperation(
            UUID id,
            Instant expectedUpdatedAt,
            String name,
            String path,
            long directResourceCount,
            long resourceCount,
            int depth
    ) {
    }

    public record CurrentState(
            UUID parentId,
            String name,
            int sortOrder,
            String description,
            String path,
            long directResourceCount,
            long resourceCount
    ) {
    }
}
