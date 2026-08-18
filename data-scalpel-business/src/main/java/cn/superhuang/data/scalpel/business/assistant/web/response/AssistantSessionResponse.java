package cn.superhuang.data.scalpel.business.assistant.web.response;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantSession;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantSessionStatus;

import java.time.Instant;
import java.util.UUID;

public record AssistantSessionResponse(
        UUID id,
        String title,
        UUID selectedModelId,
        AssistantSessionStatus status,
        Instant lastMessageAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static AssistantSessionResponse from(AssistantSession session) {
        return new AssistantSessionResponse(
                session.getId(), session.getTitle(), session.getSelectedModelId(), session.getStatus(),
                session.getLastMessageAt(), session.getCreatedAt(), session.getUpdatedAt()
        );
    }
}
