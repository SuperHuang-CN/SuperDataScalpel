package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.Size;

public record TestStoredServiceEngineRequest(
        @Size(max = 500) String adminUrl,
        @Size(max = 500) String runtimeUrl,
        @Size(max = 1000) String managementToken,
        @Size(max = 200) String geoServerUsername,
        @Size(max = 1000) String geoServerPassword,
        @Size(max = 100) String geoServerWorkspace
) {
    public TestStoredServiceEngineRequest(String adminUrl, String managementToken) {
        this(adminUrl, null, managementToken, null, null, null);
    }
}
