package cn.superhuang.data.scalpel.business.operations.web.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "查询告警投递记录", description = "按通用 Search DSL 分页查询 Webhook 投递的目标、事件类型、尝试次数、状态、耗时及安全错误摘要；站内通知使用独立接口查询。本接口不保存或返回完整响应正文和认证信息。")
    @GetMapping public PageResponse<AlertDeliveryResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "告警投递：重试执行")
    @Operation(summary = "重试失败的告警投递", description = "仅 FAILED 状态可手工重试。操作把实际尝试次数重置为 0 并重新置为待发送，由后台按原目标配置版本异步执行；渠道或规则已停用、配置版本变化、处于静默期、告警已清理或不满足事件顺序时拒绝重试。")
    @PostMapping("/{id}/actions/retry") @PreAuthorize("hasAuthority('alert.manage')")
    public AlertDeliveryResponse retry(@Parameter(description = "告警投递记录 UUID。") @PathVariable UUID id) { return service.retry(id); }
}
