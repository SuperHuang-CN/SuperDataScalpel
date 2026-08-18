package cn.superhuang.data.scalpel.business.assistant.web.response;

public record AssistantSessionDetailResponse(
        AssistantSessionResponse session,
        AssistantChangeSetResponse latestChangeSet,
        AssistantRunResponse latestRun
) {
}
