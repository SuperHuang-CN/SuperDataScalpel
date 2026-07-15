package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeployment;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeploymentStatus;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;

import java.time.Instant;
import java.util.UUID;

public record DataServiceResponse(
        UUID id,
        String code,
        String name,
        UUID directoryId,
        UUID modelId,
        UUID engineId,
        String routePath,
        DataServiceStatus status,
        long revision,
        DataServiceDeploymentStatus deploymentStatus,
        String deploymentError,
        Instant deployedAt,
        String description,
        Instant createdAt,
        Instant updatedAt
) {

    public static DataServiceResponse from(DataService service, DataServiceDeployment deployment) {
        return new DataServiceResponse(
                service.getId(), service.getCode(), service.getName(), service.getDirectoryId(), service.getModelId(),
                service.getEngineId(), service.getRoutePath(), service.getStatus(), service.getRevision(),
                deployment == null ? null : deployment.getStatus(), deployment == null ? null : deployment.getLastError(),
                deployment == null ? null : deployment.getDeployedAt(), service.getDescription(),
                service.getCreatedAt(), service.getUpdatedAt()
        );
    }
}
