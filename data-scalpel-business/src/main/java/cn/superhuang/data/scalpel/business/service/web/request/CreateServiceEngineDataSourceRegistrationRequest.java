package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateServiceEngineDataSourceRegistrationRequest(
        @NotNull UUID engineId,
        @NotNull UUID dataSourceId
) {
}
