package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;

import java.util.List;
import java.util.UUID;

public record DirectoryExecutionResult(
        DirectoryScope scope,
        List<CreatedDirectory> created,
        List<UpdatedDirectory> updated,
        List<DeletedDirectory> deleted
) {
    public DirectoryExecutionResult {
        created = List.copyOf(created);
        updated = List.copyOf(updated);
        deleted = List.copyOf(deleted);
    }

    public record CreatedDirectory(String ref, UUID id, String name) {
    }

    public record UpdatedDirectory(UUID id, String name) {
    }

    public record DeletedDirectory(UUID id, String name) {
    }
}
