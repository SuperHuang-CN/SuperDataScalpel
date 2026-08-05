package cn.superhuang.superapigateway.controlplane.web.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RotateApiKeyRequest(
        @Size(min = 32, max = 256)
        @Pattern(regexp = "^[!-~]+$")
        String secret
) {
}
