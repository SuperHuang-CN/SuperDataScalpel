package cn.superhuang.data.scalpel.business.operations.web.resource;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;

import cn.superhuang.data.scalpel.business.operations.service.*;
import cn.superhuang.data.scalpel.business.operations.domain.AlertRuleType;
import cn.superhuang.data.scalpel.business.operations.web.request.*;
import cn.superhuang.data.scalpel.business.operations.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "告警规则")
@RestController
@RequestMapping("/api/v1/alert-rules")
@PreAuthorize("hasAuthority('alert.manage')")
public class AlertRuleResource {
    private final AlertRuleService service;
    private final AlertRecipientService recipients;
    public AlertRuleResource(AlertRuleService service, AlertRecipientService recipients) { this.service = service; this.recipients = recipients; }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "告警规则：查询列表")
    @GetMapping public PageResponse<AlertRuleResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "告警规则：查询接收人")
    @GetMapping("/recipients") public PageResponse<AlertRecipientResponse> recipients(@RequestParam AlertRuleType ruleType,
            @RequestParam(required=false) String keyword, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="100") int size) { return recipients.search(ruleType, keyword, page, size); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：创建")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AlertRuleResponse create(@Valid @RequestBody SaveAlertRuleRequest request) { return service.save(null, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：修改")
    @PostMapping("/{id}/actions/update")
    public AlertRuleResponse update(@PathVariable UUID id, @Valid @RequestBody SaveAlertRuleRequest request) { return service.save(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：应用覆盖配置")
    @PostMapping("/actions/apply-overrides")
    public java.util.List<AlertRuleResponse> applyOverrides(@Valid @RequestBody ApplyAlertOverridesRequest request) { return service.applyOverrides(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：启用")
    @PostMapping("/{id}/actions/enable")
    public AlertRuleResponse enable(@PathVariable UUID id) { return service.setEnabled(id, true); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：停用")
    @PostMapping("/{id}/actions/disable")
    public AlertRuleResponse disable(@PathVariable UUID id) { return service.setEnabled(id, false); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：重置配置")
    @PostMapping("/{id}/actions/reset-override") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable UUID id) { service.resetOverride(id); }
}
