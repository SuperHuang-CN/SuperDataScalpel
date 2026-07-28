package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.Size;

public record TestStoredServiceEngineRequest(
        @Size(max = 500) String adminUrl,
        @Size(max = 1000) String managementToken
) {
}
