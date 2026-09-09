package cn.superhuang.data.scalpel.business.operations.web.resource;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;

import cn.superhuang.data.scalpel.business.operations.service.AlertDeliveryService;
import cn.superhuang.data.scalpel.business.operations.web.response.AlertDeliveryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@io.swagger.v3.oas.annotations.tags.Tag(name = "告警投递")
@RestController
@RequestMapping("/api/v1/alert-deliveries")
@PreAuthorize("isAuthenticated()")
public class AlertDeliveryResource {
    private final AlertDeliveryService service;
    public AlertDeliveryResource(AlertDeliveryService service) { this.service = service; }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "告警投递：查询列表")
    @GetMapping public PageResponse<AlertDeliveryResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "告警投递：重试执行")
    @PostMapping("/{id}/actions/retry") @PreAuthorize("hasAuthority('alert.manage')")
    public AlertDeliveryResponse retry(@PathVariable UUID id) { return service.retry(id); }
}
