package cn.superhuang.data.scalpel.business.service.web.request;

import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SqlDataServiceDefinitionRequest(
        @NotNull UUID dataSourceId,
        @NotEmpty List<@NotNull UUID> modelIds,
        @NotBlank @Size(max = 100_000) String sqlText,
        @Size(max = 50) List<@Valid SqlServiceParameterDefinition> parameters
) {

    public SqlDataServiceDefinitionRequest {
        modelIds = modelIds == null ? List.of() : List.copyOf(modelIds);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
