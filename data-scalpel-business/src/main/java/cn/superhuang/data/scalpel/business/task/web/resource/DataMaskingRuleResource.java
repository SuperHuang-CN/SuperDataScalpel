package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.task.service.DataMaskingRuleService;
import cn.superhuang.data.scalpel.business.task.web.request.CreateDataMaskingRuleRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateDataMaskingRuleRequest;
import cn.superhuang.data.scalpel.business.task.web.response.DataMaskingRuleResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/masking-rules")
@Tag(name = "脱敏规则管理")
public class DataMaskingRuleResource {

    private final DataMaskingRuleService service;

    public DataMaskingRuleResource(DataMaskingRuleService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询脱敏规则")
    @GetMapping
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询脱敏规则")
    public PageResponse<DataMaskingRuleResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询脱敏规则详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询脱敏规则详情")
    public DataMaskingRuleResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建脱敏规则")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "创建脱敏规则")
    public DataMaskingRuleResponse create(
            @Valid @RequestBody CreateDataMaskingRuleRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改脱敏规则")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "修改脱敏规则")
    public DataMaskingRuleResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDataMaskingRuleRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除脱敏规则")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "删除脱敏规则")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
