package cn.superhuang.data.scalpel.business.quality.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.quality.service.ModelQualityRuleService;
import cn.superhuang.data.scalpel.business.quality.service.ModelQualityOverviewService;
import cn.superhuang.data.scalpel.business.quality.web.request.AcceptModelQualityRuleSuggestionsRequest;
import cn.superhuang.data.scalpel.business.quality.web.request.CreateModelQualityRuleRequest;
import cn.superhuang.data.scalpel.business.quality.web.request.UpdateModelQualityRuleRequest;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityRuleResponse;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityRuleSuggestionResponse;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "模型质量规则")
public class ModelQualityRuleResource {

    private final ModelQualityRuleService service;
    private final ModelQualityOverviewService overviewService;

    public ModelQualityRuleResource(
            ModelQualityRuleService service,
            ModelQualityOverviewService overviewService
    ) {
        this.service = service;
        this.overviewService = overviewService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "模型质量：查询最近概览",
            keywords = {"模型", "数据质量", "质检", "质量结论", "规则结果"},
            relatedOperations = {"GET /api/v1/models/{modelId}/quality-rules", "GET /api/v1/tasks/{id}/model-quality-definition", "POST /api/v1/tasks/{id}/actions/run", "GET /api/v1/task-runs/{runId}"})
    @GetMapping("/models/{modelId}/quality-overview")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询模型最近质量概览和规则结果", description = "返回按排队时间选择的最近质检运行，以及按结束时间选择的最近一次成功且形成质量结论的有效结果。规则明细从同一次不可变结果制品读取；存储不可用或制品无效时仍返回管理库汇总，不触发新的质检。")
    public ModelQualityOverviewResponse overview(@Parameter(description = "模型 UUID。") @PathVariable UUID modelId) {
        return overviewService.get(modelId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "模型质量：查询规则",
            keywords = {"模型", "数据质量", "质量规则", "约束"},
            relatedOperations = {"GET /api/v1/models/{modelId}/quality-rule-suggestions", "GET /api/v1/models/{modelId}/quality-overview", "GET /api/v1/tasks/{id}/model-quality-definition"})
    @GetMapping("/models/{modelId}/quality-rules")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询模型质量规则", description = "按创建时间返回模型的全部启用、停用和失效规则，并展开当前引用字段、字典及引用目标模型；不返回运行结果。")
    public List<ModelQualityRuleResponse> list(@Parameter(description = "模型 UUID。") @PathVariable UUID modelId) {
        return service.list(modelId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "模型质量：生成规则建议",
            keywords = {"模型", "数据质量", "规则建议", "主键", "码表", "时间键", "Geometry"},
            prerequisites = "模型存在且字段定义可读取。",
            relatedOperations = {"POST /api/v1/models/{modelId}/quality-rules/actions/accept-suggestions"})
    @GetMapping("/models/{modelId}/quality-rule-suggestions")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "根据模型字段生成质量规则建议", description = "根据非空/主键、标准字典、时间键和 Geometry 元数据确定性生成建议，并排除当前已有同名或同语义规则；不调用模型，不保存规则。")
    public List<ModelQualityRuleSuggestionResponse> suggestions(@Parameter(description = "模型 UUID。") @PathVariable UUID modelId) {
        return service.suggestions(modelId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "模型质量：新增规则",
            keywords = {"模型", "数据质量", "新增质量规则"},
            prerequisites = "模型存在；定义引用的字段和引用目标在结构上与规则类型兼容；模型内不存在同名或同语义 key 的规则。码表或引用数据源当前不可用不会阻止保存，但会使运行跳过规则。",
            relatedOperations = {"GET /api/v1/models/{modelId}/quality-rules"})
    @PostMapping("/models/{modelId}/quality-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "新增模型质量规则", description = "校验并规范化多态规则定义后创建，返回 201。该操作只定义后续质检应检查的内容，不创建或运行质检任务。")
    public ModelQualityRuleResponse create(
            @Parameter(description = "模型 UUID。") @PathVariable UUID modelId,
            @Valid @RequestBody CreateModelQualityRuleRequest request
    ) {
        return service.create(modelId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "模型质量：批量采纳规则建议",
            keywords = {"模型", "数据质量", "批量采纳", "规则建议"},
            prerequisites = "先查询该模型当前规则建议，并提交其中不重复的 key；模型字段与已有规则在两次请求间未产生冲突。",
            relatedOperations = {"GET /api/v1/models/{modelId}/quality-rule-suggestions", "GET /api/v1/models/{modelId}/quality-rules"})
    @PostMapping("/models/{modelId}/quality-rules/actions/accept-suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "批量采纳模型质量规则建议", description = "在一个事务中重新生成当前建议、校验全部 key 及名称/语义唯一性，并创建为停用规则；任一建议过期、重复或冲突时整批失败，不产生部分规则。")
    public List<ModelQualityRuleResponse> acceptSuggestions(
            @Parameter(description = "模型 UUID。") @PathVariable UUID modelId,
            @Valid @RequestBody AcceptModelQualityRuleSuggestionsRequest request
    ) {
        return service.acceptSuggestions(modelId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "模型质量：修改规则",
            keywords = {"模型", "数据质量", "修改质量规则"},
            prerequisites = "规则存在；新定义类型与创建时类型相同，当前字段和引用目标结构兼容，且模型内不存在同名或同语义 key 的其他规则。")
    @PostMapping("/model-quality-rules/{id}/actions/update")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "修改模型质量规则", description = "更新名称、说明、严重程度和定义，保留启用状态；规则类型不可变。更新成功会清除失效标记，但不会改写历史质检快照或自动重新运行。")
    public ModelQualityRuleResponse update(
            @Parameter(description = "质量规则 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateModelQualityRuleRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "模型质量：启用规则",
            keywords = {"模型", "数据质量", "启用质量规则"},
            prerequisites = "规则存在，当前字段、码表绑定和引用目标字段在结构上兼容，且模型内不存在同名或同语义 key 的其他规则。码表启停和引用数据源可读性在创建运行时另行判断。")
    @PostMapping("/model-quality-rules/{id}/actions/enable")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "启用模型质量规则", description = "使用当前模型字段、码表绑定和引用目标字段重新校验规则定义，清除失效标记并设置为启用；不检查目标及引用数据源的实际连接，不触发质检，也不改变历史运行。运行准备时依赖不可用的启用规则仍会以稳定原因码跳过。")
    public ModelQualityRuleResponse enable(@Parameter(description = "质量规则 UUID。") @PathVariable UUID id) {
        return service.enable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "模型质量：停用规则",
            keywords = {"模型", "数据质量", "停用质量规则"})
    @PostMapping("/model-quality-rules/{id}/actions/disable")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "停用模型质量规则", description = "使规则不再执行；后续质检会将其以 RULE_DISABLED 计入跳过规则。不会删除定义、触发质检或改变历史结果。")
    public ModelQualityRuleResponse disable(@Parameter(description = "质量规则 UUID。") @PathVariable UUID id) {
        return service.disable(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "模型质量：删除规则",
            keywords = {"模型", "数据质量", "删除质量规则"})
    @PostMapping("/model-quality-rules/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "删除模型质量规则", description = "删除规则定义并返回 204；已完成质检的不可变结果制品仍保留该规则快照，用于解释历史汇总。")
    public void delete(@Parameter(description = "质量规则 UUID。") @PathVariable UUID id) {
        service.delete(id);
    }
}
