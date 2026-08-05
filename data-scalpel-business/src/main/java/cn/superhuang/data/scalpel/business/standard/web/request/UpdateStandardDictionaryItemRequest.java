package cn.superhuang.data.scalpel.business.standard.web.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStandardDictionaryItemRequest(
        @Min(1) int expectedVersion,
        @NotBlank @Size(max = 256) String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {
}
