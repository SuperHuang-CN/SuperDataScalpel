package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.TaskMisfirePolicy;
import cn.superhuang.data.scalpel.business.task.domain.TaskSchedule;
import cn.superhuang.data.scalpel.business.task.domain.TaskScheduleStatus;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TaskQuartzScheduler {

    public static final String SCHEDULE_ID_KEY = "scheduleId";
    public static final String GROUP = "DATA_SCALPEL_TASK_SCHEDULE";

    private final Scheduler scheduler;

    public TaskQuartzScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void synchronize(TaskSchedule schedule, boolean taskPublished) {
        try {
            JobKey jobKey = jobKey(schedule.getId());
            TriggerKey triggerKey = triggerKey(schedule.getId());
            JobDetail job = JobBuilder.newJob(TaskScheduleJob.class)
                    .withIdentity(jobKey)
                    .usingJobData(SCHEDULE_ID_KEY, schedule.getId().toString())
                    .storeDurably()
                    .build();
            scheduler.addJob(job, true, true);

            CronScheduleBuilder cron = CronScheduleBuilder.cronSchedule(schedule.getCronExpression())
                    .inTimeZone(TimeZone.getTimeZone(schedule.getZoneId()));
            cron = schedule.getMisfirePolicy() == TaskMisfirePolicy.FIRE_ONCE_NOW
                    ? cron.withMisfireHandlingInstructionFireAndProceed()
                    : cron.withMisfireHandlingInstructionDoNothing();
            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .forJob(jobKey)
                    .withSchedule(cron)
                    .build();

            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger);
            } else {
                scheduler.scheduleJob(trigger);
            }
            if (taskPublished && schedule.getStatus() == TaskScheduleStatus.ENABLED) {
                scheduler.resumeTrigger(triggerKey);
            } else {
                scheduler.pauseTrigger(triggerKey);
            }
        } catch (SchedulerException | RuntimeException exception) {
            throw synchronizationFailure("同步任务计划失败", exception);
        }
    }

    public void pause(UUID scheduleId) {
        try {
            TriggerKey triggerKey = triggerKey(scheduleId);
            if (scheduler.checkExists(triggerKey)) {
                scheduler.pauseTrigger(triggerKey);
            }
        } catch (SchedulerException exception) {
            throw synchronizationFailure("暂停任务计划失败", exception);
        }
    }

    public void delete(UUID scheduleId) {
        try {
            scheduler.deleteJob(jobKey(scheduleId));
        } catch (SchedulerException exception) {
            throw synchronizationFailure("删除任务计划失败", exception);
        }
    }

    public Instant nextFireAt(UUID scheduleId) {
        try {
            Trigger trigger = scheduler.getTrigger(triggerKey(scheduleId));
            Date nextFireTime = trigger == null ? null : trigger.getNextFireTime();
            return nextFireTime == null ? null : nextFireTime.toInstant();
        } catch (SchedulerException exception) {
            throw synchronizationFailure("读取任务计划状态失败", exception);
        }
    }

    public void deleteOrphans(Set<UUID> scheduleIds) {
        try {
            Set<JobKey> expected = scheduleIds.stream().map(TaskQuartzScheduler::jobKey).collect(Collectors.toSet());
            for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP))) {
                if (!expected.contains(jobKey)) {
                    scheduler.deleteJob(jobKey);
                }
            }
        } catch (SchedulerException exception) {
            throw synchronizationFailure("清理孤立任务计划失败", exception);
        }
    }

    public static JobKey jobKey(UUID scheduleId) {
        return JobKey.jobKey(scheduleId.toString(), GROUP);
    }

    public static TriggerKey triggerKey(UUID scheduleId) {
        return TriggerKey.triggerKey(scheduleId.toString(), GROUP);
    }

    private static TaskScheduleSynchronizationException synchronizationFailure(String message, Exception cause) {
        return new TaskScheduleSynchronizationException(message, cause);
    }
}
