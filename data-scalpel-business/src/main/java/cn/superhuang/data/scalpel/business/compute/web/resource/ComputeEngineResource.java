package cn.superhuang.data.scalpel.business.compute.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
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
import io.swagger.v3.oas.annotations.Operation;
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

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "计算引擎：查询列表",
            keywords = {"计算引擎", "Dispatcher", "Spark", "执行环境"})
    @GetMapping
    @PreAuthorize("hasAuthority('compute.engine.view')")
    @Operation(summary = "查询计算引擎", description = "分页查询 Admin 中保存的计算引擎配置及最近检查状态，支持通用 Search DSL；结果不是 Dispatcher 实时负载快照。")
    public PageResponse<ComputeEngineResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "计算引擎：查看详情",
            keywords = {"计算引擎", "Dispatcher", "配置", "状态"},
            relatedOperations = {"GET /api/v1/compute-engines/{id}/runtime-overview", "POST /api/v1/compute-engines/{id}/actions/test"})
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('compute.engine.view')")
    @Operation(summary = "查询计算引擎详情", description = "返回 Admin 保存的配置、注册生命周期和最近一次控制面检查结果；Dispatcher Token 只返回是否已配置。")
    public ComputeEngineResponse get(@Parameter(description = "计算引擎 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "计算引擎：查询运行概览",
            keywords = {"计算引擎", "Dispatcher", "运行态", "容量", "依赖"},
            prerequisites = "计算引擎存在，且其 Dispatcher 控制面可访问、身份和后端类型与 Admin 配置一致。",
            relatedOperations = {"GET /api/v1/compute-engines/{id}/executions", "POST /api/v1/compute-engines/{id}/actions/test"})
    @GetMapping("/{id}/runtime-overview")
    @PreAuthorize("hasAuthority('compute.engine.view')")
    @Operation(summary = "查询计算引擎运行概览", description = "实时调用 Dispatcher，返回其身份、注册状态、依赖就绪情况、准入容量与执行账本占用。无需计算引擎处于 ACTIVE；Admin 不保存快照，也不据此更新 healthState。控制面不可达、后端不匹配、已锁定的实例身份不匹配，或远端明确返回其他 engineId 时返回 502。")
    public ComputeEngineRuntimeOverviewResponse runtimeOverview(@Parameter(description = "计算引擎 UUID。") @PathVariable UUID id) {
        return runtimeService.overview(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "计算引擎：查询执行记录",
            keywords = {"计算引擎", "Dispatcher", "执行账本", "队列", "运行记录"},
            prerequisites = "计算引擎存在，且其 Dispatcher 控制面可访问、身份和后端类型与 Admin 配置一致。",
            relatedOperations = {"GET /api/v1/compute-engines/{id}/runtime-overview"})
    @GetMapping("/{id}/executions")
    @PreAuthorize("hasAuthority('compute.engine.view') and hasAuthority('task.view')")
    @Operation(summary = "查询计算引擎执行记录", description = "先读取一次运行概览并核对 Dispatcher 身份，再实时读取其持久化执行账本，保持远端分页和排序，并用 executionId 批量补充 Admin 任务和 TaskRun 信息。无需计算引擎处于 ACTIVE；远端记录尚未同步回 Admin或 runId 不一致时仍返回并标记 synchronized=false。")
    public PageResponse<ComputeEngineExecutionResponse> executions(
            @Parameter(description = "计算引擎 UUID。") @PathVariable UUID id,
            @Parameter(description = "执行范围：ACTIVE 返回全部未终止执行；QUEUED 只返回排队执行并计算队列位置；RECENT 返回最近结束的终态执行。") @RequestParam(defaultValue = "ACTIVE") DispatcherExecutionScope scope,
            @Parameter(description = "从 0 开始的页码。") @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @Parameter(description = "每页记录数，范围 1 到 100。")
            @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1)
            @jakarta.validation.constraints.Max(100) int size
    ) {
        return runtimeService.executions(id, scope, page, size);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "计算引擎：创建",
            keywords = {"计算引擎", "Dispatcher", "新增", "配置"},
            prerequisites = "名称、命令 Topic 和 Runner 事件 Topic 未被占用；Admin 已监听指定管理事件 Topic。",
            relatedOperations = {"POST /api/v1/compute-engines/{id}/actions/test", "POST /api/v1/compute-engines/{id}/actions/register"})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('compute.engine.create')")
    @Operation(summary = "创建计算引擎", description = "只在 Admin 保存 Dispatcher 地址、加密 Token、Topic、准入限制和资源策略，返回 201；不会连接或注册 Dispatcher，新记录初始状态为 CREATED/UNKNOWN。")
    public ComputeEngineResponse create(@Valid @RequestBody CreateComputeEngineRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "计算引擎：修改",
            keywords = {"计算引擎", "Dispatcher", "修改配置"},
            prerequisites = "计算引擎处于 CREATED、INACTIVE、ERROR 或 DETACHED；ACTIVE、DRAINING 应使用重新配置接口。",
            relatedOperations = {"POST /api/v1/compute-engines/{id}/actions/reconfigure"})
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('compute.engine.update')")
    @Operation(summary = "修改非活动计算引擎配置", description = "完整替换可编辑配置；空白 Token 和空 resourcePolicy 分别保留现有值。活动、排空或注册中的引擎返回冲突；新资源上限低于已绑定 Spark JAR 任务申请时整次拒绝。")
    public ComputeEngineResponse update(@Parameter(description = "计算引擎 UUID。") @PathVariable UUID id, @Valid @RequestBody UpdateComputeEngineRequest request) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "计算引擎：应用配置并重新注册",
            keywords = {"计算引擎", "Dispatcher", "在线重配置", "重新注册"},
            prerequisites = "计算引擎处于 ACTIVE 或 DRAINING；候选 Dispatcher 可访问且后端类型匹配。",
            relatedOperations = {"POST /api/v1/compute-engines/{id}/actions/update", "POST /api/v1/compute-engines/{id}/actions/drain"})
    @PostMapping("/{id}/actions/reconfigure")
    @PreAuthorize("hasAuthority('compute.engine.update') and hasAuthority('compute.engine.manage')")
    @Operation(summary = "应用配置并重新注册计算引擎", description = "请求规范化后与当前配置完全相同时直接返回，不调用 Dispatcher。配置有变化时先用候选地址、Token 核对实例信息和后端类型；ACTIVE 时排空旧 Dispatcher，再非强制反注册、保存新配置并注册。若仍有活动执行则返回 409，保持 DRAINING 且不保存候选配置；若新配置已保存但重新注册失败，则保留新配置并进入 ERROR。")
    public ComputeEngineResponse reconfigure(
            @Parameter(description = "ACTIVE 或 DRAINING 的计算引擎 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateComputeEngineRequest request
    ) {
        return service.reconfigure(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "计算引擎：测试 Dispatcher",
            keywords = {"计算引擎", "Dispatcher", "连接测试", "健康检查"},
            prerequisites = "计算引擎已保存有效的 Dispatcher 地址和访问 Token。",
            relatedOperations = {"POST /api/v1/compute-engines/{id}/actions/register", "GET /api/v1/compute-engines/{id}/runtime-overview"})
    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('compute.engine.test')")
    @Operation(summary = "测试 Dispatcher 控制面", description = "使用已保存地址和 Token 调用 Dispatcher /info，只核对后端类型及已知实例身份。成功更新 healthState=UP、检查时间和实例身份，但不改变注册状态、不提交测试任务；返回的 dependencies 即使包含 DOWN 也不使本接口失败。失败在配置未并发变化时记录 healthState=DOWN 和安全错误摘要。")
    public ComputeEngineTestResponse test(@Parameter(description = "计算引擎 UUID。") @PathVariable UUID id) {
        return service.test(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "计算引擎：注册 Dispatcher",
            keywords = {"计算引擎", "Dispatcher", "注册", "激活"},
            prerequisites = "计算引擎不处于 ACTIVE、DRAINING 或 REGISTERING；Dispatcher 可访问、依赖就绪且后端类型匹配。DETACHED 重新注册前应确认原 Dispatcher 已永久停止。",
            relatedOperations = {"POST /api/v1/compute-engines/{id}/actions/test", "POST /api/v1/compute-engines/{id}/actions/drain"})
    @PostMapping("/{id}/actions/register")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    @Operation(summary = "向 Dispatcher 注册计算引擎", description = "先提交本地 REGISTERING 状态，再核对 Dispatcher 身份和后端，发送 Topic、准入策略及资源策略并请求激活；远端必须原样确认引擎 UUID、实例身份、后端、全部 Topic、准入和资源策略。成功进入 ACTIVE/UP，失败进入 ERROR/DOWN 并记录安全错误；该多步操作不是单一数据库事务。")
    public ComputeEngineResponse register(@Parameter(description = "计算引擎 UUID。") @PathVariable UUID id) {
        return service.register(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "计算引擎：进入排空状态",
            keywords = {"计算引擎", "Dispatcher", "Drain", "排空"},
            prerequisites = "计算引擎处于 ACTIVE，且远端注册的引擎身份、Topic 和准入策略与 Admin 配置一致。",
            relatedOperations = {"POST /api/v1/compute-engines/{id}/actions/deactivate"})
    @PostMapping("/{id}/actions/drain")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    @Operation(summary = "排空计算引擎", description = "先核对远端注册归属和完整配置，再使 Dispatcher 进入 DRAINING：停止接收新执行，但继续监管已有执行并接受取消。成功后 Admin 记录 DRAINING/UP；不会自动等待任务结束或反注册。远端调用失败会把健康状态记为 DOWN，但保留原注册状态。")
    public ComputeEngineResponse drain(@Parameter(description = "ACTIVE 计算引擎 UUID。") @PathVariable UUID id) {
        return service.drain(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "计算引擎：反注册 Dispatcher",
            keywords = {"计算引擎", "Dispatcher", "停用", "反注册", "取消任务"},
            prerequisites = "计算引擎处于 ACTIVE、DRAINING 或 ERROR，Dispatcher 可访问且远端注册归属与配置一致。",
            relatedOperations = {"POST /api/v1/compute-engines/{id}/actions/drain", "POST /api/v1/compute-engines/{id}/actions/detach"})
    @PostMapping("/{id}/actions/deactivate")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    @Operation(summary = "从 Dispatcher 反注册计算引擎", description = "先核对远端注册归属和完整配置。force=false 时仅在没有排队或活动执行时反注册；force=true 时由 Dispatcher 请求取消这些执行并等待状态收敛。只有远端明确返回 INACTIVE 且配置一致时 Admin 才记为 INACTIVE；冲突或等待未收敛时不自动离线解绑，需检查状态后重试。")
    public ComputeEngineResponse deactivate(
            @Parameter(description = "ACTIVE、DRAINING 或 ERROR 的计算引擎 UUID。") @PathVariable UUID id,
            @RequestBody(required = false) DeactivateComputeEngineRequest request
    ) {
        return service.deactivate(id, request != null && request.force());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "计算引擎：离线解除绑定",
            keywords = {"计算引擎", "Dispatcher", "离线解绑", "灾难恢复"},
            prerequisites = "计算引擎处于 ACTIVE、DRAINING 或 ERROR；Dispatcher 因连接拒绝或超时等传输故障不可达；没有关联的活动 TaskRun 和未发布执行消息。",
            relatedOperations = {"POST /api/v1/compute-engines/{id}/actions/deactivate"})
    @PostMapping("/{id}/actions/detach")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    @Operation(summary = "离线解除计算引擎绑定", description = "仅用于 Dispatcher 主机永久损坏且控制面传输不可达的灾难恢复。校验确认名称、活动 TaskRun 和执行 Outbox 后只修改 Admin 为 DETACHED；不会停止远端进程、取消远端任务或删除历史。")
    public ComputeEngineResponse detach(
            @Parameter(description = "需要离线解除绑定的计算引擎 UUID。") @PathVariable UUID id,
            @Valid @RequestBody DetachComputeEngineRequest request
    ) {
        return service.detach(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "计算引擎：删除",
            keywords = {"计算引擎", "删除"},
            prerequisites = "计算引擎不处于 ACTIVE、DRAINING 或 REGISTERING，且没有任何任务引用。")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('compute.engine.delete')")
    @Operation(summary = "删除计算引擎", description = "删除未活动且未被任务引用的计算引擎配置，成功返回 204；不会删除任务、TaskRun 或 Dispatcher 侧数据。")
    public void delete(@Parameter(description = "计算引擎 UUID。") @PathVariable UUID id) {
        service.delete(id);
    }
}
