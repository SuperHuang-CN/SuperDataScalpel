package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Tests a draft configuration when its runtime client is currently implemented. */
public record TestDataSourceConnectionRequest(
        @NotNull DataSourceType type,
        @NotNull @Valid DataSourceConnectionRequest connection
) {
}
