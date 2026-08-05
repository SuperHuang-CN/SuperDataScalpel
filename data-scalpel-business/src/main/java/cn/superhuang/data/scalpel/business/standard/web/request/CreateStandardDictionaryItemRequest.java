package cn.superhuang.data.scalpel.business.standard.web.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateStandardDictionaryItemRequest(
        @Min(1) int expectedVersion,
        UUID parentId,
        @Min(0) Integer targetIndex,
        @NotBlank @Size(max = 256) String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull Boolean enabled,
        @Size(max = 500) String description
) {
}
