package cn.superhuang.data.scalpel.business.assistant.web.response;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantToolInvocation;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantToolRisk;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantToolStatus;

import java.time.Instant;
import java.util.UUID;

public record AssistantToolInvocationResponse(
        UUID id,
        String toolName,
        AssistantToolRisk risk,
        AssistantToolStatus status,
        String argumentsJson,
        String resultJson,
        Instant createdAt
) {
    public static AssistantToolInvocationResponse from(AssistantToolInvocation invocation) {
        return new AssistantToolInvocationResponse(
                invocation.getId(), invocation.getToolName(), invocation.getRisk(), invocation.getStatus(),
                invocation.getArgumentsJson(), invocation.getResultJson(), invocation.getCreatedAt()
        );
    }
}
