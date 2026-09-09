package cn.superhuang.data.scalpel.business.operations.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.operations.service.RuntimeWorkbenchService;
import cn.superhuang.data.scalpel.business.operations.web.request.RuntimeRunFilter;
import cn.superhuang.data.scalpel.business.operations.web.response.*;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@io.swagger.v3.oas.annotations.tags.Tag(name = "运行工作台")
@RestController
@PreAuthorize("isAuthenticated()")
public class RuntimeWorkbenchResource {
    private final RuntimeWorkbenchService service;
    public RuntimeWorkbenchResource(RuntimeWorkbenchService service) { this.service = service; }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "运行工作台：查询概览")
    @GetMapping("/api/v1/operations/overview")
    public RuntimeOverviewResponse overview(@RequestParam(required=false) Instant from, @RequestParam(required=false) Instant to) { return service.overview(from, to); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "运行工作台：查询运行记录")
    @GetMapping("/api/v1/task-runs")
    @PreAuthorize("hasAuthority('task.view')")
    public PageResponse<RuntimeRunResponse> runs(@ParameterObject @ModelAttribute SearchRequest request, @Valid @ParameterObject @ModelAttribute RuntimeRunFilter filter) { return service.runs(request, filter); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "运行工作台：查询实时运行状态")
    @GetMapping("/api/v1/operations/streaming-deployments")
    @PreAuthorize("hasAuthority('task.view')")
    public PageResponse<RuntimeStreamingResponse> streaming(@ParameterObject @ModelAttribute SearchRequest request) { return service.streaming(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "运行工作台：查询引擎")
    @GetMapping("/api/v1/operations/compute-engines")
    @PreAuthorize("hasAuthority('compute.engine.view')")
    public PageResponse<RuntimeEngineResponse> engines(@ParameterObject @ModelAttribute SearchRequest request) { return service.engines(request); }
}
