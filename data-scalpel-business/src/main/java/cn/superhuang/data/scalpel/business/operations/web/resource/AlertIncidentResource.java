package cn.superhuang.data.scalpel.business.operations.web.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "查询告警事件", description = "按通用 Search DSL 分页查询当前用户有权查看来源的团队共享告警，包含状态、级别、来源快照和通知摘要；权限过滤也作用于总数。")
    @GetMapping public PageResponse<AlertIncidentResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "告警事件：查看详情")
    @Operation(summary = "查看告警事件详情", description = "返回告警触发证据、规则快照、来源对象、当前处理状态、静默信息和通知摘要；已删除来源仍以历史快照解释。")
    @GetMapping("/{id}") public AlertIncidentResponse get(@Parameter(description = "告警事件 UUID。") @PathVariable UUID id) { return service.get(id); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "告警事件：查询历史")
    @Operation(summary = "查询告警处理历史", description = "按通用 Search DSL 分页返回告警的触发、恢复、确认、关闭、静默和解除静默时间线；仅在有权查看该告警来源时返回。")
    @GetMapping("/{id}/history") public PageResponse<AlertActionResponse> history(@Parameter(description = "告警事件 UUID。") @PathVariable UUID id, @ParameterObject @ModelAttribute SearchRequest request) { return service.history(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警事件：确认事件")
    @Operation(summary = "确认告警事件", description = "把团队共享告警标记为已确认并记录操作者、时间和说明。确认不会改变任务状态，也不会自动静默后续同类告警。")
    @PostMapping("/{id}/actions/acknowledge") @PreAuthorize("hasAuthority('alert.handle')")
    public AlertIncidentResponse acknowledge(@Parameter(description = "告警事件 UUID。") @PathVariable UUID id, @Valid @RequestBody AlertAcknowledgementRequest request) { return service.acknowledge(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警事件：关闭事件")
    @Operation(summary = "关闭告警事件", description = "仅非持续的事件型告警允许人工关闭；持续条件告警必须由系统依据恢复证据关闭。人工关闭只表示处理结束，不会修改来源任务或伪造恢复事件；重复关闭返回当前状态。")
    @PostMapping("/{id}/actions/close") @PreAuthorize("hasAuthority('alert.handle')")
    public AlertIncidentResponse close(@Parameter(description = "告警事件 UUID。") @PathVariable UUID id, @Valid @RequestBody CloseAlertRequest request) { return service.close(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警事件：静默事件")
    @Operation(summary = "静默告警事件", description = "在给定截止时间前，按该事件的规则类型和对象 UUID 静默通知；同一对象同类型的其他活动事件也会受影响。静默期间仍继续观测、评估和记录告警，单次最长 30 天。")
    @PostMapping("/{id}/actions/silence") @PreAuthorize("hasAuthority('alert.handle')")
    public AlertIncidentResponse silence(@Parameter(description = "告警事件 UUID。") @PathVariable UUID id, @Valid @RequestBody SilenceAlertRequest request) { return service.silence(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警事件：解除静默")
    @Operation(summary = "解除告警事件静默", description = "立即结束该事件规则类型与对象范围的当前静默并记录处理历史；即使当前没有有效静默也会记录动作，且不会补发静默期间已被抑制的通知。")
    @PostMapping("/{id}/actions/unsilence") @PreAuthorize("hasAuthority('alert.handle')")
    public AlertIncidentResponse unsilence(@Parameter(description = "告警事件 UUID。") @PathVariable UUID id) { return service.unsilence(id); }
}
