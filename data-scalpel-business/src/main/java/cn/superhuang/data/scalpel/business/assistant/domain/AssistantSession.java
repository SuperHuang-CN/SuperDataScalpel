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
        name = "ai_assistant_session",
        indexes = {
                @Index(name = "idx_ai_session_owner", columnList = "owner_username,status,last_message_at"),
                @Index(name = "idx_ai_session_model", columnList = "selected_model_id")
        }
)
public class AssistantSession extends BaseEntity {

    @Column(name = "owner_username", nullable = false, updatable = false, length = 100)
    private String ownerUsername;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(name = "selected_model_id", nullable = false)
    private UUID selectedModelId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AssistantSessionStatus status;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    protected AssistantSession() {
    }

    private AssistantSession(String ownerUsername, UUID selectedModelId) {
        this.ownerUsername = requireText(ownerUsername, "会话用户不能为空", 100);
        this.selectedModelId = java.util.Objects.requireNonNull(selectedModelId, "会话模型不能为空");
        title = "新对话";
        status = AssistantSessionStatus.ACTIVE;
        lastMessageAt = Instant.now();
    }

    public static AssistantSession create(String ownerUsername, UUID selectedModelId) {
        return new AssistantSession(ownerUsername, selectedModelId);
    }

    public void selectModel(UUID modelId) {
        selectedModelId = java.util.Objects.requireNonNull(modelId, "会话模型不能为空");
    }

    public void recordUserMessage(String content, Instant now) {
        if ("新对话".equals(title)) {
            String normalized = requireText(content, "消息不能为空", 8000);
            title = normalized.length() <= 60 ? normalized : normalized.substring(0, 60);
        }
        status = AssistantSessionStatus.ACTIVE;
        lastMessageAt = now;
    }

    public void touch(Instant now) {
        lastMessageAt = now;
    }

    public void archive() {
        status = AssistantSessionStatus.ARCHIVED;
    }

    private static String requireText(String value, String message, int maximumLength) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
        String normalized = value.trim();
        if (normalized.length() > maximumLength) throw new IllegalArgumentException("字段长度不能超过 " + maximumLength + " 个字符");
        return normalized;
    }

    public String getOwnerUsername() { return ownerUsername; }
    public String getTitle() { return title; }
    public UUID getSelectedModelId() { return selectedModelId; }
    public AssistantSessionStatus getStatus() { return status; }
    public Instant getLastMessageAt() { return lastMessageAt; }
}
