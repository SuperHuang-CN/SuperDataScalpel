package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record UpdateLocalSqlTaskDefinitionRequest(
        @NotBlank @Size(max = 100_000) String sql,
        @NotEmpty @Size(max = 50) List<@NotNull UUID> inputModelIds,
        @NotNull UUID outputModelId,
        @NotNull LocalSqlWriteMode writeMode,
        @NotNull @Min(1) @Max(3600) Integer timeoutSeconds
) {
}
