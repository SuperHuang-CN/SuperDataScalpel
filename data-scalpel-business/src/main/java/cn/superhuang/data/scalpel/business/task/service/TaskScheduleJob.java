package cn.superhuang.data.scalpel.business.task.service;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class TaskScheduleJob extends QuartzJobBean {

    private final TaskRunService taskRunService;

    public TaskScheduleJob(TaskRunService taskRunService) {
        this.taskRunService = taskRunService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        try {
            UUID scheduleId = UUID.fromString(context.getMergedJobDataMap().getString(TaskQuartzScheduler.SCHEDULE_ID_KEY));
            Date scheduledFireTime = context.getScheduledFireTime();
            if (scheduledFireTime == null) {
                throw new IllegalStateException("Quartz 未提供计划触发时间");
            }
            taskRunService.runScheduled(scheduleId, Instant.ofEpochMilli(scheduledFireTime.getTime()));
        } catch (RuntimeException exception) {
            throw new JobExecutionException("定时任务触发失败", exception, false);
        }
    }
}
