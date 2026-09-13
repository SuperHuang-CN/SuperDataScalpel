package cn.superhuang.data.scalpel.business.operations.web.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;

import cn.superhuang.data.scalpel.business.operations.service.AlertChannelService;
import cn.superhuang.data.scalpel.business.operations.web.request.SaveAlertChannelRequest;
import cn.superhuang.data.scalpel.business.operations.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@io.swagger.v3.oas.annotations.tags.Tag(name = "告警通道")
@RestController
@RequestMapping("/api/v1/alert-channels")
@PreAuthorize("hasAuthority('alert.manage')")
public class AlertChannelResource {
    private final AlertChannelService service;
    public AlertChannelResource(AlertChannelService service) { this.service = service; }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "告警通道：查询列表")
    @Operation(summary = "查询告警通道", description = "按通用 Search DSL 分页查询 Webhook 通道及启用状态、配置版本和最近投递摘要；认证密钥和完整地址中的敏感信息不会返回。")
    @GetMapping public PageResponse<AlertChannelResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警通道：创建")
    @Operation(summary = "创建告警通道", description = "保存一个 Webhook 通道及其加密认证配置。创建不会发送测试消息；是否启用由请求决定。")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AlertChannelResponse create(@Valid @RequestBody SaveAlertChannelRequest request) { return service.save(null, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警通道：修改")
    @Operation(summary = "修改告警通道", description = "整体修改 Webhook 名称、地址、启停状态和认证。地址、启停状态或凭据变化会递增配置版本，使使用旧版本的待投递记录被抑制；只修改名称不递增版本。")
    @PostMapping("/{id}/actions/update")
    public AlertChannelResponse update(@Parameter(description = "告警通道 UUID。") @PathVariable UUID id, @Valid @RequestBody SaveAlertChannelRequest request) { return service.save(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警通道：启用")
    @Operation(summary = "启用告警通道", description = "允许后续告警投递使用该通道；不会补发停用期间已被抑制的历史通知。")
    @PostMapping("/{id}/actions/enable") public AlertChannelResponse enable(@Parameter(description = "告警通道 UUID。") @PathVariable UUID id) { return service.setEnabled(id, true); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警通道：停用")
    @Operation(summary = "停用告警通道", description = "阻止尚未开始的后续 Webhook 投递使用该通道；已发送或在途请求保留实际结果。")
    @PostMapping("/{id}/actions/disable") public AlertChannelResponse disable(@Parameter(description = "告警通道 UUID。") @PathVariable UUID id) { return service.setEnabled(id, false); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "告警通道：测试连接")
    @Operation(summary = "发送告警通道测试消息", description = "仅启用的通道可测试。创建一条 TEST 投递任务并返回其标识，后台异步向该通道发送明确标记的测试消息；HTTP 202 只表示投递记录已创建，不代表 Webhook 已送达。")
    @PostMapping("/{id}/actions/test") @ResponseStatus(HttpStatus.ACCEPTED)
    public AlertTestResponse test(@Parameter(description = "告警通道 UUID。") @PathVariable UUID id) { return new AlertTestResponse(service.test(id)); }
}
