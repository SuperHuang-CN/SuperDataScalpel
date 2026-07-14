package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DatabaseType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record CreateDataSourceRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}", message = "编码只能包含字母、数字和下划线，且必须以字母开头")
        String code,
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @NotEmpty Set<@NotNull DataSourcePurpose> purposes,
        @NotNull DatabaseType databaseType,
        Boolean enabled,
        @Size(max = 1000) String description,
        @NotNull @Valid DataSourceConnectionRequest connection
) {
}
