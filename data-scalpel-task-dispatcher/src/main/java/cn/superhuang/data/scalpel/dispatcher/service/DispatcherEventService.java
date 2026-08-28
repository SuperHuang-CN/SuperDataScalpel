package cn.superhuang.data.scalpel.dispatcher.service;

import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.StreamingQueryProgress;
import cn.superhuang.data.scalpel.contract.execution.StreamingSourceProgress;
import cn.superhuang.data.scalpel.contract.execution.StreamingQueryDescriptor;
import cn.superhuang.data.scalpel.contract.execution.UserJobObservabilitySnapshot;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherEventOutbox;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import cn.superhuang.data.scalpel.dispatcher.messaging.DispatcherMessageJsonCodec;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherEventOutboxRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import cn.superhuang.data.scalpel.contract.quality.QualitySummary;

@Service
public class DispatcherEventService {
    private final DispatcherEventOutboxRepository outboxRepository;
    private final DispatcherRegistrationRepository registrationRepository;
    private final DispatcherMessageJsonCodec codec;

    public DispatcherEventService(
            DispatcherEventOutboxRepository outboxRepository,
            DispatcherRegistrationRepository registrationRepository,
            DispatcherMessageJsonCodec codec
    ) {
        this.outboxRepository = outboxRepository;
        this.registrationRepository = registrationRepository;
        this.codec = codec;
    }

    public DispatcherExecutionEvent enqueue(
            DispatcherTaskExecution execution,
            ExecutionMessageType type,
            SafeExecutionError error,
            Long affectedRows
    ) {
        return enqueue(execution, type, error, affectedRows, List.of(), List.of(), null, null, null);
    }

    public DispatcherExecutionEvent enqueue(
            DispatcherTaskExecution execution,
            ExecutionMessageType type,
            SafeExecutionError error,
            Long affectedRows,
            QualitySummary qualitySummary
    ) {
        return enqueue(execution, type, error, affectedRows, List.of(), List.of(), null, qualitySummary, null);
    }

    public DispatcherExecutionEvent enqueueTerminal(
            DispatcherTaskExecution execution,
            ExecutionMessageType type,
            SafeExecutionError error,
            Long affectedRows,
            QualitySummary qualitySummary,
            UserJobObservabilitySnapshot observability
    ) {
        return enqueue(execution, type, error, affectedRows, List.of(), List.of(), null,
                qualitySummary, observability);
    }

    public DispatcherExecutionEvent enqueueObservability(
            DispatcherTaskExecution execution,
            UserJobObservabilitySnapshot observability
    ) {
        return enqueue(execution, ExecutionMessageType.USER_OBSERVABILITY, null, null,
                List.of(), List.of(), null, null, observability);
    }

    public DispatcherExecutionEvent enqueue(
            DispatcherTaskExecution execution,
            ExecutionMessageType type,
            SafeExecutionError error,
            Long affectedRows,
            List<StreamingQueryProgress> streamingProgress
    ) {
        return enqueue(execution, type, error, affectedRows, List.of(), streamingProgress, null, null, null);
    }

    public DispatcherExecutionEvent enqueue(
            DispatcherTaskExecution execution,
            ExecutionMessageType type,
            SafeExecutionError error,
            Long affectedRows,
            List<StreamingQueryProgress> streamingProgress,
            StreamingSourceProgress streamingSourceProgress
    ) {
        return enqueue(execution, type, error, affectedRows, List.of(), streamingProgress,
                streamingSourceProgress, null, null);
    }

    public DispatcherExecutionEvent enqueueStreamingStarted(
            DispatcherTaskExecution execution,
            List<StreamingQueryDescriptor> queries
    ) {
        return enqueue(execution, ExecutionMessageType.EXECUTION_RUNNING, null, null,
                queries, List.of(), null, null, null);
    }

    private DispatcherExecutionEvent enqueue(
            DispatcherTaskExecution execution,
            ExecutionMessageType type,
            SafeExecutionError error,
            Long affectedRows,
            List<StreamingQueryDescriptor> streamingQueries,
            List<StreamingQueryProgress> streamingProgress,
            StreamingSourceProgress streamingSourceProgress,
            QualitySummary qualitySummary,
            UserJobObservabilitySnapshot userJobObservability
    ) {
        long sequence = execution.nextEventSequence();
        String resultSha256 = switch (type) {
            case EXECUTION_SUCCEEDED, EXECUTION_FAILED, EXECUTION_TIMED_OUT, EXECUTION_CANCELLED ->
                    execution.getResultSha256();
            default -> null;
        };
        DispatcherExecutionEvent event = new DispatcherExecutionEvent(
                1, UUID.randomUUID(), type, Instant.now(), execution.getEngineId(), execution.getExecutionId(),
                execution.getRunId(), execution.getAttempt(), sequence, execution.getBackendType(),
                execution.getExternalExecutionId(), execution.getTrackingUrl(), execution.getStartedAt(),
                execution.getEndedAt(), affectedRows, error,
                execution.getStreamingDeploymentId(), streamingQueries, streamingProgress,
                streamingSourceProgress, qualitySummary,
                execution.getTaskType(), userJobObservability, resultSha256
        );
        String topic = registrationRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new IllegalStateException("Dispatcher 尚未注册"))
                .getAdminEventTopic();
        outboxRepository.save(DispatcherEventOutbox.pending(topic, event, codec.write(event)));
        return event;
    }

    public void enqueueRunnerControl(
            DispatcherTaskExecution execution,
            StopStreamingExecutionCommand command
    ) {
        String topic = registrationRepository.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new IllegalStateException("Dispatcher 尚未注册"))
                .getRunnerControlTopic();
        outboxRepository.save(DispatcherEventOutbox.pendingControl(
                topic, command, codec.write(command)));
    }

    public DispatcherExecutionEvent enqueueUntrackedStreamingStop(
            StopStreamingExecutionCommand command,
            DispatcherRegistration registration
    ) {
        Instant stoppedAt = Instant.now();
        UUID messageId = UUID.nameUUIDFromBytes(
                ("dispatcher-untracked-streaming-stop:" + command.messageId())
                        .getBytes(StandardCharsets.UTF_8)
        );
        DispatcherExecutionEvent event = new DispatcherExecutionEvent(
                1, messageId, ExecutionMessageType.EXECUTION_STOPPED, stoppedAt,
                command.engineId(), command.executionId(), command.runId(), command.attempt(),
                1, registration.getBackendType(), null, null, null, stoppedAt,
                null, null, command.deploymentId(), List.of()
        );
        outboxRepository.save(DispatcherEventOutbox.pending(
                registration.getAdminEventTopic(), event, codec.write(event)));
        return event;
    }
}
