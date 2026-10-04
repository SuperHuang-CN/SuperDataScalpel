package cn.superhuang.superapigateway.controlplane.web.request;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.Instant;
public record AccessValidityRequest(Instant validFrom, Instant expiresAt, @Min(0) @Max(1_000_000) int requestsPerSecond) {}
