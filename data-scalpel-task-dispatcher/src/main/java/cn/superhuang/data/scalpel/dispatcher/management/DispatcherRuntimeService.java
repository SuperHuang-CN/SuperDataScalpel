package cn.superhuang.data.scalpel.dispatcher.management;

import cn.superhuang.data.scalpel.contract.execution.DispatcherAdmissionCapacity;
import cn.superhuang.data.scalpel.contract.execution.DispatcherAdmissionUsage;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionScope;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionState;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionSummaryResponse;
import cn.superhuang.data.scalpel.contract.execution.DispatcherResourceConfiguration;
import cn.superhuang.data.scalpel.contract.execution.DispatcherRuntimeDependency;
import cn.superhuang.data.scalpel.contract.execution.DispatcherRuntimeOverviewResponse;
import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherProperties;
import cn.superhuang.data.scalpel.dispatcher.config.KubernetesProperties;
import cn.superhuang.data.scalpel.dispatcher.config.LocalDockerProperties;
import cn.superhuang.data.scalpel.dispatcher.config.YarnProperties;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Read-only Dispatcher operational projections for the control plane. */
@Service
public class DispatcherRuntimeService {
    private static final List<DispatcherExecutionState> ACTIVE_STATES = List.of(
            DispatcherExecutionState.SUBMITTING, DispatcherExecutionState.SUBMITTED,
            DispatcherExecutionState.RUNNING, DispatcherExecutionState.CANCEL_REQUESTED
    );
    private static final List<DispatcherExecutionState> TERMINAL_STATES = List.of(
            DispatcherExecutionState.SUCCESS, DispatcherExecutionState.FAILED,
            DispatcherExecutionState.TIMED_OUT, DispatcherExecutionState.CANCELLED,
            DispatcherExecutionState.STOPPED, DispatcherExecutionState.LOST
    );

    private final DispatcherRegistrationService registrationService;
    private final DispatcherRegistrationRepository registrationRepository;
    private final DispatcherTaskExecutionRepository executionRepository;
    private final DispatcherProperties dispatcherProperties;
    private final LocalDockerProperties localDockerProperties;
    private final YarnProperties yarnProperties;
    private final KubernetesProperties kubernetesProperties;

    public DispatcherRuntimeService(
            DispatcherRegistrationService registrationService,
            DispatcherRegistrationRepository registrationRepository,
            DispatcherTaskExecutionRepository executionRepository,
            DispatcherProperties dispatcherProperties,
            LocalDockerProperties localDockerProperties,
            YarnProperties yarnProperties,
            KubernetesProperties kubernetesProperties
    ) {
        this.registrationService = registrationService;
        this.registrationRepository = registrationRepository;
        this.executionRepository = executionRepository;
        this.dispatcherProperties = dispatcherProperties;
        this.localDockerProperties = localDockerProperties;
        this.yarnProperties = yarnProperties;
        this.kubernetesProperties = kubernetesProperties;
    }

    @Transactional(readOnly = true)
    public DispatcherRuntimeOverviewResponse overview() {
        DispatcherInfoResponse info = registrationService.info();
        DispatcherRegistration registration = registrationRepository.findFirstByOrderByCreatedAtAsc().orElse(null);
        DispatcherAdmissionCapacity capacity = registration == null ? null : new DispatcherAdmissionCapacity(
                registration.getMaxQueuedExecutions(), registration.getMaxConcurrentSubmissions(),
                registration.getMaxInFlightApplications());
        DispatcherAdmissionUsage usage = new DispatcherAdmissionUsage(
                executionRepository.countByState(DispatcherExecutionState.QUEUED),
                executionRepository.countByState(DispatcherExecutionState.SUBMITTING),
                executionRepository.countByState(DispatcherExecutionState.SUBMITTED),
                executionRepository.countByState(DispatcherExecutionState.RUNNING),
                executionRepository.countByState(DispatcherExecutionState.CANCEL_REQUESTED),
                executionRepository.countByStateIn(ACTIVE_STATES)
        );
        List<DispatcherRuntimeDependency> dependencies = info.dependencies().stream()
                .map(value -> new DispatcherRuntimeDependency(value.name(), value.state(), value.detail()))
                .toList();
        return new DispatcherRuntimeOverviewResponse(
                registration == null ? null : registration.getEngineId(),
                info.dispatcherInstanceId(), info.backendType(), info.version(),
                registration == null ? "UNREGISTERED" : registration.getState().name(),
                dependencies, capacity, usage, resourceConfiguration(), Instant.now());
    }

