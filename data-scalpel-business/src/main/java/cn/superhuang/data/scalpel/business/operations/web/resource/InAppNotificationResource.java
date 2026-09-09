package cn.superhuang.data.scalpel.business.operations.web.resource;
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
    @GetMapping public PageResponse<InAppNotificationResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "站内通知：查询未读数")
    @GetMapping("/unread-count") public UnreadNotificationCountResponse unread() { return service.unread(); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "站内通知：标记已读")
    @PostMapping("/{id}/actions/read") public InAppNotificationResponse read(@PathVariable UUID id) { return service.read(id); }
}
