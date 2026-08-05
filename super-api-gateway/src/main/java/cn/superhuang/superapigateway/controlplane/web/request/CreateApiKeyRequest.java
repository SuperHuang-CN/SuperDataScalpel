package cn.superhuang.superapigateway.controlplane.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateApiKeyRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 64) String source,
        @Size(max = 128) String externalId,
        @Size(min = 32, max = 256)
        @Pattern(regexp = "^[!-~]+$")
        String secret
) {
}
