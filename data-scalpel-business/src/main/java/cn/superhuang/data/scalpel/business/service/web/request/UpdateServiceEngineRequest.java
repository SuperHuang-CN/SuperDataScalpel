package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateServiceEngineRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 500) String adminUrl,
        @NotBlank @Size(max = 500) String runtimeUrl,
        @Size(max = 1000) String managementToken,
        boolean enabled,
        @Size(max = 1000) String description
) {
}
