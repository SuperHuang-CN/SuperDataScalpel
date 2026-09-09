package cn.superhuang.data.scalpel.business.operations.web.resource;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;

import cn.superhuang.data.scalpel.business.operations.service.*;
import cn.superhuang.data.scalpel.business.operations.web.request.*;
import cn.superhuang.data.scalpel.business.operations.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "告警事件")
@RestController
@RequestMapping("/api/v1/alert-incidents")
@PreAuthorize("isAuthenticated()")
public class AlertIncidentResource {
    private final AlertIncidentService service;
    public AlertIncidentResource(AlertIncidentService service) { this.service = service; }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "告警事件：查询列表")
    @GetMapping public PageResponse<AlertIncidentResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "告警事件：查看详情")
    @GetMapping("/{id}") public AlertIncidentResponse get(@PathVariable UUID id) { return service.get(id); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "告警事件：查询历史")
    @GetMapping("/{id}/history") public PageResponse<AlertActionResponse> history(@PathVariable UUID id, @ParameterObject @ModelAttribute SearchRequest request) { return service.history(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警事件：确认事件")
    @PostMapping("/{id}/actions/acknowledge") @PreAuthorize("hasAuthority('alert.handle')")
    public AlertIncidentResponse acknowledge(@PathVariable UUID id, @Valid @RequestBody AlertAcknowledgementRequest request) { return service.acknowledge(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警事件：关闭事件")
    @PostMapping("/{id}/actions/close") @PreAuthorize("hasAuthority('alert.handle')")
    public AlertIncidentResponse close(@PathVariable UUID id, @Valid @RequestBody CloseAlertRequest request) { return service.close(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警事件：静默事件")
    @PostMapping("/{id}/actions/silence") @PreAuthorize("hasAuthority('alert.handle')")
    public AlertIncidentResponse silence(@PathVariable UUID id, @Valid @RequestBody SilenceAlertRequest request) { return service.silence(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警事件：解除静默")
    @PostMapping("/{id}/actions/unsilence") @PreAuthorize("hasAuthority('alert.handle')")
    public AlertIncidentResponse unsilence(@PathVariable UUID id) { return service.unsilence(id); }
}
