package cn.superhuang.data.scalpel.dispatcher.messaging.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerFailedEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerResultAvailableEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerStartedEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerStreamingStartedEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerStreamingProgressEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerStreamingStoppedEvent;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherResultResolution;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherResultService;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherMessageInbox;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import cn.superhuang.data.scalpel.dispatcher.messaging.MessageCoordinates;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherMessageInboxRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import cn.superhuang.data.scalpel.dispatcher.service.DispatcherEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DispatcherRunnerEventService {
    private final DispatcherMessageInboxRepository inboxRepository;
    private final DispatcherTaskExecutionRepository executionRepository;
    private final DispatcherEventService eventService;
    private final DispatcherResultService resultService;
    private final TransactionTemplate transactionTemplate;

    public DispatcherRunnerEventService(
            DispatcherMessageInboxRepository inboxRepository,
            DispatcherTaskExecutionRepository executionRepository,
            DispatcherEventService eventService,
            DispatcherResultService resultService,
            TransactionTemplate transactionTemplate
    ) {
        this.inboxRepository = inboxRepository;
        this.executionRepository = executionRepository;
        this.eventService = eventService;
        this.resultService = resultService;
        this.transactionTemplate = transactionTemplate;
    }

    public void accept(RunnerExecutionEvent event, MessageCoordinates coordinates) {
        if (inboxRepository.existsByMessageId(event.messageId())) return;
        if (event instanceof RunnerResultAvailableEvent available) {
            acceptResult(available, coordinates);
            return;
        }
        transactionTemplate.executeWithoutResult(status -> acceptSignal(event, coordinates));
    }

    private void acceptResult(RunnerResultAvailableEvent event, MessageCoordinates coordinates) {
        DispatcherTaskExecution snapshot = executionRepository.findByExecutionIdAndAttempt(
                event.executionId(), event.attempt()).orElse(null);
        if (!matches(snapshot, event)) {
            transactionTemplate.executeWithoutResult(status -> rejectIdentity(event, coordinates));
            return;
        }
        if (snapshot.getState() == DispatcherExecutionState.SUBMITTING
                || snapshot.getState() == DispatcherExecutionState.QUEUED) {
            throw new IllegalStateException("Runner 结果早于 Dispatcher 完成提交，等待重试");
        }
        DispatcherResultResolution resolution;
        try {
            // Validate availability and identity now, but defer the authoritative
            // terminal transition until the Backend is terminal and its console log
            // has been stored by the coordinator.
            resolution = resultService.verify(event.executionId(), event.resultSha256());
        } catch (BackendException exception) {
            throw new IllegalStateException("Runner 结果制品暂时无法读取", exception);
        }
        if (resolution == DispatcherResultResolution.NOT_FOUND) {
            throw new IllegalStateException("Runner 已报告结果但结果制品尚不可见");
        }
        transactionTemplate.executeWithoutResult(status -> {
            if (inboxRepository.existsByMessageId(event.messageId())) return;
            DispatcherMessageInbox inbox = inboxRepository.save(received(event, coordinates));
            if (resolution == DispatcherResultResolution.SIGNAL_REJECTED) {
                inbox.rejected("Runner 结果 SHA-256 与对象内容不匹配");
            } else {
                inbox.processed();
            }
            inboxRepository.save(inbox);
        });
    }

    private void acceptSignal(RunnerExecutionEvent event, MessageCoordinates coordinates) {
        if (inboxRepository.existsByMessageId(event.messageId())) return;
        DispatcherMessageInbox inbox = inboxRepository.save(DispatcherMessageInbox.received(
                event.messageId(), event.messageType().name(), coordinates.topic(), coordinates.partition(),
                coordinates.offset(), event.executionId()
        ));
        DispatcherTaskExecution execution = executionRepository.findByExecutionIdAndAttempt(
                event.executionId(), event.attempt()
        ).orElse(null);
        if (!matches(execution, event)) {
            inbox.rejected("Runner 事件身份与执行账本不匹配");
            return;
        }
        switch (event) {
            case RunnerStartedEvent started -> {
                if (execution.getState().terminal()) break;
                boolean first = execution.getStartedAt() == null;
                execution.running(started.occurredAt());
                if (first && execution.getState() == DispatcherExecutionState.RUNNING) {
                    eventService.enqueue(execution, ExecutionMessageType.EXECUTION_RUNNING, null, null);
                }
            }
            case RunnerResultAvailableEvent ignored -> throw new IllegalStateException("结果事件必须经过制品校验");
            case RunnerFailedEvent failed -> {
                if (execution.getState().terminal()) break;
                execution.fail(failed.error());
                eventService.enqueue(execution, ExecutionMessageType.EXECUTION_FAILED, failed.error(), null);
            }
            case RunnerStreamingStartedEvent started -> {
                if (execution.getState().terminal()) break;
                boolean first = execution.getStartedAt() == null;
                execution.running(started.occurredAt());
                if (first && execution.getState() == DispatcherExecutionState.RUNNING) {
                    eventService.enqueue(execution, ExecutionMessageType.EXECUTION_RUNNING, null, null);
                }
            }
            case RunnerStreamingProgressEvent progress -> {
                if (execution.getState().terminal()) break;
                execution.running(progress.occurredAt());
                eventService.enqueue(
                        execution, ExecutionMessageType.STREAMING_PROGRESS, null, null, progress.queries());
            }
            case RunnerStreamingStoppedEvent stopped -> {
                if (execution.getState().terminal()) break;
                execution.stopped(stopped.stoppedAt());
                eventService.enqueue(execution, ExecutionMessageType.EXECUTION_STOPPED, null, null);
            }
        }
        executionRepository.save(execution);
        inbox.processed();
        inboxRepository.save(inbox);
    }

    private void rejectIdentity(RunnerExecutionEvent event, MessageCoordinates coordinates) {
        if (inboxRepository.existsByMessageId(event.messageId())) return;
        DispatcherMessageInbox inbox = inboxRepository.save(received(event, coordinates));
        inbox.rejected("Runner 事件身份与执行账本不匹配");
        inboxRepository.save(inbox);
    }

    private static DispatcherMessageInbox received(RunnerExecutionEvent event, MessageCoordinates coordinates) {
        return DispatcherMessageInbox.received(
                event.messageId(), event.messageType().name(), coordinates.topic(), coordinates.partition(),
                coordinates.offset(), event.executionId());
    }

    private static boolean matches(DispatcherTaskExecution execution, RunnerExecutionEvent event) {
        return execution != null && execution.getEngineId().equals(event.engineId())
                && execution.getRunId().equals(event.runId());
    }
}
