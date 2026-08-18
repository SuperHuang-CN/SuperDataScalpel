package cn.superhuang.data.scalpel.business.quality.web.resource;

import cn.superhuang.data.scalpel.business.quality.service.ModelQualityRuleService;
import cn.superhuang.data.scalpel.business.quality.service.ModelQualityOverviewService;
import cn.superhuang.data.scalpel.business.quality.web.request.AcceptModelQualityRuleSuggestionsRequest;
import cn.superhuang.data.scalpel.business.quality.web.request.CreateModelQualityRuleRequest;
import cn.superhuang.data.scalpel.business.quality.web.request.UpdateModelQualityRuleRequest;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityRuleResponse;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityRuleSuggestionResponse;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
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

    @GetMapping("/models/{modelId}/quality-overview")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询模型最近质量概览和规则结果")
    public ModelQualityOverviewResponse overview(@PathVariable UUID modelId) {
        return overviewService.get(modelId);
    }

    @GetMapping("/models/{modelId}/quality-rules")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "查询模型质量规则")
    public List<ModelQualityRuleResponse> list(@PathVariable UUID modelId) {
        return service.list(modelId);
    }

    @GetMapping("/models/{modelId}/quality-rule-suggestions")
    @PreAuthorize("hasAuthority('model.view')")
    @Operation(summary = "根据模型字段生成质量规则建议")
    public List<ModelQualityRuleSuggestionResponse> suggestions(@PathVariable UUID modelId) {
        return service.suggestions(modelId);
    }

    @PostMapping("/models/{modelId}/quality-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "新增模型质量规则")
    public ModelQualityRuleResponse create(
            @PathVariable UUID modelId,
            @Valid @RequestBody CreateModelQualityRuleRequest request
    ) {
        return service.create(modelId, request);
    }

    @PostMapping("/models/{modelId}/quality-rules/actions/accept-suggestions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "批量采纳模型质量规则建议")
    public List<ModelQualityRuleResponse> acceptSuggestions(
            @PathVariable UUID modelId,
            @Valid @RequestBody AcceptModelQualityRuleSuggestionsRequest request
    ) {
        return service.acceptSuggestions(modelId, request);
    }

    @PostMapping("/model-quality-rules/{id}/actions/update")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "修改模型质量规则")
    public ModelQualityRuleResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateModelQualityRuleRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/model-quality-rules/{id}/actions/enable")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "启用模型质量规则")
    public ModelQualityRuleResponse enable(@PathVariable UUID id) {
        return service.enable(id);
    }

    @PostMapping("/model-quality-rules/{id}/actions/disable")
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "停用模型质量规则")
    public ModelQualityRuleResponse disable(@PathVariable UUID id) {
        return service.disable(id);
    }

    @PostMapping("/model-quality-rules/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('model.update')")
    @Operation(summary = "删除模型质量规则")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
