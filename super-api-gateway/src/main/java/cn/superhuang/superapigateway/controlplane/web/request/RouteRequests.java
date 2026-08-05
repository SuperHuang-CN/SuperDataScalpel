package cn.superhuang.superapigateway.controlplane.web.request;

import cn.superhuang.superapigateway.controlplane.domain.GatewayHttpMethod;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public final class RouteRequests {
    private RouteRequests() {
    }

    public record Create(
            @NotNull UUID serviceId,
            @NotBlank @Pattern(regexp = "[a-z][a-z0-9._-]{0,63}") String code,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 500) String pathPattern,
            @NotEmpty Set<GatewayHttpMethod> methods,
            @Min(-10000) @Max(10000) int order,
            @Min(0) @Max(16) int stripPrefixSegments,
            boolean enabled,
            @Size(max = 64) String source,
            @Size(max = 128) String externalId
    ) {
    }

    public record Update(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 500) String pathPattern,
            @NotEmpty Set<GatewayHttpMethod> methods,
            @Min(-10000) @Max(10000) int order,
            @Min(0) @Max(16) int stripPrefixSegments,
            boolean enabled
    ) {
    }
}
