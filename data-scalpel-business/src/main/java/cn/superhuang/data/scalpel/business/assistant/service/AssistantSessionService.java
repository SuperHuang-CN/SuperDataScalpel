package cn.superhuang.data.scalpel.business.assistant.service;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantMessage;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantSession;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantSessionStatus;
import cn.superhuang.data.scalpel.business.assistant.domain.LlmModelConfiguration;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantMessageRepository;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantRunRepository;
import cn.superhuang.data.scalpel.business.assistant.repository.AssistantSessionRepository;
import cn.superhuang.data.scalpel.business.assistant.web.request.CreateAssistantSessionRequest;
import cn.superhuang.data.scalpel.business.assistant.web.request.SelectAssistantModelRequest;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantMessageResponse;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantSessionResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class AssistantSessionService {

    private final AssistantSessionRepository sessionRepository;
    private final AssistantMessageRepository messageRepository;
    private final AssistantRunRepository runRepository;
    private final LlmModelManagementService modelService;
    private final SearchEngine searchEngine;

    public AssistantSessionService(
            AssistantSessionRepository sessionRepository,
            AssistantMessageRepository messageRepository,
            AssistantRunRepository runRepository,
            LlmModelManagementService modelService,
            SearchEngine searchEngine
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.runRepository = runRepository;
        this.modelService = modelService;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssistantSessionResponse> search(String username, SearchRequest request) {
        Specification<AssistantSession> owned = (root, query, builder) ->
                builder.equal(root.get("ownerUsername"), username);
        Page<AssistantSession> page = searchEngine.search(
                request, AssistantSession.class, sessionRepository, owned
        );
        return new PageResponse<>(
                page.getContent().stream().map(AssistantSessionResponse::from).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional
    public AssistantSessionResponse create(String username, CreateAssistantSessionRequest request) {
        LlmModelConfiguration model = request.modelId() == null
                ? modelService.requireDefault() : modelService.requireAvailable(request.modelId());
        return AssistantSessionResponse.from(sessionRepository.saveAndFlush(
                AssistantSession.create(username, model.getId())
        ));
    }

    @Transactional(readOnly = true)
    public AssistantSessionResponse get(String username, UUID id) {
        return AssistantSessionResponse.from(requireOwned(username, id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AssistantMessageResponse> messages(String username, UUID sessionId, Integer page, Integer size) {
        requireOwned(username, sessionId);
        int resolvedPage = page == null || page < 0 ? 0 : page;
        int resolvedSize = size == null ? 50 : Math.min(100, Math.max(1, size));
        Page<AssistantMessage> result = messageRepository.findAllBySessionId(
                sessionId,
                PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<AssistantMessageResponse> content = new java.util.ArrayList<>(
                result.getContent().stream().map(AssistantMessageResponse::from).toList()
        );
        Collections.reverse(content);
        return new PageResponse<>(content, result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize());
    }

    @Transactional
    public AssistantSessionResponse selectModel(String username, UUID id, SelectAssistantModelRequest request) {
        AssistantSession session = requireOwned(username, id);
        if (runRepository.existsBySessionIdAndStatus(id, cn.superhuang.data.scalpel.business.assistant.domain.AssistantRunStatus.RUNNING)) {
            throw conflict("当前会话正在生成回复，暂不能切换模型");
        }
        modelService.requireAvailable(request.modelId());
        session.selectModel(request.modelId());
        return AssistantSessionResponse.from(sessionRepository.saveAndFlush(session));
    }

    @Transactional
    public AssistantSessionResponse archive(String username, UUID id) {
        AssistantSession session = requireOwned(username, id);
        if (runRepository.existsBySessionIdAndStatus(id, cn.superhuang.data.scalpel.business.assistant.domain.AssistantRunStatus.RUNNING)) {
            throw conflict("当前会话正在生成回复，暂不能归档");
        }
        session.archive();
        return AssistantSessionResponse.from(sessionRepository.saveAndFlush(session));
    }

    @Transactional(readOnly = true)
    public List<AssistantMessage> recentMessages(UUID sessionId) {
        List<AssistantMessage> messages = new java.util.ArrayList<>(
                messageRepository.findTop20BySessionIdOrderByCreatedAtDesc(sessionId)
        );
        Collections.reverse(messages);
        return List.copyOf(messages);
    }

    @Transactional(readOnly = true)
    public AssistantSession requireOwned(String username, UUID id) {
        return sessionRepository.findByIdAndOwnerUsername(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI 助手会话不存在"));
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