    @Transactional(readOnly = true)
    public PageResponse<DispatcherExecutionSummaryResponse> executions(
            DispatcherExecutionScope scope,
            int page,
            int size
    ) {
        Page<DispatcherTaskExecution> executions = switch (scope) {
            case ACTIVE -> executionRepository.findAllByStateIn(ACTIVE_STATES,
                    PageRequest.of(page, size, Sort.by("queuedAt").ascending().and(Sort.by("executionId").ascending())));
            case QUEUED -> executionRepository.findAllByState(DispatcherExecutionState.QUEUED,
                    PageRequest.of(page, size, Sort.by("queuedAt").ascending().and(Sort.by("executionId").ascending())));
            case RECENT -> executionRepository.findAllByStateIn(TERMINAL_STATES,
                    PageRequest.of(page, size, Sort.by("endedAt").descending().and(Sort.by("executionId").descending())));
        };
        long firstPosition = (long) page * size + 1;
        List<DispatcherExecutionSummaryResponse> content = new java.util.ArrayList<>(executions.getNumberOfElements());
        for (int index = 0; index < executions.getNumberOfElements(); index++) {
            DispatcherTaskExecution execution = executions.getContent().get(index);
            Long queuePosition = scope == DispatcherExecutionScope.QUEUED ? firstPosition + index : null;
            content.add(summary(execution, queuePosition));
        }
        return new PageResponse<>(content, executions.getTotalElements(), executions.getTotalPages(),
                executions.getNumber(), executions.getSize());
    }

    private DispatcherResourceConfiguration resourceConfiguration() {
        ExecutionBackendType backend = dispatcherProperties.backend();
        return switch (backend) {
            case LOCAL_DOCKER -> new DispatcherResourceConfiguration(
                    backend, localDockerProperties.image(), localDockerProperties.cpus(), localDockerProperties.memory(),
                    heapOption(localDockerProperties.runnerJavaOptions()), null, null,
                    null, null, null, null
            );
            case YARN -> new DispatcherResourceConfiguration(
                    backend, null, null, null, null, yarnProperties.queue(), null,
                    yarnProperties.driverMemory(), yarnProperties.executorMemory(), yarnProperties.executorCores(),
                    yarnProperties.numExecutors()
            );
            case KUBERNETES -> new DispatcherResourceConfiguration(
                    backend, kubernetesProperties.image(), null, null, null, null, kubernetesProperties.namespace(),
                    kubernetesProperties.driverMemory(), kubernetesProperties.executorMemory(),
                    kubernetesProperties.executorCores(), kubernetesProperties.executorInstances()
            );
        };
    }

    private static DispatcherExecutionSummaryResponse summary(DispatcherTaskExecution execution, Long queuePosition) {
        return new DispatcherExecutionSummaryResponse(
                execution.getExecutionId(), execution.getRunId(), execution.getTaskId(), execution.getTaskType(),
                execution.getDefinitionVersion(), execution.getState(), execution.getExternalExecutionId(),
                execution.getTrackingUrl(), execution.getDeadlineAt(), execution.getQueuedAt(),
                execution.getSubmissionStartedAt(), execution.getSubmittedAt(), execution.getStartedAt(),
                execution.getEndedAt(), execution.getLastObservedAt(), execution.getSafeErrorCode(),
                execution.getSafeErrorMessage(), queuePosition
        );
    }

    private static String heapOption(String javaOptions) {
        if (javaOptions == null || javaOptions.isBlank()) return null;
        return javaOptions.lines().flatMap(line -> java.util.Arrays.stream(line.trim().split("\\s+")))
                .filter(value -> value.startsWith("-Xmx"))
                .findFirst().orElse(null);
    }
}
