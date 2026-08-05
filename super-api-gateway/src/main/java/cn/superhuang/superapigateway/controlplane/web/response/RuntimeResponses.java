package cn.superhuang.superapigateway.controlplane.web.response;

import java.time.Instant;
import java.util.List;

public final class RuntimeResponses {
    private RuntimeResponses() {
    }

    public record Summary(
            long targetRevision,
            long services,
            long routes,
            long consumers,
            long apiKeys,
            long subscriptions,
            List<Instance> instances
    ) {
    }

    public record Instance(
            String id,
            String state,
            long loadedRevision,
            Instant startedAt,
            Instant lastSeenAt,
            String lastError,
            String applicationVersion
    ) {
    }
}
