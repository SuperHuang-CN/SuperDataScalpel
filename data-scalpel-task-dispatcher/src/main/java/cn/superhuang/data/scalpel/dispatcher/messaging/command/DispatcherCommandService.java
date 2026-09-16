package cn.superhuang.data.scalpel.dispatcher.messaging.command;

import cn.superhuang.data.scalpel.contract.execution.CancelExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.ExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.ForceTerminateExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.SafeExecutionError;
import cn.superhuang.data.scalpel.contract.execution.SubmitExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.StartStreamingExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionState;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherMessageInbox;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherStreamingStop;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherStreamingStopRepository;
import cn.superhuang.data.scalpel.dispatcher.messaging.ExecutionRequestFingerprint;
import cn.superhuang.data.scalpel.dispatcher.messaging.MessageCoordinates;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherMessageInboxRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import cn.superhuang.data.scalpel.dispatcher.service.DispatcherEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DispatcherCommandService {

    public enum Outcome { ACCEPTED, DUPLICATE, REJECTED }

    private final DispatcherMessageInboxRepository inboxRepository;
    private final DispatcherTaskExecutionRepository executionRepository;
    private final DispatcherRegistrationRepository registrationRepository;
    private final DispatcherEventService eventService;
    private final DispatcherStreamingStopRepository stoppedExecutions;

    public DispatcherCommandService(
            DispatcherMessageInboxRepository inboxRepository,
            DispatcherTaskExecutionRepository executionRepository,
            DispatcherRegistrationRepository registrationRepository,
            DispatcherEventService eventService,
            DispatcherStreamingStopRepository stoppedExecutions
    ) {
        this.inboxRepository = inboxRepository;
        this.executionRepository = executionRepository;
        this.registrationRepository = registrationRepository;
        this.eventService = eventService;
        this.stoppedExecutions = stoppedExecutions;
    }

    @Transactional
    public Outcome accept(ExecutionCommand command, MessageCoordinates coordinates) {
        if (inboxRepository.existsByMessageId(command.messageId())) return Outcome.DUPLICATE;
        DispatcherMessageInbox inbox = inboxRepository.save(DispatcherMessageInbox.received(
                command.messageId(), command.messageType().name(), coordinates.topic(), coordinates.partition(),
                coordinates.offset(), command.executionId()
        ));
        List<DispatcherRegistration> registrations = registrationRepository.findAllForUpdate();
        DispatcherRegistration registration = registrations.isEmpty() ? null : registrations.getFirst();
        if (registration == null || !registration.getEngineId().equals(command.engineId())
                || registration.getState() == DispatcherRegistrationState.INACTIVE
                || registration.getState() == DispatcherRegistrationState.ERROR) {
            inbox.rejected("Dispatcher 注册身份或状态不允许处理命令");
            return Outcome.REJECTED;
        }
        Outcome outcome = switch (command) {
            case SubmitExecutionCommand submit -> submit(submit, registration, inbox);
            case CancelExecutionCommand cancel -> cancel(cancel, inbox);
            case ForceTerminateExecutionCommand forceTerminate -> forceTerminate(forceTerminate, inbox);
            case StartStreamingExecutionCommand start -> start(start, registration, inbox);
            case StopStreamingExecutionCommand stop -> stop(stop, registration, inbox);
        };
        inboxRepository.save(inbox);
        return outcome;
    }

    private Outcome start(
            StartStreamingExecutionCommand command,
            DispatcherRegistration registration,
            DispatcherMessageInbox inbox
    ) {
        var stopped = stoppedExecutions.findByExecutionIdAndAttempt(command.executionId(), command.attempt());
        if (stopped.isPresent()) {
            if (!stopped.get().matches(command)) {
                inbox.rejected("实时执行与已停止记录的身份不一致");
                return Outcome.REJECTED;
            }
            inbox.processed();
            return Outcome.DUPLICATE;
        }
        String fingerprint = ExecutionRequestFingerprint.of(command);
        DispatcherTaskExecution existing = executionRepository.findByExecutionIdAndAttempt(
                command.executionId(), command.attempt()).orElse(null);
        if (existing != null) {
            if (existing.getRequestFingerprint().equals(fingerprint)) {
                inbox.processed();
                return Outcome.DUPLICATE;
            }
            inbox.rejected("实时执行请求指纹冲突");
            return Outcome.REJECTED;
        }
        SparkExecutionResourceSpec resources = resolveResources(command.executionResources(), registration);
        DispatcherTaskExecution execution = DispatcherTaskExecution.queue(
                command, fingerprint, registration.getBackendType(), resources);
        if (resources.exceeds(registration.getResourcePolicy().maximums())) {
            SafeExecutionError error = new SafeExecutionError(
                    "RESOURCE_LIMIT_EXCEEDED", "任务运行资源超过计算引擎单次任务上限");
            execution.fail(error);
            executionRepository.save(execution);
            eventService.enqueue(execution, ExecutionMessageType.EXECUTION_REJECTED, error, null);
            inbox.processed();
            return Outcome.REJECTED;
        }
        if (registration.getState() == DispatcherRegistrationState.DRAINING) {
            SafeExecutionError error = new SafeExecutionError("ENGINE_DRAINING", "计算引擎正在排空");
            execution.fail(error);
            executionRepository.save(execution);
            eventService.enqueue(execution, ExecutionMessageType.EXECUTION_REJECTED, error, null);
            inbox.processed();
            return Outcome.REJECTED;
        }
        long queued = executionRepository.countByState(DispatcherExecutionState.QUEUED);
        if (queued >= registration.getMaxQueuedExecutions()) {
            SafeExecutionError error = new SafeExecutionError("CAPACITY_EXCEEDED", "Dispatcher 排队容量已满");
            execution.fail(error);
            executionRepository.save(execution);
            eventService.enqueue(execution, ExecutionMessageType.EXECUTION_REJECTED, error, null);
            inbox.processed();
            return Outcome.REJECTED;
        }
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_ACCEPTED, null, null);
        inbox.processed();
        return Outcome.ACCEPTED;
    }

    private Outcome stop(
            StopStreamingExecutionCommand command,
            DispatcherRegistration registration,
            DispatcherMessageInbox inbox
    ) {
        DispatcherTaskExecution execution = executionRepository.findByExecutionIdAndAttempt(
                command.executionId(), command.attempt()).orElse(null);
        if (execution == null) {
            // Registration row locking serializes START and STOP. Persist the decision
            // in the same transaction as the event so a delayed START cannot launch.
            var stopped = stoppedExecutions.findByExecutionIdAndAttempt(command.executionId(), command.attempt());
            if (stopped.isPresent()) {
                if (!stopped.get().matches(command)) {
                    inbox.rejected("实时停止命令与已停止记录的身份不一致");
                    return Outcome.REJECTED;
                }
                inbox.processed();
                return Outcome.DUPLICATE;
            }
            stoppedExecutions.save(DispatcherStreamingStop.from(command));
            eventService.enqueueUntrackedStreamingStop(command, registration);
            inbox.processed();
            return Outcome.ACCEPTED;
        }
        if (execution.getStreamingDeploymentId() == null
                || !execution.getStreamingDeploymentId().equals(command.deploymentId())
                || !execution.getRunId().equals(command.runId())
                || !execution.getEngineId().equals(command.engineId())) {
            inbox.rejected("停止命令身份与实时执行账本不匹配");
            return Outcome.REJECTED;
        }
        boolean queued = execution.getState() == DispatcherExecutionState.QUEUED;
        execution.requestStreamingStop(command.gracePeriodSeconds());
        executionRepository.save(execution);
        if (queued) {
            eventService.enqueue(execution, ExecutionMessageType.EXECUTION_STOPPED, null, null);
        } else {
            eventService.enqueueRunnerControl(execution, command);
        }
        inbox.processed();
        return Outcome.ACCEPTED;
    }

    private Outcome submit(
            SubmitExecutionCommand command,
            DispatcherRegistration registration,
            DispatcherMessageInbox inbox
    ) {
        String fingerprint = ExecutionRequestFingerprint.of(command);
        DispatcherTaskExecution existing = executionRepository.findByExecutionIdAndAttempt(
                command.executionId(), command.attempt()
        ).orElse(null);
        if (existing != null) {
            if (existing.getRequestFingerprint().equals(fingerprint)) {
                inbox.processed();
                return Outcome.DUPLICATE;
            }
            // Never publish a rejection using the existing ledger's next sequence:
            // that would make Admin fail a legitimate in-flight execution. The
            // conflicting command remains auditable in the Inbox and is rejected.
            inbox.rejected("执行请求指纹冲突");
            return Outcome.REJECTED;
        }

        SparkExecutionResourceSpec resources = resolveResources(command.executionResources(), registration);
        DispatcherTaskExecution execution = DispatcherTaskExecution.queue(
                command, fingerprint, registration.getBackendType(), resources);
        if (resources.exceeds(registration.getResourcePolicy().maximums())) {
            SafeExecutionError error = new SafeExecutionError(
                    "RESOURCE_LIMIT_EXCEEDED", "任务运行资源超过计算引擎单次任务上限");
            execution.fail(error);
            executionRepository.save(execution);
            eventService.enqueue(execution, ExecutionMessageType.EXECUTION_REJECTED, error, null);
            inbox.processed();
            return Outcome.REJECTED;
        }
        if (registration.getState() == DispatcherRegistrationState.DRAINING) {
            SafeExecutionError error = new SafeExecutionError("ENGINE_DRAINING", "计算引擎正在排空");
            execution.fail(error);
            executionRepository.save(execution);
            eventService.enqueue(execution, ExecutionMessageType.EXECUTION_REJECTED, error, null);
            inbox.processed();
            return Outcome.REJECTED;
        }
        long queued = executionRepository.countByState(DispatcherExecutionState.QUEUED);
        if (queued >= registration.getMaxQueuedExecutions()) {
            SafeExecutionError error = new SafeExecutionError("CAPACITY_EXCEEDED", "Dispatcher 排队容量已满");
            execution.fail(error);
            executionRepository.save(execution);
            eventService.enqueue(execution, ExecutionMessageType.EXECUTION_REJECTED, error, null);
            inbox.processed();
            return Outcome.REJECTED;
        }
        executionRepository.save(execution);
        eventService.enqueue(execution, ExecutionMessageType.EXECUTION_ACCEPTED, null, null);
        inbox.processed();
        return Outcome.ACCEPTED;
    }

    private Outcome cancel(CancelExecutionCommand command, DispatcherMessageInbox inbox) {
        DispatcherTaskExecution execution = executionRepository.findByExecutionIdAndAttempt(
                command.executionId(), command.attempt()
        ).orElse(null);
        if (execution == null || !execution.getRunId().equals(command.runId())
                || !execution.getEngineId().equals(command.engineId())) {
            inbox.rejected("取消命令身份与执行账本不匹配");
            return Outcome.REJECTED;
        }
        boolean queued = execution.getState() == DispatcherExecutionState.QUEUED;
        execution.requestCancel();
        executionRepository.save(execution);
        if (queued) eventService.enqueue(execution, ExecutionMessageType.EXECUTION_CANCELLED, null, null);
        inbox.processed();
        return Outcome.ACCEPTED;
    }

    private Outcome forceTerminate(
            ForceTerminateExecutionCommand command,
            DispatcherMessageInbox inbox
    ) {
        DispatcherTaskExecution execution = executionRepository.findByExecutionIdAndAttempt(
                command.executionId(), command.attempt()
        ).orElse(null);
        if (execution == null || !execution.getRunId().equals(command.runId())
                || !execution.getEngineId().equals(command.engineId())) {
            inbox.rejected("强制终止命令身份与执行账本不匹配");
            return Outcome.REJECTED;
        }
        if (execution.getState().terminal()) {
            inbox.processed();
            return Outcome.DUPLICATE;
        }
        boolean queued = execution.getState() == DispatcherExecutionState.QUEUED;
        execution.requestForceTerminate();
        if (queued) {
            SafeExecutionError error = forceTerminationError("执行在提交前被强制终止");
            execution.cancelled(error);
            eventService.enqueue(execution, ExecutionMessageType.EXECUTION_CANCELLED, error, null);
        }
        executionRepository.save(execution);
        inbox.processed();
        return Outcome.ACCEPTED;
    }

    private static SafeExecutionError forceTerminationError(String message) {
        return new SafeExecutionError(
                "EXECUTION_FORCE_TERMINATED", message, ExecutionErrorCategory.CANCELLED, false,
                null, null, null, ExecutionFailurePhase.DISPATCH, null, UUID.randomUUID());
    }

    private static SparkExecutionResourceSpec resolveResources(
            SparkExecutionResourceSpec requested,
            DispatcherRegistration registration
    ) {
        SparkExecutionResourceSpec resources = requested == null
                ? registration.getResourcePolicy().defaults() : requested;
        return resources;
    }
}
