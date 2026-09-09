package cn.superhuang.data.scalpel.business.operations.web.resource;
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
    @GetMapping public PageResponse<AlertChannelResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警通道：创建")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AlertChannelResponse create(@Valid @RequestBody SaveAlertChannelRequest request) { return service.save(null, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警通道：修改")
    @PostMapping("/{id}/actions/update")
    public AlertChannelResponse update(@PathVariable UUID id, @Valid @RequestBody SaveAlertChannelRequest request) { return service.save(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警通道：启用")
    @PostMapping("/{id}/actions/enable") public AlertChannelResponse enable(@PathVariable UUID id) { return service.setEnabled(id, true); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警通道：停用")
    @PostMapping("/{id}/actions/disable") public AlertChannelResponse disable(@PathVariable UUID id) { return service.setEnabled(id, false); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "告警通道：测试连接")
    @PostMapping("/{id}/actions/test") @ResponseStatus(HttpStatus.ACCEPTED)
    public AlertTestResponse test(@PathVariable UUID id) { return new AlertTestResponse(service.test(id)); }
}
