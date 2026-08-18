package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.repository.AssistantRunRepository;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantToolInvocationRepository;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantRunResponse;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantToolInvocationResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AssistantAuditQueryService {

    private final AssistantRunRepository runRepository;
    private final AssistantToolInvocationRepository toolRepository;

    public AssistantAuditQueryService(
            AssistantRunRepository runRepository,
            AssistantToolInvocationRepository toolRepository
    ) {
        this.runRepository = runRepository;
        this.toolRepository = toolRepository;
    }

    @Transactional(readOnly = true)
    public AssistantRunResponse latestRun(UUID sessionId) {
        return runRepository.findFirstBySessionIdOrderByStartedAtDesc(sessionId)
                .map(run -> AssistantRunResponse.from(
                        run,
                        toolRepository.findAllByRunIdOrderByCreatedAtAsc(run.getId()).stream()
                                .map(AssistantToolInvocationResponse::from)
                                .toList()
                ))
                .orElse(null);
    }
}
