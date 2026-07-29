package cn.superhuang.data.scalpel.business.compute.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "compute_engine", uniqueConstraints = {
        @UniqueConstraint(name = "uk_compute_engine_name", columnNames = "name"),
        @UniqueConstraint(name = "uk_compute_engine_command_topic", columnNames = "command_topic"),
        @UniqueConstraint(name = "uk_compute_engine_runner_event_topic", columnNames = "runner_event_topic")
})
public class ComputeEngine extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "dispatcher_base_url", nullable = false, length = 500)
    private String dispatcherBaseUrl;

    @Column(name = "access_token_ciphertext", nullable = false, length = 2048)
    private String accessTokenCiphertext;

    @Enumerated(EnumType.STRING)
    @Column(name = "expected_backend_type", nullable = false, length = 32)
    private ComputeBackendType expectedBackendType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reported_backend_type", length = 32)
    private ComputeBackendType reportedBackendType;

    @Enumerated(EnumType.STRING)
    @Column(name = "registration_state", nullable = false, length = 32)
    private ComputeEngineRegistrationState registrationState;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_state", nullable = false, length = 16)
    private ComputeEngineHealthState healthState;

    @Column(name = "command_topic", nullable = false, length = 249)
    private String commandTopic;

    @Column(name = "runner_event_topic", nullable = false, length = 249)
    private String runnerEventTopic;

    @Column(name = "admin_event_topic", nullable = false, length = 249)
    private String adminEventTopic;

    @Column(name = "max_queued_executions", nullable = false)
    private int maxQueuedExecutions;

    @Column(name = "max_concurrent_submissions", nullable = false)
    private int maxConcurrentSubmissions;

    @Column(name = "max_in_flight_applications", nullable = false)
    private int maxInFlightApplications;

    @Column(name = "dispatcher_instance_id", length = 100)
    private String dispatcherInstanceId;

    @Column(name = "last_check_at")
    private Instant lastCheckAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "detached_at")
    private Instant detachedAt;

    @Column(name = "detach_reason", length = 500)
    private String detachReason;

    protected ComputeEngine() {
    }

    public static ComputeEngine create(
            String name,
            String description,
            String dispatcherBaseUrl,
            String accessTokenCiphertext,
            ComputeBackendType expectedBackendType,
            String commandTopic,
            String runnerEventTopic,
            String adminEventTopic,
            int maxQueuedExecutions,
            int maxConcurrentSubmissions,
            int maxInFlightApplications
    ) {
        ComputeEngine engine = new ComputeEngine();
        engine.registrationState = ComputeEngineRegistrationState.CREATED;
        engine.healthState = ComputeEngineHealthState.UNKNOWN;
        engine.applyConfiguration(
                name, description, dispatcherBaseUrl, accessTokenCiphertext, expectedBackendType,
                commandTopic, runnerEventTopic, adminEventTopic,
                maxQueuedExecutions, maxConcurrentSubmissions, maxInFlightApplications
        );
        return engine;
    }

    public void update(
            String name,
            String description,
            String dispatcherBaseUrl,
            String accessTokenCiphertext,
            ComputeBackendType expectedBackendType,
            String commandTopic,
            String runnerEventTopic,
            String adminEventTopic,
            int maxQueuedExecutions,
            int maxConcurrentSubmissions,
            int maxInFlightApplications
    ) {
        String normalizedName = required(name, "名称");
        String normalizedDescription = optional(description);
        String normalizedUrl = normalizeUrl(dispatcherBaseUrl);
        String normalizedToken = required(accessTokenCiphertext, "访问令牌密文");
        ComputeBackendType normalizedBackend = Objects.requireNonNull(expectedBackendType, "计算后端类型不能为空");
        String normalizedCommandTopic = normalizeTopic(commandTopic, "命令 Topic");
        String normalizedRunnerEventTopic = normalizeTopic(runnerEventTopic, "Runner 事件 Topic");
        String normalizedAdminEventTopic = normalizeTopic(adminEventTopic, "Admin 事件 Topic");
        validateAdmission(maxQueuedExecutions, maxConcurrentSubmissions, maxInFlightApplications);

        boolean changed = !Objects.equals(this.name, normalizedName)
                || !Objects.equals(this.description, normalizedDescription)
                || !Objects.equals(this.dispatcherBaseUrl, normalizedUrl)
                || !Objects.equals(this.accessTokenCiphertext, normalizedToken)
                || this.expectedBackendType != normalizedBackend
                || !Objects.equals(this.commandTopic, normalizedCommandTopic)
                || !Objects.equals(this.runnerEventTopic, normalizedRunnerEventTopic)
                || !Objects.equals(this.adminEventTopic, normalizedAdminEventTopic)
                || this.maxQueuedExecutions != maxQueuedExecutions
                || this.maxConcurrentSubmissions != maxConcurrentSubmissions
                || this.maxInFlightApplications != maxInFlightApplications;

        applyConfiguration(
                normalizedName, normalizedDescription, normalizedUrl, normalizedToken, normalizedBackend,
                normalizedCommandTopic, normalizedRunnerEventTopic, normalizedAdminEventTopic,
                maxQueuedExecutions, maxConcurrentSubmissions, maxInFlightApplications
        );
        if (changed) {
            healthState = ComputeEngineHealthState.UNKNOWN;
            reportedBackendType = null;
            dispatcherInstanceId = null;
            lastCheckAt = null;
            lastError = null;
        }
    }

    public boolean sameConfigurationAs(ComputeEngine other) {
        Objects.requireNonNull(other, "候选配置不能为空");
        return Objects.equals(name, other.name)
                && Objects.equals(description, other.description)
                && Objects.equals(dispatcherBaseUrl, other.dispatcherBaseUrl)
                && Objects.equals(accessTokenCiphertext, other.accessTokenCiphertext)
                && expectedBackendType == other.expectedBackendType
                && Objects.equals(commandTopic, other.commandTopic)
                && Objects.equals(runnerEventTopic, other.runnerEventTopic)
                && Objects.equals(adminEventTopic, other.adminEventTopic)
                && maxQueuedExecutions == other.maxQueuedExecutions
                && maxConcurrentSubmissions == other.maxConcurrentSubmissions
                && maxInFlightApplications == other.maxInFlightApplications;
    }

    private void applyConfiguration(
            String name,
            String description,
            String dispatcherBaseUrl,
            String accessTokenCiphertext,
            ComputeBackendType expectedBackendType,
            String commandTopic,
            String runnerEventTopic,
            String adminEventTopic,
            int maxQueuedExecutions,
            int maxConcurrentSubmissions,
            int maxInFlightApplications
    ) {
        this.name = required(name, "名称");
        this.description = optional(description);
        this.dispatcherBaseUrl = normalizeUrl(dispatcherBaseUrl);
        this.accessTokenCiphertext = required(accessTokenCiphertext, "访问令牌密文");
        this.expectedBackendType = Objects.requireNonNull(expectedBackendType, "计算后端类型不能为空");
        this.commandTopic = normalizeTopic(commandTopic, "命令 Topic");
        this.runnerEventTopic = normalizeTopic(runnerEventTopic, "Runner 事件 Topic");
        this.adminEventTopic = normalizeTopic(adminEventTopic, "Admin 事件 Topic");
        validateAdmission(maxQueuedExecutions, maxConcurrentSubmissions, maxInFlightApplications);
        this.maxQueuedExecutions = maxQueuedExecutions;
        this.maxConcurrentSubmissions = maxConcurrentSubmissions;
        this.maxInFlightApplications = maxInFlightApplications;
    }

    public void beginRegistration() {
        registrationState = ComputeEngineRegistrationState.REGISTERING;
        lastError = null;
    }

    public void markHealthy(
            String dispatcherInstanceId,
            ComputeBackendType reportedBackendType
    ) {
        this.dispatcherInstanceId = required(dispatcherInstanceId, "Dispatcher 实例标识");
        this.reportedBackendType = Objects.requireNonNull(reportedBackendType, "Dispatcher 后端类型不能为空");
        this.healthState = ComputeEngineHealthState.UP;
        this.lastCheckAt = Instant.now();
        this.lastError = null;
    }

    public void activate(String dispatcherInstanceId, ComputeBackendType reportedBackendType) {
        markHealthy(dispatcherInstanceId, reportedBackendType);
        registrationState = ComputeEngineRegistrationState.ACTIVE;
        detachedAt = null;
        detachReason = null;
    }

    public void markDraining() {
        registrationState = ComputeEngineRegistrationState.DRAINING;
        healthState = ComputeEngineHealthState.UP;
        lastCheckAt = Instant.now();
        lastError = null;
    }

    public void markInactive() {
        registrationState = ComputeEngineRegistrationState.INACTIVE;
        healthState = ComputeEngineHealthState.UP;
        lastCheckAt = Instant.now();
        lastError = null;
        detachedAt = null;
        detachReason = null;
    }

    public void detach(String reason) {
        String normalizedReason = required(reason, "离线解除绑定原因");
        registrationState = ComputeEngineRegistrationState.DETACHED;
        healthState = ComputeEngineHealthState.DOWN;
        reportedBackendType = null;
        dispatcherInstanceId = null;
        detachedAt = Instant.now();
        detachReason = normalizedReason.substring(0, Math.min(500, normalizedReason.length()));
        lastCheckAt = detachedAt;
        lastError = truncate("已离线解除绑定：" + detachReason);
    }

    public void markHealthFailure(String error) {
        healthState = ComputeEngineHealthState.DOWN;
        lastCheckAt = Instant.now();
        lastError = truncate(error);
    }

    public void markRegistrationFailure(String error) {
        registrationState = ComputeEngineRegistrationState.ERROR;
        markHealthFailure(error);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getDispatcherBaseUrl() { return dispatcherBaseUrl; }
    public String getAccessTokenCiphertext() { return accessTokenCiphertext; }
    public ComputeBackendType getExpectedBackendType() { return expectedBackendType; }
    public ComputeBackendType getReportedBackendType() { return reportedBackendType; }
    public ComputeEngineRegistrationState getRegistrationState() { return registrationState; }
    public ComputeEngineHealthState getHealthState() { return healthState; }
    public String getCommandTopic() { return commandTopic; }
    public String getRunnerEventTopic() { return runnerEventTopic; }
    public String getAdminEventTopic() { return adminEventTopic; }
    public int getMaxQueuedExecutions() { return maxQueuedExecutions; }
    public int getMaxConcurrentSubmissions() { return maxConcurrentSubmissions; }
    public int getMaxInFlightApplications() { return maxInFlightApplications; }
    public String getDispatcherInstanceId() { return dispatcherInstanceId; }
    public Instant getLastCheckAt() { return lastCheckAt; }
    public String getLastError() { return lastError; }
    public Instant getDetachedAt() { return detachedAt; }
    public String getDetachReason() { return detachReason; }

    private static void validateAdmission(int queued, int submissions, int inFlight) {
        if (queued < 0 || submissions < 1 || inFlight < 0) {
            throw new IllegalArgumentException("准入参数必须满足：队列大于等于 0、并发提交大于 0、在途应用大于等于 0");
        }
    }

    private static String normalizeUrl(String value) {
        String normalized = required(value, "Dispatcher 地址").replaceFirst("/+$", "");
        try {
            URI uri = URI.create(normalized);
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Dispatcher 地址必须是有效的 HTTP 或 HTTPS 地址", exception);
        }
        return normalized;
    }

    private static String normalizeTopic(String value, String label) {
        String normalized = required(value, label);
        if (normalized.length() > 249 || !normalized.matches("[A-Za-z0-9._-]+") || ".".equals(normalized) || "..".equals(normalized)) {
            throw new IllegalArgumentException(label + "不是合法的 Kafka Topic");
        }
        return normalized;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value) {
        String safe = value == null || value.isBlank() ? "远程调用失败" : value.trim();
        return safe.substring(0, Math.min(2000, safe.length()));
    }
}
