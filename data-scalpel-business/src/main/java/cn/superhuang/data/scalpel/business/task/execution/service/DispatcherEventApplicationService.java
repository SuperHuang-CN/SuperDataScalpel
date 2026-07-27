package cn.superhuang.data.scalpel.business.task.execution.service;

import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.execution.domain.DispatcherEventInboxMessage;
import cn.superhuang.data.scalpel.business.task.execution.repository.DispatcherEventInboxRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingDeploymentRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingQueryRepository;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingDeployment;
import cn.superhuang.data.scalpel.business.task.domain.TaskStreamingQuery;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class DispatcherEventApplicationService {

    public enum Outcome { APPLIED, DUPLICATE, STALE, REJECTED }

    private final DispatcherEventInboxRepository inboxRepository;
    private final TaskRunRepository runRepository;
    private final TaskStreamingDeploymentRepository deploymentRepository;
    private final TaskStreamingQueryRepository queryRepository;

    public DispatcherEventApplicationService(
            DispatcherEventInboxRepository inboxRepository,
            TaskRunRepository runRepository,
            TaskStreamingDeploymentRepository deploymentRepository,
            TaskStreamingQueryRepository queryRepository
    ) {
        this.inboxRepository = inboxRepository;
        this.runRepository = runRepository;
        this.deploymentRepository = deploymentRepository;
        this.queryRepository = queryRepository;
    }

    @Transactional
    public Outcome accept(DispatcherExecutionEvent event) {
        // Lock the run before the Inbox existence check. Events for the same run are
        // therefore serialized, which closes the common exists-then-insert race while
        // preserving the transaction that atomically updates Inbox and TaskRun.
        TaskRun run = runRepository.findByExecutionRunIdForUpdate(event.runId()).orElse(null);
        if (inboxRepository.existsByMessageId(event.messageId())) {
            return Outcome.DUPLICATE;
        }
        DispatcherEventInboxMessage inbox = inboxRepository.saveAndFlush(DispatcherEventInboxMessage.received(event));
        String identityError = identityError(run, event);
        if (identityError != null) {
            inbox.rejected(identityError);
            inboxRepository.save(inbox);
            return Outcome.REJECTED;
        }
        if (event.sequence() <= run.getLastDispatcherEventSequence()) {
            inbox.processed();
            inboxRepository.save(inbox);
            return Outcome.STALE;
        }

        apply(run, event);
        applyStreaming(run, event);
        run.recordDispatcherEvent(event.sequence(), event.externalExecutionId(), event.trackingUrl());
        runRepository.save(run);
        inbox.processed();
        inboxRepository.save(inbox);
        return Outcome.APPLIED;
    }

    private static String identityError(TaskRun run, DispatcherExecutionEvent event) {
        if (run == null) return "任务运行不存在";
        if (!Objects.equals(run.getComputeEngineId(), event.engineId())) return "计算引擎身份不匹配";
        if (!Objects.equals(run.getExternalExecutionId(), event.executionId())) return "执行身份不匹配";
        if (!Objects.equals(run.getAttempt(), event.attempt())) return "执行 attempt 不匹配";
        return null;
    }

    private static void apply(TaskRun run, DispatcherExecutionEvent event) {
        ExecutionMessageType type = event.messageType();
        SafeExecutionError error = event.error();
        switch (type) {
            case EXECUTION_ACCEPTED, EXECUTION_SUBMITTED -> { }
            case EXECUTION_RUNNING -> run.startAt(event.startedAt() == null ? event.occurredAt() : event.startedAt());
            case EXECUTION_SUCCEEDED -> run.externalSucceed(
                    event.affectedRows(), event.startedAt(), event.endedAt()
            );
            case EXECUTION_REJECTED, EXECUTION_FAILED, EXECUTION_LOST -> run.fail(error);
            case EXECUTION_TIMED_OUT -> run.timeout(error);
            case EXECUTION_CANCELLED -> run.cancel(error, event.startedAt(), event.endedAt());
            case STREAMING_PROGRESS -> run.startAt(
                    event.startedAt() == null ? event.occurredAt() : event.startedAt());
            case EXECUTION_STOPPED -> run.stop("实时任务已正常停止", event.endedAt());
            default -> throw new IllegalArgumentException("不是 Dispatcher 执行事件：" + type);
        }
    }

    private void applyStreaming(TaskRun run, DispatcherExecutionEvent event) {
        if (run.getTaskType() != TaskType.SPARK_STREAMING_CANVAS
                || run.getStreamingDeploymentId() == null) return;
        if (event.streamingDeploymentId() != null
                && !run.getStreamingDeploymentId().equals(event.streamingDeploymentId())) {
            throw new IllegalArgumentException("实时 deploymentId 不匹配");
        }
        TaskStreamingDeployment deployment = deploymentRepository
                .findByIdForUpdate(run.getStreamingDeploymentId()).orElseThrow();
        switch (event.messageType()) {
            case EXECUTION_RUNNING -> deployment.markRunning(event.startedAt());
            case STREAMING_PROGRESS -> {
                deployment.recordProgress(event.occurredAt());
                event.streamingProgress().forEach(progress -> {
                    TaskStreamingQuery query = queryRepository
                            .findByDeploymentIdAndOutputNodeId(
                                    deployment.getId(), java.util.UUID.fromString(progress.outputNodeId()))
                            .orElse(null);
                    if (query != null) {
                        query.recordProgress(
                                progress.batchId(), progress.inputRows(),
                                progress.inputRowsPerSecond(), progress.processedRowsPerSecond(),
                                progress.batchDurationMillis(), progress.progressAt());
                        queryRepository.save(query);
                    }
                });
            }
            case EXECUTION_STOPPED -> {
                deployment.markStopped(event.endedAt());
                queryRepository.findAllByDeploymentIdOrderByOutputNodeNameAsc(deployment.getId())
                        .forEach(TaskStreamingQuery::markStopped);
            }
            case EXECUTION_CANCELLED -> {
                deployment.markStopped(event.endedAt());
                queryRepository.findAllByDeploymentIdOrderByOutputNodeNameAsc(deployment.getId())
                        .forEach(TaskStreamingQuery::markStopped);
            }
            case EXECUTION_REJECTED, EXECUTION_FAILED, EXECUTION_LOST, EXECUTION_TIMED_OUT -> {
                String message = event.error() == null ? "实时任务执行失败" : event.error().message();
                deployment.fail(message, event.endedAt());
                queryRepository.findAllByDeploymentIdOrderByOutputNodeNameAsc(deployment.getId())
                        .forEach(query -> query.fail(message, event.endedAt()));
            }
            default -> {
            }
        }
        deploymentRepository.save(deployment);
    }
}
