package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.SparkJobIdentity;
import cn.superhuang.datascalpel.sdk.TaskTriggerType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public final class TestSparkJobIdentity implements SparkJobIdentity {
    private final UUID taskId;
    private final UUID runId;
    private final UUID executionId;
    private final int attempt;
    private final int definitionVersion;
    private final TaskTriggerType triggerType;
    private final UUID scheduleId;
    private final Instant scheduledFireAt;
    private final UUID deploymentId;

    private TestSparkJobIdentity(Builder builder) {
        taskId = required(builder.taskId, "taskId");
        runId = required(builder.runId, "runId");
        executionId = required(builder.executionId, "executionId");
        if (builder.attempt < 1 || builder.definitionVersion < 1) {
            throw new IllegalArgumentException("attempt and definitionVersion must be positive");
        }
        attempt = builder.attempt;
        definitionVersion = builder.definitionVersion;
        triggerType = required(builder.triggerType, "triggerType");
        scheduleId = builder.scheduleId;
        scheduledFireAt = builder.scheduledFireAt;
        deploymentId = builder.deploymentId;
        if (triggerType == TaskTriggerType.STREAMING_START && deploymentId == null) {
            throw new IllegalArgumentException("Streaming identity requires deploymentId");
        }
        if (triggerType != TaskTriggerType.STREAMING_START && deploymentId != null) {
            throw new IllegalArgumentException("Batch identity must not define deploymentId");
        }
    }

    public static Builder batch() {
        return new Builder(TaskTriggerType.MANUAL, null);
    }

    public static Builder streaming() {
        return new Builder(TaskTriggerType.STREAMING_START, UUID.randomUUID());
    }

    @Override public UUID taskId() { return taskId; }
    @Override public UUID runId() { return runId; }
    @Override public UUID executionId() { return executionId; }
    @Override public int attempt() { return attempt; }
    @Override public int definitionVersion() { return definitionVersion; }
    @Override public TaskTriggerType triggerType() { return triggerType; }
    @Override public Optional<UUID> scheduleId() { return Optional.ofNullable(scheduleId); }
    @Override public Optional<Instant> scheduledFireAt() { return Optional.ofNullable(scheduledFireAt); }
    @Override public Optional<UUID> deploymentId() { return Optional.ofNullable(deploymentId); }

    public static final class Builder {
        private UUID taskId = UUID.randomUUID();
        private UUID runId = UUID.randomUUID();
        private UUID executionId = UUID.randomUUID();
        private int attempt = 1;
        private int definitionVersion = 1;
        private TaskTriggerType triggerType;
        private UUID scheduleId;
        private Instant scheduledFireAt;
        private UUID deploymentId;

        private Builder(TaskTriggerType triggerType, UUID deploymentId) {
            this.triggerType = triggerType;
            this.deploymentId = deploymentId;
        }

        public Builder taskId(UUID value) { taskId = value; return this; }
        public Builder runId(UUID value) { runId = value; return this; }
        public Builder executionId(UUID value) { executionId = value; return this; }
        public Builder attempt(int value) { attempt = value; return this; }
        public Builder definitionVersion(int value) { definitionVersion = value; return this; }
        public Builder triggerType(TaskTriggerType value) { triggerType = value; return this; }
        public Builder scheduleId(UUID value) { scheduleId = value; return this; }
        public Builder scheduledFireAt(Instant value) { scheduledFireAt = value; return this; }
        public Builder deploymentId(UUID value) { deploymentId = value; return this; }

        public TestSparkJobIdentity build() { return new TestSparkJobIdentity(this); }
    }

    private static <T> T required(T value, String label) {
        if (value == null) throw new IllegalArgumentException(label + " must not be null");
        return value;
    }
}
