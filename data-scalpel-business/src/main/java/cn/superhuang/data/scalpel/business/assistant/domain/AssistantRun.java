package cn.superhuang.data.scalpel.business.assistant.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ai_assistant_run",
        indexes = {
                @Index(name = "idx_ai_run_session", columnList = "session_id,status,started_at"),
                @Index(name = "idx_ai_run_model", columnList = "model_id")
        }
)
public class AssistantRun extends BaseEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "owner_username", nullable = false, updatable = false, length = 100)
    private String ownerUsername;

    @Column(name = "model_id", nullable = false, updatable = false)
    private UUID modelId;

    @Column(name = "model_name_snapshot", nullable = false, updatable = false, length = 200)
    private String modelNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AssistantRunStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "failure_summary", length = 1000)
    private String failureSummary;

    protected AssistantRun() {
    }

    private AssistantRun(UUID sessionId, String ownerUsername, UUID modelId, String modelNameSnapshot, Instant startedAt) {
        this.sessionId = java.util.Objects.requireNonNull(sessionId, "会话不能为空");
        this.ownerUsername = requireText(ownerUsername, "运行用户不能为空", 100);
        this.modelId = java.util.Objects.requireNonNull(modelId, "模型不能为空");
        this.modelNameSnapshot = requireText(modelNameSnapshot, "模型名称不能为空", 200);
        this.startedAt = java.util.Objects.requireNonNull(startedAt, "开始时间不能为空");
        status = AssistantRunStatus.RUNNING;
    }

    public static AssistantRun start(UUID sessionId, String ownerUsername, UUID modelId, String modelName, Instant now) {
        return new AssistantRun(sessionId, ownerUsername, modelId, modelName, now);
    }

    public void complete(Instant now) {
        status = AssistantRunStatus.COMPLETED;
        completedAt = now;
        failureSummary = null;
    }

    public void fail(String summary, Instant now) {
        status = AssistantRunStatus.FAILED;
        completedAt = now;
        failureSummary = truncate(summary, 1000);
    }

    private static String requireText(String value, String message, int maximumLength) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
        String normalized = value.trim();
        if (normalized.length() > maximumLength) throw new IllegalArgumentException("字段长度不能超过 " + maximumLength + " 个字符");
        return normalized;
    }

    private static String truncate(String value, int length) {
        if (value == null) return null;
        return value.length() <= length ? value : value.substring(0, length);
    }

    public UUID getSessionId() { return sessionId; }
    public String getOwnerUsername() { return ownerUsername; }
    public UUID getModelId() { return modelId; }
    public String getModelNameSnapshot() { return modelNameSnapshot; }
    public AssistantRunStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getFailureSummary() { return failureSummary; }
}
