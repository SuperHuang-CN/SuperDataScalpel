package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSet;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantChangeSetResponse;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AssistantChangeSetResponseService {

    private final DirectoryChangePlanService planService;
    private final ObjectMapper objectMapper;

    public AssistantChangeSetResponseService(DirectoryChangePlanService planService, ObjectMapper objectMapper) {
        this.planService = planService;
        this.objectMapper = objectMapper;
    }

    public AssistantChangeSetResponse toResponse(AssistantChangeSet changeSet) {
        DirectoryChangePlanPayload plan = planService.readPayload(changeSet);
        DirectoryExecutionResult result = null;
        if (changeSet.getResultJson() != null) {
            try {
                result = objectMapper.readValue(changeSet.getResultJson(), DirectoryExecutionResult.class);
            } catch (RuntimeException exception) {
                throw new IllegalStateException("无法读取目录执行结果", exception);
            }
        }
        return new AssistantChangeSetResponse(
                changeSet.getId(), changeSet.getSessionId(), changeSet.getRunId(), changeSet.getChangeType(),
                changeSet.getStatus(), changeSet.getSummary(), plan, result, changeSet.getApprovedBy(),
                changeSet.getApprovedAt(), changeSet.getExecutedAt(), changeSet.getFailureSummary(),
                changeSet.getCreatedAt(), changeSet.getUpdatedAt()
        );
    }
}
