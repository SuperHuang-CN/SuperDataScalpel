package cn.superhuang.datascalpel.sdk;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SparkJobIdentity {
    UUID taskId();

    UUID runId();

    UUID executionId();

    int attempt();

    int definitionVersion();

    TaskTriggerType triggerType();

    Optional<UUID> scheduleId();

    Optional<Instant> scheduledFireAt();

    /** Present only for a long-running streaming deployment. */
    default Optional<UUID> deploymentId() {
        return Optional.empty();
    }
}
