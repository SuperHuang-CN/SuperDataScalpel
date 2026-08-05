package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistration;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistrationStatus;

import java.time.Instant;
import java.util.UUID;

public record ServiceEngineDataSourceRegistrationResponse(
        UUID id,
        UUID engineId,
        String engineCode,
        String engineName,
        UUID dataSourceId,
        String dataSourceCode,
        String dataSourceName,
        String databaseType,
        ServiceEngineDataSourceRegistrationStatus status,
        Instant synchronizedAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {

    public static ServiceEngineDataSourceRegistrationResponse from(
            ServiceEngineDataSourceRegistration registration,
            String engineCode,
            String engineName,
            String dataSourceCode,
            String dataSourceName,
            String databaseType
    ) {
        return new ServiceEngineDataSourceRegistrationResponse(
                registration.getId(), registration.getEngineId(), engineCode, engineName,
                registration.getDataSourceId(), dataSourceCode, dataSourceName, databaseType,
                registration.getStatus(), registration.getSynchronizedAt(), registration.getLastError(),
                registration.getCreatedAt(), registration.getUpdatedAt()
        );
    }
}
