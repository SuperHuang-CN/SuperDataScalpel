package cn.superhuang.data.scalpel.business.directory.web.request;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDirectoryRequest(
        @NotNull DirectoryScope scope,
        UUID parentId,
        @NotBlank @Size(max = 100) String name,
        int sortOrder,
        @Size(max = 500) String description
) {
}
