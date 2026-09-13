package cn.superhuang.data.scalpel.business.operations.web.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "查询告警规则", description = "按通用 Search DSL 分页查询系统默认规则和对象级覆盖，返回阈值、接收人、通道、启用状态及配置版本。")
    @GetMapping public PageResponse<AlertRuleResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "告警规则：查询接收人")
    @Operation(summary = "查询告警接收人候选", description = "按规则类型和关键词分页查询当前启用且具备相应来源查看权限的系统用户，用于选择站内通知接收人；页码从 0 开始。")
    @GetMapping("/recipients") public PageResponse<AlertRecipientResponse> recipients(@Parameter(description = "必填规则类型；用于按该类告警来源的查看权限筛选候选用户。", required = true) @RequestParam AlertRuleType ruleType,
            @Parameter(description = "可选关键词，模糊匹配接收人名称或标识。") @RequestParam(required=false) String keyword, @Parameter(description = "页码，从 0 开始。") @RequestParam(defaultValue="0") int page,
            @Parameter(description = "每页记录数，默认 100，范围 1～500。") @RequestParam(defaultValue="100") int size) { return recipients.search(ruleType, keyword, page, size); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：创建")
    @Operation(summary = "创建告警规则", description = "创建全局规则或指定对象的覆盖规则，保存阈值、静默/冷却设置、个人接收人和 Webhook 通道；新规则不会回放启用前的历史事件。")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AlertRuleResponse create(@Valid @RequestBody SaveAlertRuleRequest request) { return service.save(null, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：修改")
    @Operation(summary = "修改告警规则", description = "修改规则阈值、启用配置和通知目标并递增配置版本。已经形成的告警保留触发时快照，不按新规则重写。")
    @PostMapping("/{id}/actions/update")
    public AlertRuleResponse update(@Parameter(description = "告警规则 UUID。") @PathVariable UUID id, @Valid @RequestBody SaveAlertRuleRequest request) { return service.save(id, request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：应用覆盖配置")
    @Operation(summary = "批量应用告警覆盖规则", description = "一次为 1～100 个目标创建或更新对象级规则覆盖；重复目标按首次出现顺序去重，configuration.subjectId 被每个目标 UUID 替换。整批在同一管理库事务中提交，任一目标或配置无效则整批不生效。")
    @PostMapping("/actions/apply-overrides")
    public java.util.List<AlertRuleResponse> applyOverrides(@Valid @RequestBody ApplyAlertOverridesRequest request) { return service.applyOverrides(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：启用")
    @Operation(summary = "启用告警规则", description = "允许后台从启用时刻起使用该规则评估新事实和仍处于活动状态的持续条件；不回放已结束历史运行。")
    @PostMapping("/{id}/actions/enable")
    public AlertRuleResponse enable(@Parameter(description = "告警规则 UUID。") @PathVariable UUID id) { return service.setEnabled(id, true); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：停用")
    @Operation(summary = "停用告警规则", description = "停止该规则后续评估并抑制尚未开始的通知；已有告警作为历史事实保留，活动持续告警按行政原因结束。")
    @PostMapping("/{id}/actions/disable")
    public AlertRuleResponse disable(@Parameter(description = "告警规则 UUID。") @PathVariable UUID id) { return service.setEnabled(id, false); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "告警规则：重置配置")
    @Operation(summary = "重置告警覆盖规则", description = "删除指定对象的自定义覆盖，使其重新继承同类型全局规则；全局默认规则不能通过该操作删除。成功返回 204。")
    @PostMapping("/{id}/actions/reset-override") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Parameter(description = "告警规则 UUID。") @PathVariable UUID id) { service.resetOverride(id); }
}
