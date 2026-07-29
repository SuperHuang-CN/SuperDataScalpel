package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestServiceEngineRequest(
        @NotBlank @Size(max = 500) String adminUrl,
        @NotBlank @Size(max = 1000) String managementToken
) {
}
