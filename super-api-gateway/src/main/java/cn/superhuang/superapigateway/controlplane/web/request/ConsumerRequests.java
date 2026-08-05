package cn.superhuang.superapigateway.controlplane.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ConsumerRequests {
    private ConsumerRequests() {
    }

    public record Create(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9._-]{1,63}") String code,
            @NotBlank @Size(max = 120) String name,
            boolean enabled,
            @Size(max = 1000) String description,
            @Size(max = 64) String source,
            @Size(max = 128) String externalId
    ) {
    }

    public record Update(
            @NotBlank @Size(max = 120) String name,
            boolean enabled,
            @Size(max = 1000) String description
    ) {
    }
}
