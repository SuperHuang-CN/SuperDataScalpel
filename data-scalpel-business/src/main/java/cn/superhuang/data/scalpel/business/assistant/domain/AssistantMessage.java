package cn.superhuang.data.scalpel.business.assistant.domain;

import cn.superhuang.data.scalpel.business.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(
        name = "ai_assistant_message",
        indexes = @Index(name = "idx_ai_message_session", columnList = "session_id,created_at")
)
public class AssistantMessage extends BaseEntity {

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "run_id", updatable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private AssistantMessageRole role;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false, updatable = false)
    private String content;

    protected AssistantMessage() {
    }

    private AssistantMessage(UUID sessionId, UUID runId, AssistantMessageRole role, String content) {
        this.sessionId = java.util.Objects.requireNonNull(sessionId, "会话不能为空");
        this.runId = runId;
        this.role = java.util.Objects.requireNonNull(role, "消息角色不能为空");
        if (content == null || content.trim().isEmpty()) throw new IllegalArgumentException("消息内容不能为空");
        this.content = content.trim();
    }

    public static AssistantMessage create(UUID sessionId, UUID runId, AssistantMessageRole role, String content) {
        return new AssistantMessage(sessionId, runId, role, content);
    }

    public UUID getSessionId() { return sessionId; }
    public UUID getRunId() { return runId; }
    public AssistantMessageRole getRole() { return role; }
    public String getContent() { return content; }
}
