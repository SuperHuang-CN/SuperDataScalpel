package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;

import java.util.List;
import java.util.UUID;

public record SqlDataServiceDefinitionResponse(
        UUID dataSourceId,
        List<UUID> modelIds,
        String sqlText,
        List<SqlServiceParameterDefinition> parameters,
        int version
) {

    public SqlDataServiceDefinitionResponse {
        modelIds = List.copyOf(modelIds);
        parameters = List.copyOf(parameters);
    }
}
