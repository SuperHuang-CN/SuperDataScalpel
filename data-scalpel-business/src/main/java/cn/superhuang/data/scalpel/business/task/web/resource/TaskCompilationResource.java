package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.task.service.TaskCompilationService;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskCompilationCancellationResponse;
import io.swagger.v3.oas.annotations.Operation;
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

    @PostMapping
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "编译任务定义")
    public TaskCompilationResponse compile(@Valid @RequestBody TaskCompilationRequest request) {
        return service.compile(request);
    }

    @PostMapping("/{requestId}/actions/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "取消活动的任务定义编译")
    public TaskCompilationCancellationResponse cancel(@PathVariable UUID requestId) {
        return service.cancel(requestId);
    }
}
