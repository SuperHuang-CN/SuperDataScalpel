package cn.superhuang.data.scalpel.business.task.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WorkflowRunCoordinator {
    private static final Logger log = LoggerFactory.getLogger(WorkflowRunCoordinator.class);
    private final WorkflowRunService workflows;
    private final TaskRunService runs;
    private final Set<UUID> advancing = ConcurrentHashMap.newKeySet();
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    public WorkflowRunCoordinator(WorkflowRunService workflows, TaskRunService runs) {
        this.workflows = workflows; this.runs = runs;
        executor.setCorePoolSize(4); executor.setMaxPoolSize(4); executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("workflow-advance-"); executor.initialize();
    }
    @Scheduled(fixedDelayString = "${data-scalpel.task.workflow.poll-interval:1s}")
    public void tick() {
        for (UUID id : workflows.candidates()) {
            if (!advancing.add(id)) continue;
            try { executor.execute(() -> advance(id)); }
            catch (org.springframework.core.task.TaskRejectedException exception) { advancing.remove(id); }
        }
    }
    private void advance(UUID id) {
        try {
            for (var node : workflows.advance(id)) {
                try { runs.submitWorkflowChild(UUID.fromString(node.taskId()), id, node.id()); }
                catch (RuntimeException exception) {
                    log.warn("Workflow child submission failed: parentRunId={} nodeId={}", id, node.id(), exception);
                    workflows.submissionFailed(id, node.id(), exception);
                    break;
                }
            }
        } catch (RuntimeException exception) {
            log.error("Workflow advancement failed: parentRunId={}", id, exception);
            try { workflows.advancementFailed(id); }
            catch (RuntimeException persistenceFailure) {
                log.error("Could not record workflow failure: parentRunId={}", id, persistenceFailure);
            }
        } finally { advancing.remove(id); }
    }
    @PreDestroy public void close() { executor.shutdown(); }
}
