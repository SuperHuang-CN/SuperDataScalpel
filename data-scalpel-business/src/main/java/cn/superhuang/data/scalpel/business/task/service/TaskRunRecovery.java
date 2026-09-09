package cn.superhuang.data.scalpel.business.task.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** First release deliberately does not resume JDBC statements after a process restart. */
@Component
public class TaskRunRecovery {

    private final TaskRunService taskRunService;
    private final WorkflowRunService workflows;
    private final org.quartz.Scheduler scheduler;

    public TaskRunRecovery(TaskRunService taskRunService, WorkflowRunService workflows, org.quartz.Scheduler scheduler) {
        this.workflows = workflows;
        this.scheduler = scheduler;
        this.taskRunService = taskRunService;
    }

    @org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedRunsFailed() {
        taskRunService.markInterruptedRunsFailed();
        workflows.recoverInterrupted();
        workflows.ready();
        try { scheduler.start(); }
        catch (org.quartz.SchedulerException exception) { throw new IllegalStateException("无法启动任务定时调度", exception); }
    }
}
