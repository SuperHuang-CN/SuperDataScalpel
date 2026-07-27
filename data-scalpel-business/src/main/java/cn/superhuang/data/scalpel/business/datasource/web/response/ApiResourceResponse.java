package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.ApiResource;
import cn.superhuang.data.scalpel.business.datasource.service.HttpApiConfigurationCodec;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;

import java.time.Instant;
import java.util.UUID;

public record ApiResourceResponse(
        UUID id,
        UUID dataSourceId,
        String code,
        String name,
        String connectorType,
        boolean enabled,
        HttpApiContracts.RequestTemplate request,
        HttpApiContracts.SigningConfiguration signing,
        HttpApiContracts.InvocationType invocationType,
        HttpApiContracts.PaginationConfiguration pagination,
        HttpApiContracts.AsyncJobConfiguration asyncJob,
        String recordsPointer,
        java.util.List<HttpApiContracts.OutputField> outputFields,
        HttpApiContracts.ExecutionLimits limits,
        Instant createdAt,
        Instant updatedAt
) {
    public static ApiResourceResponse from(ApiResource resource) {
        HttpApiContracts.ResourceDefinition definition = HttpApiConfigurationCodec.readResource(
                resource.getDefinitionJson());
        return new ApiResourceResponse(
                resource.getId(), resource.getDataSourceId(), resource.getCode(), resource.getName(),
                resource.getConnectorType(), resource.isEnabled(), definition.request(), definition.signing(),
                definition.invocationType(), definition.pagination(), definition.asyncJob(),
                definition.recordsPointer(), definition.outputFields(), definition.limits(),
                resource.getCreatedAt(), resource.getUpdatedAt()
        );
    }
}
