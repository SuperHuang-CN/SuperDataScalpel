package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeploymentStatus;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataServiceDetailResponse(
        UUID id,
        String code,
        String name,
        UUID directoryId,
        DataServiceType type,
        boolean definitionConfigured,
        Integer definitionVersion,
        StandardDataServiceDefinitionResponse standardDefinition,
        SqlDataServiceDefinitionResponse sqlDefinition,
        ScriptDataServiceDefinitionResponse scriptDefinition,
        UUID engineId,
        String engineRoutePath,
        DataServiceStatus status,
        long revision,
        DataServiceDeploymentStatus deploymentStatus,
        String deploymentError,
        Instant deployedAt,
        List<GatewayServiceBindingResponse> gatewayBindings,
        String description,
        Instant createdAt,
        Instant updatedAt
) {
}
