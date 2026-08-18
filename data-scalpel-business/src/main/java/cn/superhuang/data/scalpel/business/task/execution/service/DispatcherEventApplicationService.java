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
import cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

@Service
public class DispatcherEventApplicationService {

    public enum Outcome { APPLIED, DUPLICATE, STALE, REJECTED }

    private final DispatcherEventInboxRepository inboxRepository;
    private final TaskRunRepository runRepository;
    private final TaskStreamingDeploymentRepository deploymentRepository;
    private final TaskStreamingQueryRepository queryRepository;
    private final TaskExecutionOutboxService executionOutboxService;
    private final ObjectMapper objectMapper;

    public DispatcherEventApplicationService(
            DispatcherEventInboxRepository inboxRepository,
            TaskRunRepository runRepository,
            TaskStreamingDeploymentRepository deploymentRepository,
            TaskStreamingQueryRepository queryRepository,
            TaskExecutionOutboxService executionOutboxService,
            ObjectMapper objectMapper
    ) {
        this.inboxRepository = inboxRepository;
        this.runRepository = runRepository;
        this.deploymentRepository = deploymentRepository;
        this.queryRepository = queryRepository;
        this.executionOutboxService = executionOutboxService;
        this.objectMapper = objectMapper;
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
        applyObservability(run, event);
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
        cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType expectedType = switch (run.getTaskType()) {
            case SPARK_JAR -> cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_JAR;
            case SPARK_STREAMING_JAR -> cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_STREAMING_JAR;
            case SPARK_MODEL_QUALITY -> cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_MODEL_QUALITY;
            case SPARK_STREAMING_CANVAS -> cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_STREAMING_CANVAS;
            default -> cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_CANVAS;
        };
        if (event.taskType() != null && event.taskType() != expectedType) {
            throw new IllegalArgumentException("Dispatcher 事件任务类型与运行记录不一致");
        }
        if (run.getTaskType() == TaskType.SPARK_MODEL_QUALITY
                && event.messageType() == ExecutionMessageType.EXECUTION_SUCCEEDED
                && event.qualitySummary() == null) {
            throw new IllegalArgumentException("模型质检成功事件缺少质量汇总");
        }
        ExecutionMessageType type = event.messageType();
        SafeExecutionError error = event.error();
        switch (type) {
            case EXECUTION_ACCEPTED, EXECUTION_SUBMITTED -> { }
            case EXECUTION_RUNNING -> run.startAt(event.startedAt() == null ? event.occurredAt() : event.startedAt());
            case EXECUTION_SUCCEEDED -> run.externalSucceed(
                    event.affectedRows(), event.startedAt(), event.endedAt(), event.qualitySummary()
            );
            case EXECUTION_REJECTED, EXECUTION_FAILED, EXECUTION_LOST -> run.fail(error);
            case EXECUTION_TIMED_OUT -> run.timeout(error);
            case EXECUTION_CANCELLED -> run.cancel(error, event.startedAt(), event.endedAt());
            case STREAMING_PROGRESS -> run.startAt(
                    event.startedAt() == null ? event.occurredAt() : event.startedAt());
            case USER_OBSERVABILITY -> { }
            case EXECUTION_STOPPED -> run.stop("实时任务已正常停止", event.endedAt());
            default -> throw new IllegalArgumentException("不是 Dispatcher 执行事件：" + type);
        }
    }

    private void applyObservability(TaskRun run, DispatcherExecutionEvent event) {
        var snapshot = event.userJobObservability();
        if (snapshot == null) return;
        var status = snapshot.status();
        run.recordUserJobObservability(
                status == null ? null : status.phase(),
                status == null ? null : status.message(),
                status == null ? null : status.updatedAt(),
                objectMapper.writeValueAsString(snapshot.metrics()));
    }

    private void applyStreaming(TaskRun run, DispatcherExecutionEvent event) {
        if (!run.getTaskType().isStreaming()
                || run.getStreamingDeploymentId() == null) return;
        if (event.streamingDeploymentId() != null
                && !run.getStreamingDeploymentId().equals(event.streamingDeploymentId())) {
            throw new IllegalArgumentException("实时 deploymentId 不匹配");
        }
        TaskStreamingDeployment deployment = deploymentRepository
                .findByIdForUpdate(run.getStreamingDeploymentId()).orElseThrow();
        if (event.messageType() == ExecutionMessageType.EXECUTION_RUNNING
                && run.getTaskType() == TaskType.SPARK_STREAMING_JAR) {
            if (!applyStreamingQuerySet(run, deployment, event)) {
                deploymentRepository.save(deployment);
                return;
            }
        }
        switch (event.messageType()) {
            case EXECUTION_RUNNING -> deployment.markRunning(event.startedAt());
            case STREAMING_PROGRESS -> {
                deployment.recordProgress(event.occurredAt());
                deployment.recordSourceProgress(event.streamingSourceProgress());
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

    private boolean applyStreamingQuerySet(
            TaskRun run,
            TaskStreamingDeployment deployment,
            DispatcherExecutionEvent event
    ) {
        var descriptors = event.streamingQueries();
        if (descriptors.isEmpty()) {
            failChangedQuerySet(run, deployment);
            return false;
        }
        var existing = queryRepository.findAllByDeploymentIdOrderByOutputNodeNameAsc(deployment.getId());
        if (existing.isEmpty()) {
            queryRepository.saveAllAndFlush(descriptors.stream().map(descriptor -> TaskStreamingQuery.create(
                    deployment.getId(), descriptor.queryId(), descriptor.logicalName(),
                    cn.superhuang.data.scalpel.business.task.domain.StreamingSinkType.valueOf(
                            descriptor.sinkType().name()),
                    descriptor.checkpointKey())).toList());
            return true;
        }
        boolean same = existing.size() == descriptors.size() && existing.stream().allMatch(query ->
                descriptors.stream().anyMatch(descriptor ->
                        query.getOutputNodeId().equals(descriptor.queryId())
                                && query.getOutputNodeName().equals(descriptor.logicalName())
                                && query.getSinkType().name().equals(descriptor.sinkType().name())
                                && query.getCheckpointKey().equals(descriptor.checkpointKey())));
        if (!same) {
            failChangedQuerySet(run, deployment);
            return false;
        }
        return true;
    }

    private void failChangedQuerySet(TaskRun run, TaskStreamingDeployment deployment) {
        String message = "同一实时部署恢复时查询集合发生变化";
        run.fail(new SafeExecutionError("STREAMING_QUERY_SET_CHANGED_WITHIN_DEFINITION", message));
        deployment.fail(message, java.time.Instant.now());
        queryRepository.findAllByDeploymentIdOrderByOutputNodeNameAsc(deployment.getId())
                .forEach(query -> query.fail(message, java.time.Instant.now()));
        executionOutboxService.enqueue(run.getCommandTopicSnapshot(), new StopStreamingExecutionCommand(
                1, java.util.UUID.randomUUID(), ExecutionMessageType.STOP_STREAMING_EXECUTION,
                java.time.Instant.now(), run.getComputeEngineId(), run.getExternalExecutionId(),
                run.getExecutionRunId(), run.getAttempt(), deployment.getId(), message, 60));
    }
}
