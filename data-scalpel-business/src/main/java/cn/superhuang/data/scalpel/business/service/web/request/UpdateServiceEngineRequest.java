package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateServiceEngineRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 500) String adminUrl,
        @NotBlank @Size(max = 500) String runtimeUrl,
        @Size(max = 1000) String managementToken,
        @Size(max = 200) String geoServerUsername,
        @Size(max = 1000) String geoServerPassword,
        @Size(max = 100) String geoServerWorkspace,
        boolean enabled,
        @Size(max = 1000) String description
) {
    public UpdateServiceEngineRequest(
            String name,
            String adminUrl,
            String runtimeUrl,
            String managementToken,
            boolean enabled,
            String description
    ) {
        this(name, adminUrl, runtimeUrl, managementToken, null, null, null, enabled, description);
    }
}
