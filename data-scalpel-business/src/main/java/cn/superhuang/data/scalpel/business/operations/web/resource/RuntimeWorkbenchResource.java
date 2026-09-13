package cn.superhuang.data.scalpel.business.operations.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "查询运行工作台概览", description = "在当前用户可见的任务和引擎范围内汇总排队、运行、最近完成、失败、质量不通过、未关闭告警及需要关注对象；默认统计时间窗由服务端确定。")
    @GetMapping("/api/v1/operations/overview")
    public RuntimeOverviewResponse overview(@Parameter(description = "统计时间范围起点，包含该时刻。") @RequestParam(required=false) Instant from, @Parameter(description = "统计时间范围终点，不包含该时刻。") @RequestParam(required=false) Instant to) { return service.overview(from, to); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "运行工作台：查询运行记录")
    @Operation(summary = "查询全局任务运行", description = "按通用 Search DSL 与运行筛选条件分页查询 Local SQL、Canvas、Spark JAR、模型质检及工作流运行，并返回允许的取消、停止或终止能力。")
    @GetMapping("/api/v1/task-runs")
    @PreAuthorize("hasAuthority('task.view')")
    public PageResponse<RuntimeRunResponse> runs(@ParameterObject @ModelAttribute SearchRequest request, @Valid @ParameterObject @ModelAttribute RuntimeRunFilter filter) { return service.runs(request, filter); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "运行工作台：查询实时运行状态")
    @Operation(summary = "查询实时任务部署", description = "按通用 Search DSL 分页查询实时 Canvas 和实时 JAR 的期望状态、当前状态、运行标识、进度及最近错误；不会触发刷新或状态变更。")
    @GetMapping("/api/v1/operations/streaming-deployments")
    @PreAuthorize("hasAuthority('task.view')")
    public PageResponse<RuntimeStreamingResponse> streaming(@ParameterObject @ModelAttribute SearchRequest request) { return service.streaming(request); }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "运行工作台：查询引擎")
    @Operation(summary = "查询运行引擎状态", description = "按通用 Search DSL 分页查询当前用户可见计算引擎的注册、健康、容量、运行负载和告警摘要，用于全局运行诊断。")
    @GetMapping("/api/v1/operations/compute-engines")
    @PreAuthorize("hasAuthority('compute.engine.view')")
    public PageResponse<RuntimeEngineResponse> engines(@ParameterObject @ModelAttribute SearchRequest request) { return service.engines(request); }
}
