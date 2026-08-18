package cn.superhuang.data.scalpel.business.assistant.web.response;

import java.util.List;
import java.util.UUID;

public record AssistantTurnResponse(
        UUID runId,
        AssistantMessageResponse assistantMessage,
        List<AssistantClientActionResponse> clientActions,
        AssistantChangeSetResponse pendingChangeSet
) {
    public AssistantTurnResponse {
        clientActions = List.copyOf(clientActions);
    }
}
