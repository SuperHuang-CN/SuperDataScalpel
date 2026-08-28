package cn.superhuang.data.scalpel.business.task.execution.service;

import cn.superhuang.data.scalpel.business.compute.client.DispatcherExecutionResponse;
import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineExecutionService;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingDeploymentRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskStreamingQueryRepository;
import cn.superhuang.data.scalpel.business.task.service.CanvasTaskRunProperties;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Low-frequency repair path for Dispatcher events that were not observed through Kafka. */
@Component
public class DispatcherExecutionReconciliationService {
    private static final List<TaskRunStatus> ACTIVE = List.of(
            TaskRunStatus.QUEUED, TaskRunStatus.RUNNING,
            TaskRunStatus.CANCEL_REQUESTED, TaskRunStatus.STOP_REQUESTED);

    private final TaskRunRepository runRepository;
    private final TaskStreamingDeploymentRepository deploymentRepository;
    private final TaskStreamingQueryRepository queryRepository;
    private final ComputeEngineExecutionService computeEngineExecutionService;
    private final DispatcherEventApplicationService eventApplicationService;
    private final CanvasTaskRunProperties properties;
    private final TransactionTemplate transactionTemplate;

    public DispatcherExecutionReconciliationService(
            TaskRunRepository runRepository,
            TaskStreamingDeploymentRepository deploymentRepository,
            TaskStreamingQueryRepository queryRepository,
            ComputeEngineExecutionService computeEngineExecutionService,
            DispatcherEventApplicationService eventApplicationService,
            CanvasTaskRunProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.runRepository = runRepository;
        this.deploymentRepository = deploymentRepository;
        this.queryRepository = queryRepository;
        this.computeEngineExecutionService = computeEngineExecutionService;
        this.eventApplicationService = eventApplicationService;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${data-scalpel.task-run.canvas.recovery-interval:30s}")
    public void reconcile() {
        for (TaskRun run : runRepository.findAllByTaskTypeAndStatusIn(TaskType.SPARK_CANVAS, ACTIVE)) {
            reconcile(run);
        }
        for (TaskRun run : runRepository.findAllByTaskTypeAndStatusIn(
                TaskType.SPARK_MODEL_QUALITY, ACTIVE)) {
            reconcile(run);
        }
        for (TaskRun run : runRepository.findAllByTaskTypeAndStatusIn(TaskType.SPARK_JAR, ACTIVE)) {
            reconcile(run);
        }
        for (TaskRun run : runRepository.findAllByTaskTypeAndStatusIn(
                TaskType.SPARK_STREAMING_CANVAS, ACTIVE)) {
            reconcile(run);
        }
        for (TaskRun run : runRepository.findAllByTaskTypeAndStatusIn(
                TaskType.SPARK_STREAMING_JAR, ACTIVE)) {
            reconcile(run);
        }
    }

    private void reconcile(TaskRun run) {
        if (run.getComputeEngineId() == null || run.getExternalExecutionId() == null) return;
        try {
            DispatcherExecutionResponse response = computeEngineExecutionService.execution(
                    run.getComputeEngineId(), run.getExternalExecutionId());
            if (!identityMatches(run, response) || response.sequence() < 1
                    || response.sequence() <= run.getLastDispatcherEventSequence()) return;
            eventApplicationService.accept(toEvent(run, response));
        } catch (RuntimeException exception) {
            if (dispatcherExecutionNotFound(exception) && recoverUntrackedStreamingRun(run)) {
                return;
            }
            if (run.getDeadlineAt() != null
                    && Instant.now().isAfter(run.getDeadlineAt().plus(properties.recoveryGrace()))) {
                transactionTemplate.executeWithoutResult(status -> runRepository.findByIdForUpdate(run.getId())
                        .filter(current -> ACTIVE.contains(current.getStatus()))
                        .ifPresent(current -> {
                            current.timeout("无法从 Dispatcher 确认执行终态", "DISPATCHER_RECONCILIATION_TIMEOUT");
                            runRepository.save(current);
                        }));
            }
        }
    }

    private boolean recoverUntrackedStreamingRun(TaskRun snapshot) {
        if (!snapshot.getTaskType().isStreaming()
                || snapshot.getStatus() != TaskRunStatus.STOP_REQUESTED
                || snapshot.getLastDispatcherEventSequence() != 0
                || snapshot.getStartedAt() != null
                || snapshot.getBackendApplicationId() != null) {
            return false;
        }
        Instant reference = snapshot.getUpdatedAt();
        if (reference == null || reference.plus(properties.recoveryGrace()).isAfter(Instant.now())) {
            return false;
        }
        transactionTemplate.executeWithoutResult(status -> runRepository.findByIdForUpdate(snapshot.getId())
                .filter(current -> current.getTaskType().isStreaming())
                .filter(current -> current.getStatus() == TaskRunStatus.STOP_REQUESTED)
                .filter(current -> current.getLastDispatcherEventSequence() == 0)
                .filter(current -> current.getStartedAt() == null)
                .filter(current -> current.getBackendApplicationId() == null)
                .ifPresent(current -> {
                    Instant endedAt = Instant.now();
                    String message = "实时任务未进入 Dispatcher，已确认在启动前停止";
                    current.stop(message, endedAt);
                    runRepository.save(current);
                    if (current.getStreamingDeploymentId() == null) return;
                    deploymentRepository.findByIdForUpdate(current.getStreamingDeploymentId()).ifPresent(deployment -> {
                        deployment.markStopped(endedAt);
                        deploymentRepository.save(deployment);
                        queryRepository.findAllByDeploymentIdOrderByOutputNodeNameAsc(deployment.getId())
                                .forEach(query -> {
                                    query.markStopped();
                                    queryRepository.save(query);
                                });
                    });
                }));
        return true;
    }

    private static boolean identityMatches(TaskRun run, DispatcherExecutionResponse response) {
        return Objects.equals(run.getExecutionRunId(), response.runId())
                && Objects.equals(run.getExternalExecutionId(), response.executionId())
                && Objects.equals(run.getAttempt(), response.attempt())
                && Objects.equals(run.getComputeEngineId(), response.engineId())
                && response.backendType() != null;
    }

    static DispatcherExecutionEvent toEvent(
            TaskRun run,
            DispatcherExecutionResponse response
    ) {
        ExecutionMessageType type = switch (response.state()) {
            case "QUEUED", "SUBMITTING" -> ExecutionMessageType.EXECUTION_ACCEPTED;
            case "SUBMITTED" -> ExecutionMessageType.EXECUTION_SUBMITTED;
            case "RUNNING" -> ExecutionMessageType.EXECUTION_RUNNING;
            case "CANCEL_REQUESTED" -> response.startedAt() == null
                    ? ExecutionMessageType.EXECUTION_SUBMITTED : ExecutionMessageType.EXECUTION_RUNNING;
            case "SUCCESS" -> ExecutionMessageType.EXECUTION_SUCCEEDED;
            case "FAILED" -> ExecutionMessageType.EXECUTION_FAILED;
            case "TIMED_OUT" -> ExecutionMessageType.EXECUTION_TIMED_OUT;
            case "CANCELLED" -> ExecutionMessageType.EXECUTION_CANCELLED;
            case "STOPPED" -> ExecutionMessageType.EXECUTION_STOPPED;
            case "LOST" -> ExecutionMessageType.EXECUTION_LOST;
            default -> throw new IllegalArgumentException("Dispatcher 返回了未知执行状态");
        };
        SafeExecutionError error = switch (type) {
            case EXECUTION_FAILED, EXECUTION_TIMED_OUT, EXECUTION_LOST, EXECUTION_CANCELLED ->
                    response.executionError() != null ? response.executionError() : new SafeExecutionError(
                            safeCode(response.errorCode(), type), safeMessage(response.errorMessage(), type));
            default -> null;
        };
        UUID messageId = UUID.nameUUIDFromBytes(("dispatcher-reconcile:" + response.executionId() + ":"
                + response.sequence()).getBytes(StandardCharsets.UTF_8));
        String resultSha256 = switch (type) {
            case EXECUTION_SUCCEEDED, EXECUTION_FAILED, EXECUTION_TIMED_OUT, EXECUTION_CANCELLED ->
                    response.resultSha256();
            default -> null;
        };
        return new DispatcherExecutionEvent(
                1, messageId, type, Instant.now(), response.engineId(), response.executionId(), response.runId(),
                response.attempt(), response.sequence(), response.backendType(), response.externalExecutionId(),
                response.trackingUrl(), response.startedAt(), response.endedAt(), response.affectedRows(), error,
                run.getStreamingDeploymentId(), List.of(), List.of(), null, response.qualitySummary(),
                switch (run.getTaskType()) {
                    case SPARK_JAR -> cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_JAR;
                    case SPARK_STREAMING_JAR -> cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_STREAMING_JAR;
                    case SPARK_MODEL_QUALITY -> cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_MODEL_QUALITY;
                    case SPARK_STREAMING_CANVAS -> cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_STREAMING_CANVAS;
                    default -> cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_CANVAS;
                }, null, resultSha256);
    }

    static boolean dispatcherExecutionNotFound(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof RestClientResponseException response
                    && response.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return true;
            }
        }
        return false;
    }

    private static String safeCode(String value, ExecutionMessageType type) {
        if (value != null && value.matches("[A-Z][A-Z0-9_]{0,99}")) return value;
        return switch (type) {
            case EXECUTION_TIMED_OUT -> "EXECUTION_TIMEOUT";
            case EXECUTION_CANCELLED -> "EXECUTION_CANCELLED";
            case EXECUTION_LOST -> "EXECUTION_LOST";
            default -> "EXECUTION_FAILED";
        };
    }

    private static String safeMessage(String value, ExecutionMessageType type) {
        String fallback = switch (type) {
            case EXECUTION_TIMED_OUT -> "执行超时";
            case EXECUTION_CANCELLED -> "任务执行已取消";
            case EXECUTION_LOST -> "Dispatcher 无法确认外部执行";
            default -> "执行失败";
        };
        String safe = value == null || value.isBlank() ? fallback : value.trim();
        safe = safe.replaceAll("(?i)(password|secret|token)=[^\\s,;]+", "$1=***");
        return safe.substring(0, Math.min(1000, safe.length()));
    }
}
