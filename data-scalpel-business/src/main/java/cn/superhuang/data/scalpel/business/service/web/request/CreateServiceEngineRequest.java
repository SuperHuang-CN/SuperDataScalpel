package cn.superhuang.data.scalpel.business.service.web.request;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import jakarta.validation.constraints.NotNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateServiceEngineRequest(
        @NotNull ServiceEngineType type,
        @Size(max = 64) String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 500) String adminUrl,
        @NotBlank @Size(max = 500) String runtimeUrl,
        @Size(max = 1000) String managementToken,
        @Size(max = 200) String geoServerUsername,
        @Size(max = 1000) String geoServerPassword,
        @Size(max = 100) String geoServerWorkspace,
        Boolean enabled,
        @Size(max = 1000) String description
) {
    public CreateServiceEngineRequest(
            String name,
            String adminUrl,
            String runtimeUrl,
            String managementToken,
            Boolean enabled,
            String description
    ) {
        this(
                ServiceEngineType.DATASCALPEL, null, name, adminUrl, runtimeUrl,
                managementToken, null, null, null, enabled, description
        );
    }
}
