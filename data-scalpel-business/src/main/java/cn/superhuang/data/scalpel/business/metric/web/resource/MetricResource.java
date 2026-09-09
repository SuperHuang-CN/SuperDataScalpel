package cn.superhuang.data.scalpel.business.metric.web.resource;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;

import cn.superhuang.data.scalpel.business.metric.service.*;
import cn.superhuang.data.scalpel.business.metric.web.request.*;
import cn.superhuang.data.scalpel.business.metric.web.response.*;
import cn.superhuang.data.scalpel.business.task.web.response.ModelRelatedTaskResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import jakarta.validation.Valid;
import java.util.UUID;
import java.security.Principal;
@io.swagger.v3.oas.annotations.tags.Tag(name = "业务指标")
@RestController
@RequestMapping("/api/v1/metrics")
public class MetricResource {
 private final MetricManagementService service;
 private final MetricRelationService relations;
 public MetricResource(MetricManagementService service,MetricRelationService relations){this.service=service;this.relations=relations;}
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查询列表")
    @GetMapping("") @PreAuthorize("hasAuthority('metric.view')")
 public PageResponse<MetricResponse> search(@ModelAttribute SearchRequest request) { return service.search(request); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查询模型字段候选")
    @GetMapping("/model-fields") @PreAuthorize("hasAuthority('model.view') and hasAuthority('metric.manage')")
 public PageResponse<MetricFieldCandidateResponse> fields(@RequestParam UUID modelId,@ModelAttribute SearchRequest request){return service.fields(modelId,request);}
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查看详情")
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('metric.view')")
 public MetricResponse get(@PathVariable UUID id) { return service.get(id); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：创建")
    @PostMapping("") @PreAuthorize("hasAuthority('metric.manage')")
 @ResponseStatus(HttpStatus.CREATED)
 public MetricResponse create(@Valid @RequestBody CreateMetricRequest request) { return service.create(request); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：修改")
    @PostMapping("/{id}/actions/update") @PreAuthorize("hasAuthority('metric.manage')")
 public MetricResponse update(@PathVariable UUID id,@Valid @RequestBody UpdateMetricRequest request) { return service.update(id,request); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：读取草稿")
    @GetMapping("/{id}/draft") @PreAuthorize("hasAuthority('metric.manage') or hasAuthority('metric.publish')")
 public MetricDraftResponse draft(@PathVariable UUID id) { return service.draft(id); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：保存草稿")
    @PostMapping("/{id}/actions/update-definition") @PreAuthorize("hasAuthority('metric.manage')")
 public MetricDraftResponse save(@PathVariable UUID id,@Valid @RequestBody UpdateMetricDefinitionRequest request) { return service.saveDraft(id,request); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：校验")
    @GetMapping("/{id}/validation") @PreAuthorize("hasAuthority('metric.manage')")
 public MetricHealthResponse validation(@PathVariable UUID id) { return service.draft(id).health(); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：检查健康状态")
    @GetMapping("/{id}/health") @PreAuthorize("hasAuthority('metric.view')")
 public MetricHealthResponse health(@PathVariable UUID id) { return service.health(id); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "业务指标：发布")
    @PostMapping("/{id}/actions/publish") @PreAuthorize("hasAuthority('metric.publish')")
 public MetricResponse publish(@PathVariable UUID id,@Valid @RequestBody PublishMetricRequest request,Principal principal) { return service.publish(id,request,principal.getName()); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：停用")
    @PostMapping("/{id}/actions/disable") @PreAuthorize("hasAuthority('metric.publish')")
 public MetricResponse disable(@PathVariable UUID id) { return service.disable(id); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：删除")
    @PostMapping("/{id}/actions/delete") @PreAuthorize("hasAuthority('metric.manage')")
 @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable UUID id) { service.delete(id); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查询版本列表")
    @GetMapping("/{id}/versions") @PreAuthorize("hasAuthority('metric.view')")
 public PageResponse<MetricVersionResponse> versions(@PathVariable UUID id,@ModelAttribute SearchRequest request) { return service.versions(id,request); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查询版本详情")
    @GetMapping("/{id}/versions/{version}") @PreAuthorize("hasAuthority('metric.view')")
 public MetricVersionResponse version(@PathVariable UUID id,@PathVariable int version) { return service.version(id,version); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查询关联任务")
    @GetMapping("/{id}/tasks") @PreAuthorize("hasAuthority('metric.view') and hasAuthority('task.view') and hasAuthority('model.view')")
 public PageResponse<ModelRelatedTaskResponse> tasks(@PathVariable UUID id,@ModelAttribute SearchRequest request) { return relations.metricTasks(id,request); }
}
