package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ScriptRequestParameterRequest(
        @NotBlank @Size(max = 64) String id,
        @NotBlank @Size(max = 200) String key,
        @NotNull @Size(max = 10_000) String value
) {
}
