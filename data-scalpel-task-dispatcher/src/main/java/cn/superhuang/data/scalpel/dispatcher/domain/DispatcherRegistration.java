package cn.superhuang.data.scalpel.dispatcher.domain;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "dispatcher_registration", uniqueConstraints = {
        @UniqueConstraint(name = "uk_dispatcher_registration_engine", columnNames = "engine_id")
})
public class DispatcherRegistration extends DispatcherBaseEntity {

    @Column(name = "engine_id", nullable = false)
    private UUID engineId;

    @Column(name = "dispatcher_instance_id", nullable = false, updatable = false)
    private UUID dispatcherInstanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "backend_type", nullable = false, length = 32)
    private ExecutionBackendType backendType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DispatcherRegistrationState state;

    @Column(name = "command_topic", nullable = false, length = 249)
    private String commandTopic;

    @Column(name = "runner_event_topic", nullable = false, length = 249)
    private String runnerEventTopic;

    @Column(name = "admin_event_topic", nullable = false, length = 249)
    private String adminEventTopic;

    @Column(name = "runner_control_topic", length = 249)
    private String runnerControlTopic;

    @Column(name = "max_queued_executions", nullable = false)
    private int maxQueuedExecutions;

    @Column(name = "max_concurrent_submissions", nullable = false)
    private int maxConcurrentSubmissions;

    @Column(name = "max_in_flight_applications", nullable = false)
    private int maxInFlightApplications;

    @Column(name = "resource_policy", nullable = false, length = 200)
    private String resourcePolicy;

    @Column(name = "registered_at")
    private Instant registeredAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    protected DispatcherRegistration() {
    }

    public static DispatcherRegistration activate(
            UUID engineId,
            UUID dispatcherInstanceId,
            ExecutionBackendType backendType,
            String commandTopic,
            String runnerEventTopic,
            String adminEventTopic,
            String runnerControlTopic,
            int maxQueuedExecutions,
            int maxConcurrentSubmissions,
            int maxInFlightApplications,
            SparkExecutionResourcePolicy resourcePolicy
    ) {
        DispatcherRegistration registration = new DispatcherRegistration();
        registration.engineId = Objects.requireNonNull(engineId);
        registration.dispatcherInstanceId = Objects.requireNonNull(dispatcherInstanceId);
        registration.apply(backendType, commandTopic, runnerEventTopic,
                adminEventTopic, runnerControlTopic,
                maxQueuedExecutions, maxConcurrentSubmissions, maxInFlightApplications, resourcePolicy);
        registration.state = DispatcherRegistrationState.ACTIVE;
        registration.registeredAt = Instant.now();
        return registration;
    }

    public void reactivate(
            ExecutionBackendType backendType,
            String commandTopic,
            String runnerEventTopic,
            String adminEventTopic,
            String runnerControlTopic,
            int maxQueuedExecutions,
            int maxConcurrentSubmissions,
            int maxInFlightApplications,
            SparkExecutionResourcePolicy resourcePolicy
    ) {
        if (state != DispatcherRegistrationState.INACTIVE && state != DispatcherRegistrationState.ERROR) {
            throw new IllegalStateException("当前 Dispatcher 注册不能重新激活");
        }
        apply(backendType, commandTopic, runnerEventTopic,
                adminEventTopic, runnerControlTopic,
                maxQueuedExecutions, maxConcurrentSubmissions, maxInFlightApplications, resourcePolicy);
        state = DispatcherRegistrationState.ACTIVE;
        registeredAt = Instant.now();
        lastError = null;
    }

    private void apply(
            ExecutionBackendType backendType,
            String commandTopic,
            String runnerEventTopic,
            String adminEventTopic,
            String runnerControlTopic,
            int maxQueuedExecutions,
            int maxConcurrentSubmissions,
            int maxInFlightApplications,
            SparkExecutionResourcePolicy resourcePolicy
    ) {
        if (backendType == null || blank(commandTopic) || blank(runnerEventTopic) || blank(adminEventTopic)
                || blank(runnerControlTopic)
                || maxQueuedExecutions < 0 || maxConcurrentSubmissions < 1 || maxInFlightApplications < 0
                || resourcePolicy == null) {
            throw new IllegalArgumentException("Dispatcher 注册配置无效");
        }
        this.backendType = backendType;
        this.commandTopic = commandTopic.trim();
        this.runnerEventTopic = runnerEventTopic.trim();
        this.adminEventTopic = adminEventTopic.trim();
        this.runnerControlTopic = runnerControlTopic.trim();
        this.maxQueuedExecutions = maxQueuedExecutions;
        this.maxConcurrentSubmissions = maxConcurrentSubmissions;
        this.maxInFlightApplications = maxInFlightApplications;
        this.resourcePolicy = encodePolicy(resourcePolicy);
    }

    public boolean sameConfiguration(
            String command,
            String runner,
            String admin,
            String control,
            int queued,
            int submissions,
            int inFlight,
            SparkExecutionResourcePolicy resourcePolicy
    ) {
        return Objects.equals(commandTopic, command)
                && Objects.equals(runnerEventTopic, runner) && Objects.equals(adminEventTopic, admin)
                && Objects.equals(getRunnerControlTopic(), control)
                && maxQueuedExecutions == queued && maxConcurrentSubmissions == submissions
                && maxInFlightApplications == inFlight
                && Objects.equals(getResourcePolicy(), resourcePolicy);
    }

    public void drain() {
        if (state != DispatcherRegistrationState.ACTIVE) throw new IllegalStateException("只有 ACTIVE Dispatcher 可以 Drain");
        state = DispatcherRegistrationState.DRAINING;
    }

    public void beginForcedDeactivation() {
        if (state == DispatcherRegistrationState.INACTIVE) return;
        state = DispatcherRegistrationState.DRAINING;
        lastError = null;
    }

    public void deactivate() {
        state = DispatcherRegistrationState.INACTIVE;
        lastError = null;
    }

    public UUID getEngineId() { return engineId; }
    public UUID getDispatcherInstanceId() { return dispatcherInstanceId; }
    public ExecutionBackendType getBackendType() { return backendType; }
    public DispatcherRegistrationState getState() { return state; }
    public String getCommandTopic() { return commandTopic; }
    public String getRunnerEventTopic() { return runnerEventTopic; }
    public String getAdminEventTopic() { return adminEventTopic; }
    public String getRunnerControlTopic() {
        return blank(runnerControlTopic) ? runnerEventTopic + ".control" : runnerControlTopic;
    }
    public int getMaxQueuedExecutions() { return maxQueuedExecutions; }
    public int getMaxConcurrentSubmissions() { return maxConcurrentSubmissions; }
    public int getMaxInFlightApplications() { return maxInFlightApplications; }
    public SparkExecutionResourcePolicy getResourcePolicy() { return decodePolicy(resourcePolicy, backendType); }
    public Instant getRegisteredAt() { return registeredAt; }
    public String getLastError() { return lastError; }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static String encodePolicy(SparkExecutionResourcePolicy policy) {
        SparkExecutionResourceSpec defaults = policy.defaults();
        SparkExecutionResourceSpec maximums = policy.maximums();
        return "%d,%d,%d,%d,%d;%d,%d,%d,%d,%d".formatted(
                defaults.driverCores(), defaults.driverMemoryMiB(), defaults.executorInstances(),
                defaults.executorCores(), defaults.executorMemoryMiB(), maximums.driverCores(),
                maximums.driverMemoryMiB(), maximums.executorInstances(), maximums.executorCores(),
                maximums.executorMemoryMiB());
    }

    private static SparkExecutionResourcePolicy decodePolicy(String encoded, ExecutionBackendType backendType) {
        if (encoded == null || encoded.isBlank()) return SparkExecutionResourcePolicy.defaultsFor(backendType);
        try {
            String[] values = encoded.split("[;,]");
            if (values.length != 10) throw new IllegalArgumentException();
            int[] numbers = java.util.Arrays.stream(values).mapToInt(Integer::parseInt).toArray();
            return new SparkExecutionResourcePolicy(
                    new SparkExecutionResourceSpec(numbers[0], numbers[1], numbers[2], numbers[3], numbers[4]),
                    new SparkExecutionResourceSpec(numbers[5], numbers[6], numbers[7], numbers[8], numbers[9]));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Dispatcher 运行资源策略损坏", exception);
        }
    }
}
