package cn.superhuang.data.scalpel.business.assistant.web.response;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantRun;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantRunStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssistantRunResponse(
        UUID id,
        String modelName,
        AssistantRunStatus status,
        Instant startedAt,
        Instant completedAt,
        String failureSummary,
        List<AssistantToolInvocationResponse> toolInvocations
) {
    public AssistantRunResponse {
        toolInvocations = List.copyOf(toolInvocations);
    }

    public static AssistantRunResponse from(
            AssistantRun run,
            List<AssistantToolInvocationResponse> toolInvocations
    ) {
        return new AssistantRunResponse(
                run.getId(), run.getModelNameSnapshot(), run.getStatus(), run.getStartedAt(), run.getCompletedAt(),
                run.getFailureSummary(), toolInvocations
        );
    }
}
