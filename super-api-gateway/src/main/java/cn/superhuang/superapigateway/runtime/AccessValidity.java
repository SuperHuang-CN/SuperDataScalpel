package cn.superhuang.superapigateway.runtime;

import java.time.Instant;
public record AccessValidity(Instant validFrom, Instant expiresAt, int requestsPerSecond) {
    public static AccessValidity unlimited() { return new AccessValidity(null, null, 0); }
    public boolean validAt(Instant now) {
        return (validFrom == null || !now.isBefore(validFrom)) && (expiresAt == null || now.isBefore(expiresAt));
    }
}
