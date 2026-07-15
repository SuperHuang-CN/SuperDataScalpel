package cn.superhuang.data.scalpel.business.task.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** First release deliberately does not resume JDBC statements after a process restart. */
@Component
public class TaskRunRecovery {

    private final TaskRunService taskRunService;

    public TaskRunRecovery(TaskRunService taskRunService) {
        this.taskRunService = taskRunService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedRunsFailed() {
        taskRunService.markInterruptedRunsFailed();
    }
}
