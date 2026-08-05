package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ScriptRequestExampleRequest(
        @NotBlank @Size(max = 64) String id,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 200_000) String bodyText,
        @NotNull @Size(max = 100) List<@Valid ScriptRequestParameterRequest> query,
        @NotNull @Size(max = 100) List<@Valid ScriptRequestParameterRequest> headers
) {
}
