package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskSchedule;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class TaskScheduleReconciler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskScheduleReconciler.class);

    private final TaskScheduleRepository scheduleRepository;
    private final DataTaskRepository taskRepository;
    private final TaskQuartzScheduler quartzScheduler;

    public TaskScheduleReconciler(
            TaskScheduleRepository scheduleRepository,
            DataTaskRepository taskRepository,
            TaskQuartzScheduler quartzScheduler
    ) {
        this.scheduleRepository = scheduleRepository;
        this.taskRepository = taskRepository;
        this.quartzScheduler = quartzScheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcile() {
        try {
            var schedules = scheduleRepository.findAll();
            Map<UUID, DataTask> tasks = taskRepository.findAllById(
                    schedules.stream().map(TaskSchedule::getTaskId).collect(Collectors.toSet())
            ).stream().collect(Collectors.toMap(DataTask::getId, Function.identity()));
            for (TaskSchedule schedule : schedules) {
                DataTask task = tasks.get(schedule.getTaskId());
                quartzScheduler.synchronize(schedule, task != null && task.getStatus() == TaskStatus.PUBLISHED);
            }
            Set<UUID> scheduleIds = schedules.stream().map(TaskSchedule::getId).collect(Collectors.toSet());
            quartzScheduler.deleteOrphans(scheduleIds);
        } catch (RuntimeException exception) {
            LOGGER.error("任务计划启动对账失败", exception);
        }
    }
}
