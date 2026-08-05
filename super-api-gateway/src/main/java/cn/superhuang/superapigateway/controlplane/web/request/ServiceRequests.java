package cn.superhuang.superapigateway.controlplane.web.request;

import cn.superhuang.superapigateway.controlplane.domain.AccessMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ServiceRequests {
    private ServiceRequests() {
    }

    public record Create(
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9._-]{0,63}") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 1000) String upstreamUri,
            @NotNull AccessMode accessMode,
            @Min(100) @Max(120_000) int connectTimeoutMs,
            @Min(100) @Max(600_000) int responseTimeoutMs,
            boolean enabled,
            @Size(max = 1000) String description,
            @Size(max = 64) String source,
            @Size(max = 128) String externalId
    ) {
    }

    public record Update(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 1000) String upstreamUri,
            @NotNull AccessMode accessMode,
            @Min(100) @Max(120_000) int connectTimeoutMs,
            @Min(100) @Max(600_000) int responseTimeoutMs,
            boolean enabled,
            @Size(max = 1000) String description
    ) {
    }
}
