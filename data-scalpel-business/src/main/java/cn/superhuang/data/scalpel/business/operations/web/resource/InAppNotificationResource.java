package cn.superhuang.data.scalpel.business.operations.web.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;

import cn.superhuang.data.scalpel.business.operations.service.InAppNotificationService;
import cn.superhuang.data.scalpel.business.operations.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@io.swagger.v3.oas.annotations.tags.Tag(name = "站内通知")
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("isAuthenticated()")
public class InAppNotificationResource {
    private final InAppNotificationService service;
    public InAppNotificationResource(InAppNotificationService service) { this.service = service; }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "站内通知：查询列表")
    @Operation(summary = "查询我的站内通知", description = "按通用 Search DSL 分页查询当前登录用户的通知，包含告警摘要、事件类型、创建时间和已读状态；不会返回其他用户的通知。")
    @GetMapping public PageResponse<InAppNotificationResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "站内通知：查询未读数")
    @Operation(summary = "查询我的未读通知数", description = "返回当前登录用户仍未标记已读的站内通知数量，用于页面角标；不改变通知状态。")
    @GetMapping("/unread-count") public UnreadNotificationCountResponse unread() { return service.unread(); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "站内通知：标记已读")
    @Operation(summary = "标记我的通知已读", description = "将属于当前登录用户的指定通知标记为已读；重复调用幂等。该操作只改变个人收件箱，不确认或关闭团队共享告警。")
    @PostMapping("/{id}/actions/read") public InAppNotificationResponse read(@Parameter(description = "站内通知 UUID。") @PathVariable UUID id) { return service.read(id); }
}
