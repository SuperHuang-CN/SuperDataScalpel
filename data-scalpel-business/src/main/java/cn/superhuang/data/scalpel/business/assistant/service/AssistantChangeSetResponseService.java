package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSet;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantChangeSetResponse;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AssistantChangeSetResponseService {

    private final DirectoryChangePlanService planService;
    private final TaskCanvasProposalService taskCanvasProposalService;
    private final ObjectMapper objectMapper;

    public AssistantChangeSetResponseService(
            DirectoryChangePlanService planService,
            TaskCanvasProposalService taskCanvasProposalService,
            ObjectMapper objectMapper
    ) {
        this.planService = planService;
        this.taskCanvasProposalService = taskCanvasProposalService;
        this.objectMapper = objectMapper;
    }

    public AssistantChangeSetResponse toResponse(AssistantChangeSet changeSet) {
        DirectoryChangePlanPayload plan = null;
        DirectoryExecutionResult result = null;
        TaskCanvasProposalPayload taskCanvasProposal = null;
        TaskCanvasApplicationResult taskCanvasResult = null;
        switch (changeSet.getChangeType()) {
            case DIRECTORY -> {
                plan = planService.readPayload(changeSet);
                if (changeSet.getResultJson() != null) {
                    try {
                        result = objectMapper.readValue(changeSet.getResultJson(), DirectoryExecutionResult.class);
                    } catch (RuntimeException exception) {
                        throw new IllegalStateException("无法读取目录执行结果", exception);
                    }
                }
            }
            case TASK_CANVAS -> {
                taskCanvasProposal = taskCanvasProposalService.readPayload(changeSet);
                taskCanvasResult = taskCanvasProposalService.readResult(changeSet);
            }
        }
        return new AssistantChangeSetResponse(
                changeSet.getId(), changeSet.getSessionId(), changeSet.getRunId(), changeSet.getChangeType(),
                changeSet.getStatus(), changeSet.getSummary(), plan, result,
                taskCanvasProposal, taskCanvasResult, changeSet.getApprovedBy(),
                changeSet.getApprovedAt(), changeSet.getExecutedAt(), changeSet.getFailureSummary(),
                changeSet.getCreatedAt(), changeSet.getUpdatedAt()
        );
    }
}
