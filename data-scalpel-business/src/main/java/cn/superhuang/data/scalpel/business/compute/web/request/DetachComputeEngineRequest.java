package cn.superhuang.data.scalpel.business.compute.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DetachComputeEngineRequest(
        @NotBlank @Size(max = 100) String confirmationName,
        @NotBlank @Size(max = 500) String reason
) {
}
