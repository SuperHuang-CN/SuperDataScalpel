package cn.superhuang.data.scalpel.dispatcher.web.resource;

import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionScope;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionSummaryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherRuntimeService;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import cn.superhuang.data.scalpel.dispatcher.backend.ExternalExecutionHandle;
import cn.superhuang.data.scalpel.dispatcher.backend.TaskExecutionBackend;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import cn.superhuang.data.scalpel.dispatcher.web.response.DispatcherExecutionResponse;
import cn.superhuang.data.scalpel.dispatcher.web.response.DispatcherExecutionLogResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/task-executions")
@Validated
public class DispatcherExecutionResource {
    private final DispatcherTaskExecutionRepository repository;
    private final DispatcherRuntimeService runtimeService;
    private final TaskExecutionBackend backend;

    public DispatcherExecutionResource(
            DispatcherTaskExecutionRepository repository,
            DispatcherRuntimeService runtimeService,
            TaskExecutionBackend backend
    ) {
        this.repository = repository;
        this.runtimeService = runtimeService;
        this.backend = backend;
    }

    @GetMapping
    public PageResponse<DispatcherExecutionSummaryResponse> search(
            @RequestParam(defaultValue = "ACTIVE") DispatcherExecutionScope scope,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return runtimeService.executions(scope, page, size);
    }

    @GetMapping("/{executionId}")
    public DispatcherExecutionResponse get(@PathVariable UUID executionId) {
        return repository.findByExecutionId(executionId).map(DispatcherExecutionResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "执行不存在"));
    }

    @GetMapping("/{executionId}/logs")
    public DispatcherExecutionLogResponse logs(
            @PathVariable UUID executionId,
            @RequestParam @Min(1) int attempt
    ) {
        var execution = repository.findByExecutionIdAndAttempt(executionId, attempt)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "执行不存在"));
        if (execution.getBackendType() != backend.type()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "执行后端与当前 Dispatcher 不一致");
        }
        if (execution.getExternalExecutionId() == null || execution.getExternalExecutionId().isBlank()) {
            return response(execution, DispatcherExecutionLogResponse.Status.WAITING, null, 0, false,
                    "等待任务启动并产生日志");
        }
        var identity = new ExecutionIdentity(execution.getEngineId(), execution.getExecutionId(),
                execution.getRunId(), execution.getAttempt());
        var handle = new ExternalExecutionHandle(execution.getBackendType(), execution.getExternalExecutionId(),
                execution.getTrackingUrl());
        try {
            backend.inspect(handle, identity);
            var log = backend.collectRecentLog(handle);
            String content = new String(log.content(), StandardCharsets.UTF_8);
            return response(execution, DispatcherExecutionLogResponse.Status.AVAILABLE, content,
                    content.getBytes(StandardCharsets.UTF_8).length, log.truncated(), null);
        } catch (BackendException exception) {
            if ("YARN_LOG_UNAVAILABLE".equals(exception.code())) {
                return response(execution, DispatcherExecutionLogResponse.Status.UNAVAILABLE, null, 0, false,
                        "集群尚未提供运行日志");
            }
            if ("EXTERNAL_EXECUTION_NOT_FOUND".equals(exception.code()) && execution.getState().terminal()) {
                return response(execution, DispatcherExecutionLogResponse.Status.UNAVAILABLE, null, 0, false,
                        "外部执行已清理，等待最终日志归档");
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "暂时无法读取运行日志", exception);
        }
    }

    private static DispatcherExecutionLogResponse response(
            cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution execution,
            DispatcherExecutionLogResponse.Status status,
            String content,
            int sizeBytes,
            boolean truncated,
            String message
    ) {
        return new DispatcherExecutionLogResponse(execution.getEngineId(), execution.getExecutionId(),
                execution.getRunId(), execution.getAttempt(), status, content, Instant.now(), sizeBytes,
                truncated, message);
    }
}
