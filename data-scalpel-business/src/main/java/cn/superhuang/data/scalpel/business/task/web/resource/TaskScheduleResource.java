package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.task.service.TaskScheduleService;
import cn.superhuang.data.scalpel.business.task.web.request.CreateTaskScheduleRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateTaskScheduleRequest;
import cn.superhuang.data.scalpel.business.task.web.response.TaskScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "查询任务运行计划", description = "返回指定任务的全部定时运行计划和当前启停状态，不触发任务运行。")
    public List<TaskScheduleResponse> list(@Parameter(description = "任务 UUID。") @PathVariable UUID taskId) {
        return service.list(taskId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建任务运行计划")
    @PostMapping("/api/v1/tasks/{taskId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "创建任务运行计划", description = "为非实时任务创建一条初始为 DISABLED 的 Quartz Cron 计划，并同步到调度器；创建不会立即运行，任务发布后仍需显式启用。调度器同步失败时返回 503 且创建回滚。")
    public TaskScheduleResponse create(
            @Parameter(description = "任务 UUID。") @PathVariable UUID taskId,
            @Valid @RequestBody CreateTaskScheduleRequest request
    ) {
        return service.create(taskId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询任务运行计划详情")
    @GetMapping("/api/v1/task-schedules/{scheduleId}")
    @PreAuthorize("hasAuthority('task.view')")
    @Operation(summary = "查询任务运行计划详情", description = "读取一个运行计划的 Cron 表达式、时区、启停状态和最近调度信息。")
    public TaskScheduleResponse get(@Parameter(description = "任务运行计划 UUID。") @PathVariable UUID scheduleId) {
        return service.get(scheduleId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改任务运行计划")
    @PostMapping("/api/v1/task-schedules/{scheduleId}/actions/update")
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "修改任务运行计划", description = "整体替换非实时任务计划的名称、Cron、时区、错过策略和重叠策略，保留 ENABLED/DISABLED 状态并重新同步 Quartz；已经产生的运行不受影响。同步失败时返回 503 且修改回滚。")
    public TaskScheduleResponse update(
            @Parameter(description = "任务运行计划 UUID。") @PathVariable UUID scheduleId,
            @Valid @RequestBody UpdateTaskScheduleRequest request
    ) {
        return service.update(scheduleId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "启用任务运行计划")
    @PostMapping("/api/v1/task-schedules/{scheduleId}/actions/enable")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "启用任务运行计划", description = "仅当所属非实时任务为 PUBLISHED 时启用计划并同步 Quartz，使后续 Cron 触发可提交运行；错过时间按 misfirePolicy 由 Quartz 处理，本接口本身不直接创建运行。")
    public TaskScheduleResponse enable(@Parameter(description = "任务运行计划 UUID。") @PathVariable UUID scheduleId) {
        return service.enable(scheduleId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "停用任务运行计划")
    @PostMapping("/api/v1/task-schedules/{scheduleId}/actions/disable")
    @PreAuthorize("hasAuthority('task.publish')")
    @Operation(summary = "停用任务运行计划", description = "将计划置为 DISABLED 并从 Quartz 取消后续触发；不要求任务处于发布状态，不取消已提交或正在运行的任务。")
    public TaskScheduleResponse disable(@Parameter(description = "任务运行计划 UUID。") @PathVariable UUID scheduleId) {
        return service.disable(scheduleId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除任务运行计划")
    @PostMapping("/api/v1/task-schedules/{scheduleId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('task.delete')")
    @Operation(summary = "删除任务运行计划", description = "删除运行计划并停止后续自动触发；不会删除历史运行或取消已提交运行。")
    public void delete(@Parameter(description = "任务运行计划 UUID。") @PathVariable UUID scheduleId) {
        service.delete(scheduleId);
    }
}
