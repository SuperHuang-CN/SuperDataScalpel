package cn.superhuang.data.scalpel.dispatcher.web.resource;

import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionScope;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionSummaryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherRuntimeService;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import cn.superhuang.data.scalpel.dispatcher.web.response.DispatcherExecutionResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/task-executions")
@Validated
public class DispatcherExecutionResource {
    private final DispatcherTaskExecutionRepository repository;
    private final DispatcherRuntimeService runtimeService;

    public DispatcherExecutionResource(
            DispatcherTaskExecutionRepository repository,
            DispatcherRuntimeService runtimeService
    ) {
        this.repository = repository;
        this.runtimeService = runtimeService;
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
}
