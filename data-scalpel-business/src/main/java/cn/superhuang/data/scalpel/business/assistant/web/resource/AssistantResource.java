package cn.superhuang.data.scalpel.business.assistant.web.resource;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSet;
import cn.superhuang.data.scalpel.business.assistant.service.AssistantChangeSetResponseService;
import cn.superhuang.data.scalpel.business.assistant.service.AssistantAuditQueryService;
import cn.superhuang.data.scalpel.business.assistant.service.AssistantConversationService;
import cn.superhuang.data.scalpel.business.assistant.service.AssistantSessionService;
import cn.superhuang.data.scalpel.business.assistant.service.DirectoryChangePlanService;
import cn.superhuang.data.scalpel.business.assistant.service.DirectoryChangeSetApplicationService;
import cn.superhuang.data.scalpel.business.assistant.service.LlmModelManagementService;
import cn.superhuang.data.scalpel.business.assistant.web.request.CreateAssistantSessionRequest;
import cn.superhuang.data.scalpel.business.assistant.web.request.SelectAssistantModelRequest;
import cn.superhuang.data.scalpel.business.assistant.web.request.SendAssistantMessageRequest;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantChangeSetResponse;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantMessageResponse;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantSessionDetailResponse;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantSessionResponse;
import cn.superhuang.data.scalpel.business.assistant.web.response.AssistantTurnResponse;
import cn.superhuang.data.scalpel.business.assistant.web.response.AvailableLlmModelResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assistant")
@PreAuthorize("isAuthenticated()")
@Tag(name = "AI 助手")
public class AssistantResource {

    private final LlmModelManagementService modelService;
    private final AssistantSessionService sessionService;
    private final AssistantConversationService conversationService;
    private final DirectoryChangePlanService changePlanService;
    private final DirectoryChangeSetApplicationService applicationService;
    private final AssistantChangeSetResponseService changeSetResponseService;
    private final AssistantAuditQueryService auditQueryService;

    public AssistantResource(
            LlmModelManagementService modelService,
            AssistantSessionService sessionService,
            AssistantConversationService conversationService,
            DirectoryChangePlanService changePlanService,
            DirectoryChangeSetApplicationService applicationService,
            AssistantChangeSetResponseService changeSetResponseService,
            AssistantAuditQueryService auditQueryService
    ) {
        this.modelService = modelService;
        this.sessionService = sessionService;
        this.conversationService = conversationService;
        this.changePlanService = changePlanService;
        this.applicationService = applicationService;
        this.changeSetResponseService = changeSetResponseService;
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/models")
    @Operation(summary = "查询当前用户可选的 AI 模型")
    public List<AvailableLlmModelResponse> models() {
        return modelService.availableModels();
    }

    @GetMapping("/sessions")
    @Operation(summary = "查询当前用户的 AI 会话")
    public PageResponse<AssistantSessionResponse> sessions(
            @ParameterObject @ModelAttribute SearchRequest request,
            Authentication authentication
    ) {
        return sessionService.search(authentication.getName(), request);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建 AI 会话")
    public AssistantSessionResponse createSession(
            @RequestBody CreateAssistantSessionRequest request,
            Authentication authentication
    ) {
        return sessionService.create(authentication.getName(), request);
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "查询 AI 会话详情")
    public AssistantSessionDetailResponse session(@PathVariable UUID id, Authentication authentication) {
        AssistantSessionResponse session = sessionService.get(authentication.getName(), id);
        AssistantChangeSet latest = changePlanService.latest(id);
        AssistantChangeSetResponse latestResponse = latest == null
                ? null : changeSetResponseService.toResponse(latest);
        return new AssistantSessionDetailResponse(session, latestResponse, auditQueryService.latestRun(id));
    }

    @GetMapping("/sessions/{id}/messages")
    @Operation(summary = "查询 AI 会话消息")
    public PageResponse<AssistantMessageResponse> messages(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        return sessionService.messages(authentication.getName(), id, page, size);
    }

    @PostMapping("/sessions/{id}/actions/select-model")
    @Operation(summary = "切换 AI 会话模型")
    public AssistantSessionResponse selectModel(
            @PathVariable UUID id,
            @Valid @RequestBody SelectAssistantModelRequest request,
            Authentication authentication
    ) {
        return sessionService.selectModel(authentication.getName(), id, request);
    }

    @PostMapping("/sessions/{id}/actions/message")
    @Operation(summary = "发送 AI 助手消息")
    public AssistantTurnResponse message(
            @PathVariable UUID id,
            @Valid @RequestBody SendAssistantMessageRequest request,
            Authentication authentication
    ) {
        return conversationService.message(authentication.getName(), id, request, authentication);
    }

    @PostMapping("/sessions/{id}/actions/archive")
    @Operation(summary = "归档 AI 会话")
    public AssistantSessionResponse archive(@PathVariable UUID id, Authentication authentication) {
        return sessionService.archive(authentication.getName(), id);
    }

    @GetMapping("/change-sets/{id}")
    @Operation(summary = "查询助手变更计划")
    public AssistantChangeSetResponse changeSet(@PathVariable UUID id, Authentication authentication) {
        return changeSetResponseService.toResponse(changePlanService.getOwned(id, authentication.getName()));
    }

    @PostMapping("/change-sets/{id}/actions/approve")
    @PreAuthorize("hasAuthority('directory.manage')")
    @Operation(summary = "确认并执行目录变更计划")
    public AssistantChangeSetResponse approve(@PathVariable UUID id, Authentication authentication) {
        return changeSetResponseService.toResponse(
                applicationService.approve(id, authentication.getName()).changeSet()
        );
    }

    @PostMapping("/change-sets/{id}/actions/reject")
    @Operation(summary = "拒绝目录变更计划")
    public AssistantChangeSetResponse reject(@PathVariable UUID id, Authentication authentication) {
        return changeSetResponseService.toResponse(changePlanService.reject(id, authentication.getName()));
    }
}
