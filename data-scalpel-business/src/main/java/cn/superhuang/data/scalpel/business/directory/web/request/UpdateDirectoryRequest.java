package cn.superhuang.data.scalpel.business.directory.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateDirectoryRequest(
        UUID parentId,
        @NotBlank @Size(max = 100) String name,
        int sortOrder,
        @Size(max = 500) String description
) {
}
