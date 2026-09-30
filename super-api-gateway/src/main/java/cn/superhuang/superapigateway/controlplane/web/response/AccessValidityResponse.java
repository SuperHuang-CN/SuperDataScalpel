package cn.superhuang.superapigateway.controlplane.web.response;
import java.time.Instant;
public record AccessValidityResponse(Instant validFrom, Instant expiresAt, int requestsPerSecond,
                                     String state, long targetRevision, long loadedRevision) {}
