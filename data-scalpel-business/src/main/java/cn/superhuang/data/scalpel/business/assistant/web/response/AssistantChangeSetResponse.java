package cn.superhuang.data.scalpel.business.assistant.web.response;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSetStatus;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSetType;
import cn.superhuang.data.scalpel.business.assistant.service.DirectoryChangePlanPayload;
import cn.superhuang.data.scalpel.business.assistant.service.DirectoryExecutionResult;
import cn.superhuang.data.scalpel.business.assistant.service.TaskCanvasApplicationResult;
import cn.superhuang.data.scalpel.business.assistant.service.TaskCanvasProposalPayload;

import java.time.Instant;
import java.util.UUID;

public record AssistantChangeSetResponse(
        UUID id,
        UUID sessionId,
        UUID runId,
        AssistantChangeSetType changeType,
        AssistantChangeSetStatus status,
        String summary,
        DirectoryChangePlanPayload directoryPlan,
        DirectoryExecutionResult directoryResult,
        TaskCanvasProposalPayload taskCanvasProposal,
        TaskCanvasApplicationResult taskCanvasResult,
        String approvedBy,
        Instant approvedAt,
        Instant executedAt,
        String failureSummary,
        Instant createdAt,
        Instant updatedAt
) {
}
