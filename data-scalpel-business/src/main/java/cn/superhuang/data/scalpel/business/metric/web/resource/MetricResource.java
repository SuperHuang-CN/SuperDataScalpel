package cn.superhuang.data.scalpel.business.metric.web.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;

import cn.superhuang.data.scalpel.business.metric.service.*;
import cn.superhuang.data.scalpel.business.metric.web.request.*;
import cn.superhuang.data.scalpel.business.metric.web.response.*;
import cn.superhuang.data.scalpel.business.task.web.response.ModelRelatedTaskResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springdoc.core.annotations.ParameterObject;
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
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查询列表",
         keywords = {"业务指标", "指标口径", "指标状态", "检索"},
         relatedOperations = {"GET /api/v1/metrics/{id}"})
    @GetMapping("") @PreAuthorize("hasAuthority('metric.view')")
 @Operation(summary = "查询业务指标", description = "使用通用 Search DSL 分页查询指标基础资料和当前状态；不查询或计算指标业务数据。")
 public PageResponse<MetricResponse> search(@ParameterObject @ModelAttribute SearchRequest request) { return service.search(request); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查询模型字段候选",
         keywords = {"业务指标", "结果绑定", "模型字段", "字段候选"})
    @GetMapping("/model-fields") @PreAuthorize("hasAuthority('model.view') and hasAuthority('metric.manage')")
 @Operation(summary = "查询指标结果模型字段候选", description = "在一个指定模型内使用通用 Search DSL 分页查询可用于指标结果绑定的字段元数据；不会读取模型物理表数据。")
 public PageResponse<MetricFieldCandidateResponse> fields(@Parameter(description = "模型 UUID。") @RequestParam UUID modelId,@ParameterObject @ModelAttribute SearchRequest request){return service.fields(modelId,request);}
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查看详情",
         keywords = {"业务指标", "详情", "口径", "结果绑定", "诊断"},
         relatedOperations = {"GET /api/v1/metrics/{id}/draft", "GET /api/v1/metrics/{id}/health", "GET /api/v1/metrics/{id}/versions"})
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('metric.view')")
 @Operation(summary = "查看业务指标详情", description = "返回基础资料、当前有效口径、结果绑定引用及管理元数据诊断。从未发布时使用已保存草稿；不会读取指标业务结果。")
 public MetricResponse get(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id) { return service.get(id); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：创建",
         keywords = {"业务指标", "创建指标"})
    @PostMapping("") @PreAuthorize("hasAuthority('metric.manage')")
 @ResponseStatus(HttpStatus.CREATED)
 @Operation(summary = "创建业务指标", description = "创建 DRAFT 指标基础资料和空定义草稿，返回 201。不会生成计算任务、查询结果或发布口径。")
 public MetricResponse create(@Valid @RequestBody CreateMetricRequest request) { return service.create(request); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：修改",
         keywords = {"业务指标", "修改基础资料"})
    @PostMapping("/{id}/actions/update") @PreAuthorize("hasAuthority('metric.manage')")
 @Operation(summary = "修改业务指标基础资料", description = "修改名称、目录、负责人和简介等基础资料；编码创建后不可修改，kind 首次发布后不可修改。不会改变已发布口径版本。")
 public MetricResponse update(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id,@Valid @RequestBody UpdateMetricRequest request) { return service.update(id,request); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：读取草稿",
         keywords = {"业务指标", "草稿", "口径编辑", "摘要"},
         relatedOperations = {"POST /api/v1/metrics/{id}/actions/update-definition", "POST /api/v1/metrics/{id}/actions/publish"})
    @GetMapping("/{id}/draft") @PreAuthorize("hasAuthority('metric.manage') or hasAuthority('metric.publish')")
 @Operation(summary = "读取指标口径草稿", description = "返回已保存的类型化定义、draftFingerprint 和诊断。草稿可以不满足发布必填项，读取不会重新计算业务数据。")
 public MetricDraftResponse draft(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id) { return service.draft(id); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：保存草稿",
         keywords = {"业务指标", "保存口径", "结果绑定", "参考资源"},
         prerequisites = "expectedDraftFingerprint 与服务端当前草稿一致；新增或替换的资源引用有效且调用者拥有对应查看权限。")
    @PostMapping("/{id}/actions/update-definition") @PreAuthorize("hasAuthority('metric.manage')")
 @Operation(summary = "保存指标口径草稿", description = "乐观校验草稿指纹后保存业务口径、时间与粒度、结果绑定和参考资源，并返回当前诊断；允许保存尚不能发布的不完整草稿。")
 public MetricDraftResponse save(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id,@Valid @RequestBody UpdateMetricDefinitionRequest request) { return service.saveDraft(id,request); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：校验草稿",
         keywords = {"业务指标", "草稿校验", "发布诊断"})
    @GetMapping("/{id}/validation") @PreAuthorize("hasAuthority('metric.manage')")
 @Operation(summary = "校验已保存指标草稿", description = "只读校验已保存草稿的发布必填项、结果字段归属和参考资源元数据，返回阻断及非阻断诊断；不保存、不发布。")
 public MetricHealthResponse validation(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id) { return service.draft(id).health(); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：检查健康状态",
         keywords = {"业务指标", "健康状态", "结果绑定", "元数据诊断"})
    @GetMapping("/{id}/health") @PreAuthorize("hasAuthority('metric.view')")
 @Operation(summary = "检查当前指标健康状态", description = "检查当前有效口径及结果绑定的管理元数据，bindingStatus 为 UNBOUND、VALID 或 INVALID；VALID 不表示物理结果完整或口径实现正确。")
 public MetricHealthResponse health(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id) { return service.health(id); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：发布",
         keywords = {"业务指标", "发布口径", "版本"},
         prerequisites = "expectedDraftFingerprint 与当前草稿一致，全部发布必填项和结果引用校验通过。",
         relatedOperations = {"GET /api/v1/metrics/{id}/validation", "GET /api/v1/metrics/{id}/versions"})
    @PostMapping("/{id}/actions/publish") @PreAuthorize("hasAuthority('metric.publish')")
 @Operation(summary = "发布指标口径", description = "校验已保存草稿后生成不可变发布快照并切换当前版本；定义和绑定契约完全相同时可复用最近版本。不会运行或生成计算任务。")
 public MetricResponse publish(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id,@Valid @RequestBody PublishMetricRequest request,Principal principal) { return service.publish(id,request,principal.getName()); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：停用")
    @PostMapping("/{id}/actions/disable") @PreAuthorize("hasAuthority('metric.publish')")
 @Operation(summary = "停用业务指标", description = "将当前指标设为 DISABLED，保留草稿、发布版本和引用说明；不改变任务或模型状态。")
 public MetricResponse disable(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id) { return service.disable(id); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "业务指标：删除",
         prerequisites = "指标从未发布；已发布或停用指标不能删除。")
    @PostMapping("/{id}/actions/delete") @PreAuthorize("hasAuthority('metric.manage')")
 @ResponseStatus(HttpStatus.NO_CONTENT)
 @Operation(summary = "删除未发布业务指标", description = "删除从未发布的指标及草稿投影并返回 204；存在任何发布历史时拒绝删除。")
 public void delete(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id) { service.delete(id); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查询版本列表",
         keywords = {"业务指标", "发布版本", "历史口径"})
    @GetMapping("/{id}/versions") @PreAuthorize("hasAuthority('metric.view')")
 @Operation(summary = "查询指标发布版本", description = "使用通用 Search DSL 分页查询该指标的不可变发布快照摘要；历史版本说明当时口径和引用，不表示历史物理数据。")
 public PageResponse<MetricVersionResponse> versions(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id,@ParameterObject @ModelAttribute SearchRequest request) { return service.versions(id,request); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查询版本详情")
    @GetMapping("/{id}/versions/{version}") @PreAuthorize("hasAuthority('metric.view')")
 @Operation(summary = "查询指标发布版本详情", description = "返回指定递增版本的不可变口径、当时资源名称/编码与绑定解释快照；不会用当前资源元数据改写历史。")
 public MetricVersionResponse version(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id,@Parameter(description = "指标发布版本号，从 1 开始。") @PathVariable int version) { return service.version(id,version); }
 @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "业务指标：查询关联任务",
         keywords = {"业务指标", "关联任务", "结果模型", "字段血缘"})
    @GetMapping("/{id}/tasks") @PreAuthorize("hasAuthority('metric.view') and hasAuthority('task.view') and hasAuthority('model.view')")
 @Operation(summary = "查询指标关联任务", description = "从指标默认展示定义的结果模型反查当前保存定义中将该模型作为输出引用的任务。从未发布时使用草稿绑定，已发布或停用时使用当前发布版本绑定；没有有效结果模型时返回空分页。结果只包含任务与模型引用关系，不返回字段血缘，也不证明任务实现了指标口径或产生完整结果。")
 public PageResponse<ModelRelatedTaskResponse> tasks(@Parameter(description = "业务指标 UUID。") @PathVariable UUID id,@ParameterObject @ModelAttribute SearchRequest request) { return relations.metricTasks(id,request); }
}
