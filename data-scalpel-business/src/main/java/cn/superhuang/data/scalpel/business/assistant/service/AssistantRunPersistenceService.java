package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantMessage;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantMessageRole;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantRun;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantRunStatus;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantSession;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantSessionStatus;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantToolInvocation;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantToolRisk;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantToolStatus;
import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelConfiguration;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantMessageRepository;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantRunRepository;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantSessionRepository;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantToolInvocationRepository;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantMessageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class AssistantRunPersistenceService {

    private static final int AUDIT_TEXT_LIMIT = 12_000;

    private final AssistantRunRepository runRepository;
    private final AssistantSessionRepository sessionRepository;
    private final AssistantMessageRepository messageRepository;
    private final AssistantToolInvocationRepository toolRepository;
    private final LlmModelManagementService modelService;
    private final AssistantProperties properties;

    public AssistantRunPersistenceService(
            AssistantRunRepository runRepository,
            AssistantSessionRepository sessionRepository,
            AssistantMessageRepository messageRepository,
            AssistantToolInvocationRepository toolRepository,
            LlmModelManagementService modelService,
            AssistantProperties properties
    ) {
        this.runRepository = runRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.toolRepository = toolRepository;
        this.modelService = modelService;
        this.properties = properties;
    }

    @Transactional
    public RunStart start(String username, UUID sessionId, String content) {
        AssistantSession session = sessionRepository.findLockedByIdAndOwnerUsername(sessionId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI 助手会话不存在"));
        if (session.getStatus() == AssistantSessionStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "归档会话不能继续发送消息");
        }
        Instant now = Instant.now();
        var staleRuns = runRepository.findAllBySessionIdAndStatusAndStartedAtBefore(
                sessionId, AssistantRunStatus.RUNNING, now.minus(properties.staleRunTimeout())
        );
        for (AssistantRun stale : staleRuns) stale.fail("运行超过允许时长，已自动结束", now);
        if (!staleRuns.isEmpty()) runRepository.saveAll(staleRuns);
        if (runRepository.existsBySessionIdAndStatus(sessionId, AssistantRunStatus.RUNNING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前会话正在生成回复");
        }
        LlmModelConfiguration model = modelService.requireAvailable(session.getSelectedModelId());
        AssistantRun run = runRepository.saveAndFlush(AssistantRun.start(
                sessionId, username, model.getId(), model.getName(), now
        ));
        AssistantMessage userMessage = messageRepository.saveAndFlush(AssistantMessage.create(
                sessionId, run.getId(), AssistantMessageRole.USER, content
        ));
        session.recordUserMessage(content, now);
        sessionRepository.saveAndFlush(session);
        return new RunStart(run.getId(), sessionId, model.getId(), model.getName(), userMessage.getId());
    }

    @Transactional
    public AssistantMessageResponse complete(UUID runId, UUID sessionId, String assistantContent) {
        AssistantRun run = requireRun(runId);
        AssistantMessage message = messageRepository.saveAndFlush(AssistantMessage.create(
                sessionId, runId, AssistantMessageRole.ASSISTANT, assistantContent
        ));
        Instant now = Instant.now();
        run.complete(now);
        runRepository.saveAndFlush(run);
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.touch(now);
            sessionRepository.save(session);
        });
        return AssistantMessageResponse.from(message);
    }

    @Transactional
    public void fail(UUID runId, String summary) {
        AssistantRun run = requireRun(runId);
        if (run.getStatus() == AssistantRunStatus.RUNNING) {
            run.fail(summary, Instant.now());
            runRepository.saveAndFlush(run);
        }
    }

    @Transactional
    public void recordTool(
            UUID runId,
            String toolName,
            AssistantToolRisk risk,
            AssistantToolStatus status,
            String argumentsJson,
            String resultJson
    ) {
        toolRepository.save(AssistantToolInvocation.record(
                runId,
                truncate(toolName, 100),
                risk,
                status,
                truncate(argumentsJson, AUDIT_TEXT_LIMIT),
                truncate(resultJson, AUDIT_TEXT_LIMIT)
        ));
    }

    private AssistantRun requireRun(UUID id) {
        return runRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI 助手运行不存在"));
    }

    private static String truncate(String value, int maximumLength) {
        String normalized = value == null ? "" : value;
        return normalized.length() <= maximumLength ? normalized : normalized.substring(0, maximumLength);
    }

    public record RunStart(
            UUID runId,
            UUID sessionId,
            UUID modelId,
            String modelName,
            UUID userMessageId
    ) {
    }
}
