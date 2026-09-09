package cn.superhuang.data.scalpel.business.compute.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineManagementService;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineRuntimeService;
import cn.superhuang.data.scalpel.business.compute.web.request.CreateComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.request.DeactivateComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.request.DetachComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.request.UpdateComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineResponse;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineExecutionResponse;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineRuntimeOverviewResponse;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionScope;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineTestResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "计算引擎")
@RestController
@RequestMapping("/api/v1/compute-engines")
public class ComputeEngineResource {

    private final ComputeEngineManagementService service;
    private final ComputeEngineRuntimeService runtimeService;

    public ComputeEngineResource(ComputeEngineManagementService service, ComputeEngineRuntimeService runtimeService) {
        this.service = service;
        this.runtimeService = runtimeService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "计算引擎：查询列表")
    @GetMapping
    @PreAuthorize("hasAuthority('compute.engine.view')")
    public PageResponse<ComputeEngineResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "计算引擎：查看详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('compute.engine.view')")
    public ComputeEngineResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "计算引擎：查询运行概览")
    @GetMapping("/{id}/runtime-overview")
    @PreAuthorize("hasAuthority('compute.engine.view')")
    public ComputeEngineRuntimeOverviewResponse runtimeOverview(@PathVariable UUID id) {
        return runtimeService.overview(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "计算引擎：查询执行记录")
    @GetMapping("/{id}/executions")
    @PreAuthorize("hasAuthority('compute.engine.view') and hasAuthority('task.view')")
    public PageResponse<ComputeEngineExecutionResponse> executions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "ACTIVE") DispatcherExecutionScope scope,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(100) int size
    ) {
        return runtimeService.executions(id, scope, page, size);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "计算引擎：创建")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('compute.engine.create')")
    public ComputeEngineResponse create(@Valid @RequestBody CreateComputeEngineRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "计算引擎：修改")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('compute.engine.update')")
    public ComputeEngineResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateComputeEngineRequest request) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "计算引擎：重配置引擎")
    @PostMapping("/{id}/actions/reconfigure")
    @PreAuthorize("hasAuthority('compute.engine.update') and hasAuthority('compute.engine.manage')")
    public ComputeEngineResponse reconfigure(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateComputeEngineRequest request
    ) {
        return service.reconfigure(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "计算引擎：测试连接")
    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('compute.engine.test')")
    public ComputeEngineTestResponse test(@PathVariable UUID id) {
        return service.test(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "计算引擎：注册资源")
    @PostMapping("/{id}/actions/register")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    public ComputeEngineResponse register(@PathVariable UUID id) {
        return service.register(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "计算引擎：进入排空状态")
    @PostMapping("/{id}/actions/drain")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    public ComputeEngineResponse drain(@PathVariable UUID id) {
        return service.drain(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "计算引擎：停用引擎")
    @PostMapping("/{id}/actions/deactivate")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    public ComputeEngineResponse deactivate(
            @PathVariable UUID id,
            @RequestBody(required = false) DeactivateComputeEngineRequest request
    ) {
        return service.deactivate(id, request != null && request.force());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "计算引擎：解除关联")
    @PostMapping("/{id}/actions/detach")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    public ComputeEngineResponse detach(
            @PathVariable UUID id,
            @Valid @RequestBody DetachComputeEngineRequest request
    ) {
        return service.detach(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "计算引擎：删除")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('compute.engine.delete')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
