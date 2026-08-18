package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskSchedule;
import cn.superhuang.data.scalpel.business.task.domain.TaskScheduleStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskScheduleRepository;
import cn.superhuang.data.scalpel.business.task.web.request.CreateTaskScheduleRequest;
import cn.superhuang.data.scalpel.business.task.web.request.UpdateTaskScheduleRequest;
import cn.superhuang.data.scalpel.business.task.web.response.TaskScheduleResponse;
import org.quartz.CronExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TaskScheduleService {

    private final TaskScheduleRepository scheduleRepository;
    private final DataTaskRepository taskRepository;
    private final TaskQuartzScheduler quartzScheduler;

    public TaskScheduleService(
            TaskScheduleRepository scheduleRepository,
            DataTaskRepository taskRepository,
            TaskQuartzScheduler quartzScheduler
    ) {
        this.scheduleRepository = scheduleRepository;
        this.taskRepository = taskRepository;
        this.quartzScheduler = quartzScheduler;
    }

    @Transactional(readOnly = true)
    public List<TaskScheduleResponse> list(UUID taskId) {
        DataTask task = requireTask(taskId);
        return scheduleRepository.findAllByTaskIdOrderByNameAsc(taskId).stream()
                .map(schedule -> response(schedule, task.getStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskScheduleResponse get(UUID scheduleId) {
        TaskSchedule schedule = requireSchedule(scheduleId);
        DataTask task = requireTask(schedule.getTaskId());
        return response(schedule, task.getStatus());
    }

    @Transactional
    public TaskScheduleResponse create(UUID taskId, CreateTaskScheduleRequest request) {
        DataTask task = requireTask(taskId);
        requireSchedulableTask(task);
        validateCron(request.cronExpression());
        String name = normalizedName(request.name());
        if (scheduleRepository.existsByTaskIdAndName(taskId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务下已存在同名运行计划");
        }
        TaskSchedule schedule;
        try {
            schedule = TaskSchedule.create(
                    taskId, name, request.cronExpression(), request.zoneId(),
                    request.misfirePolicy(), request.overlapPolicy()
            );
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidRequest(exception);
        }
        TaskSchedule saved = scheduleRepository.saveAndFlush(schedule);
        synchronize(saved, task.getStatus());
        return response(saved, task.getStatus());
    }

    @Transactional
    public TaskScheduleResponse update(UUID scheduleId, UpdateTaskScheduleRequest request) {
        TaskSchedule schedule = requireScheduleForUpdate(scheduleId);
        DataTask task = requireTask(schedule.getTaskId());
        requireSchedulableTask(task);
        validateCron(request.cronExpression());
        String name = normalizedName(request.name());
        if (scheduleRepository.existsByTaskIdAndNameAndIdNot(schedule.getTaskId(), name, scheduleId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务下已存在同名运行计划");
        }
        try {
            schedule.update(
                    name, request.cronExpression(), request.zoneId(),
                    request.misfirePolicy(), request.overlapPolicy()
            );
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw invalidRequest(exception);
        }
        TaskSchedule saved = scheduleRepository.saveAndFlush(schedule);
        synchronize(saved, task.getStatus());
        return response(saved, task.getStatus());
    }

    @Transactional
    public TaskScheduleResponse enable(UUID scheduleId) {
        TaskSchedule schedule = requireScheduleForUpdate(scheduleId);
        DataTask task = requireTask(schedule.getTaskId());
        requireSchedulableTask(task);
        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布任务可以启用运行计划");
        }
        schedule.enable();
        TaskSchedule saved = scheduleRepository.saveAndFlush(schedule);
        synchronize(saved, task.getStatus());
        return response(saved, task.getStatus());
    }

    @Transactional
    public TaskScheduleResponse disable(UUID scheduleId) {
        TaskSchedule schedule = requireScheduleForUpdate(scheduleId);
        DataTask task = requireTask(schedule.getTaskId());
        schedule.disable();
        TaskSchedule saved = scheduleRepository.saveAndFlush(schedule);
        synchronize(saved, task.getStatus());
        return response(saved, task.getStatus());
    }

    @Transactional
    public void delete(UUID scheduleId) {
        TaskSchedule schedule = requireScheduleForUpdate(scheduleId);
        scheduleRepository.delete(schedule);
        scheduleRepository.flush();
        deleteQuartzSchedule(scheduleId);
    }

    @Transactional(readOnly = true)
    public void pauseForTask(UUID taskId) {
        scheduleRepository.findAllByTaskIdOrderByNameAsc(taskId).forEach(schedule -> {
            try {
                quartzScheduler.pause(schedule.getId());
            } catch (TaskScheduleSynchronizationException exception) {
                throw schedulerUnavailable(exception);
            }
        });
    }

    @Transactional(readOnly = true)
    public void resumeForTask(UUID taskId) {
        scheduleRepository.findAllByTaskIdOrderByNameAsc(taskId).forEach(schedule -> synchronize(schedule, TaskStatus.PUBLISHED));
    }

    @Transactional
    public void deleteForTask(UUID taskId) {
        List<UUID> scheduleIds = scheduleRepository.findAllByTaskIdOrderByNameAsc(taskId).stream()
                .map(TaskSchedule::getId).toList();
        scheduleRepository.deleteAllByTaskId(taskId);
        scheduleRepository.flush();
        scheduleIds.forEach(this::deleteQuartzSchedule);
    }

    private TaskScheduleResponse response(TaskSchedule schedule, TaskStatus taskStatus) {
        Instant nextFireAt = schedule.getStatus() == TaskScheduleStatus.ENABLED && taskStatus == TaskStatus.PUBLISHED
                ? nextFireAt(schedule.getId())
                : null;
        return TaskScheduleResponse.from(schedule, nextFireAt);
    }

    private void synchronize(TaskSchedule schedule, TaskStatus taskStatus) {
        try {
            quartzScheduler.synchronize(schedule, taskStatus == TaskStatus.PUBLISHED);
        } catch (TaskScheduleSynchronizationException exception) {
            throw schedulerUnavailable(exception);
        }
    }

    private Instant nextFireAt(UUID scheduleId) {
        try {
            return quartzScheduler.nextFireAt(scheduleId);
        } catch (TaskScheduleSynchronizationException exception) {
            throw schedulerUnavailable(exception);
        }
    }

    private void deleteQuartzSchedule(UUID scheduleId) {
        try {
            quartzScheduler.delete(scheduleId);
        } catch (TaskScheduleSynchronizationException exception) {
            throw schedulerUnavailable(exception);
        }
    }

    private DataTask requireTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private static void requireSchedulableTask(DataTask task) {
        if (task.getType().isStreaming()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark 实时任务持续运行，不支持定时计划");
        }
    }

    private TaskSchedule requireSchedule(UUID scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "运行计划不存在"));
    }

    private TaskSchedule requireScheduleForUpdate(UUID scheduleId) {
        return scheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "运行计划不存在"));
    }

    private static void validateCron(String expression) {
        if (expression == null || !CronExpression.isValidExpression(expression.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quartz Cron 表达式无效");
        }
    }

    private static String normalizedName(String name) {
        return name == null ? null : name.trim();
    }

    private static ResponseStatusException invalidRequest(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    private static ResponseStatusException schedulerUnavailable(TaskScheduleSynchronizationException exception) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), exception);
    }
}
