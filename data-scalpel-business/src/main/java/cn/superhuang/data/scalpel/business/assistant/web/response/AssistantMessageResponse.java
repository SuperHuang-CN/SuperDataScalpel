package cn.superhuang.data.scalpel.business.assistant.web.response;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantMessage;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantMessageRole;

import java.time.Instant;
import java.util.UUID;

public record AssistantMessageResponse(
        UUID id,
        UUID sessionId,
        UUID runId,
        AssistantMessageRole role,
        String content,
        Instant createdAt
) {
    public static AssistantMessageResponse from(AssistantMessage message) {
        return new AssistantMessageResponse(
                message.getId(), message.getSessionId(), message.getRunId(), message.getRole(),
                message.getContent(), message.getCreatedAt()
        );
    }
}
