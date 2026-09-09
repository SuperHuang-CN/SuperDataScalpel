package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.task.service.TaskScheduleService;
import cn.superhuang.data.scalpel.business.task.web.request.CreateTaskScheduleRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateTaskScheduleRequest;
import cn.superhuang.data.scalpel.business.task.web.response.TaskScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "任务运行计划")
public class TaskScheduleResource {

    private final TaskScheduleService service;

    public TaskScheduleResource(TaskScheduleService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行计划")
    @GetMapping("/api/v1/tasks/{taskId}/schedules")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行计划")
    public List<TaskScheduleResponse> list(@PathVariable UUID taskId) {
        return service.list(taskId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建任务运行计划")
    @PostMapping("/api/v1/tasks/{taskId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "创建任务运行计划")
    public TaskScheduleResponse create(
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateTaskScheduleRequest request
    ) {
        return service.create(taskId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行计划详情")
    @GetMapping("/api/v1/task-schedules/{scheduleId}")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行计划详情")
    public TaskScheduleResponse get(@PathVariable UUID scheduleId) {
        return service.get(scheduleId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改任务运行计划")
    @PostMapping("/api/v1/task-schedules/{scheduleId}/actions/update")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "修改任务运行计划")
    public TaskScheduleResponse update(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody UpdateTaskScheduleRequest request
    ) {
        return service.update(scheduleId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "启用任务运行计划")
    @PostMapping("/api/v1/task-schedules/{scheduleId}/actions/enable")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "启用任务运行计划")
    public TaskScheduleResponse enable(@PathVariable UUID scheduleId) {
        return service.enable(scheduleId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "停用任务运行计划")
    @PostMapping("/api/v1/task-schedules/{scheduleId}/actions/disable")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "停用任务运行计划")
    public TaskScheduleResponse disable(@PathVariable UUID scheduleId) {
        return service.disable(scheduleId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除任务运行计划")
    @PostMapping("/api/v1/task-schedules/{scheduleId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task.delete')")
    @Operation(summary = "删除任务运行计划")
    public void delete(@PathVariable UUID scheduleId) {
        service.delete(scheduleId);
    }
}
