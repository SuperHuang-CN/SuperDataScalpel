package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record UpdateDataSourceRequest(
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @NotEmpty Set<@NotNull DataSourcePurpose> purposes,
        @NotNull DataSourceType type,
        @NotNull Boolean enabled,
        @Size(max = 1000) String description,
        @NotNull @Valid DataSourceConnectionRequest connection
) {
}
