package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.task.service.TaskCompilationService;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskCompilationCancellationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/task-compilations")
@Tag(name = "任务管理-任务定义编译")
public class TaskCompilationResource {

    private final TaskCompilationService service;

    public TaskCompilationResource(TaskCompilationService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "编译任务定义")
    @PostMapping
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "编译 Canvas 任务定义", description = "同步将完整 CANVAS 定义和调用方提供的元数据快照发送给 Task Engine，在受控 Spark 编译会话中执行协议、节点、Schema 和血缘分析；当前不支持其他任务类型。不会保存业务任务定义或提交正式运行，成功 HTTP 响应仍需检查 valid 和问题列表。")
    public TaskCompilationResponse compile(@Valid @RequestBody TaskCompilationRequest request) {
        return service.compile(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "取消活动的任务定义编译")
    @PostMapping("/{requestId}/actions/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "取消活动的任务定义编译", description = "按 requestId 请求取消 Task Engine 内仍登记为活动的编译；存在时返回 202 和 CANCEL_REQUESTED，不存在或已完成则返回 404。取消是协作式中断，正在执行的 Spark 操作可能稍后才停止。")
    public TaskCompilationCancellationResponse cancel(@Parameter(description = "编译请求 UUID；只能取消仍处于活动状态的请求。") @PathVariable UUID requestId) {
        return service.cancel(requestId);
    }
}
