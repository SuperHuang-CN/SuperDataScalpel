package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DatabaseType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Request shape kept stable for the later real database adapter implementation. */
public record TestDataSourceConnectionRequest(
        @NotNull DatabaseType databaseType,
        @NotNull @Valid DataSourceConnectionRequest connection
) {
}
