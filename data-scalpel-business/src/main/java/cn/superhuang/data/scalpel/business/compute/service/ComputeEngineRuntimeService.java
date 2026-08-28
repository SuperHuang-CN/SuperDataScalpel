package cn.superhuang.data.scalpel.business.compute.service;

import cn.superhuang.data.scalpel.business.compute.client.ComputeEngineDispatcherClient;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineExecutionResponse;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineRuntimeOverviewResponse;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionScope;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionSummaryResponse;
import cn.superhuang.data.scalpel.contract.execution.DispatcherRuntimeOverviewResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Aggregates Dispatcher runtime facts with Admin task identities without persisting monitoring snapshots. */
@Service
public class ComputeEngineRuntimeService {
    private final ComputeEngineRepository engineRepository;
    private final TaskRunRepository taskRunRepository;
    private final DataTaskRepository taskRepository;
    private final ComputeEngineCredentialCipher credentialCipher;
    private final ComputeEngineDispatcherClient dispatcherClient;
    private final TransactionTemplate readTransaction;

    public ComputeEngineRuntimeService(
            ComputeEngineRepository engineRepository,
            TaskRunRepository taskRunRepository,
            DataTaskRepository taskRepository,
            ComputeEngineCredentialCipher credentialCipher,
            ComputeEngineDispatcherClient dispatcherClient,
            PlatformTransactionManager transactionManager
    ) {
        this.engineRepository = engineRepository;
        this.taskRunRepository = taskRunRepository;
        this.taskRepository = taskRepository;
        this.credentialCipher = credentialCipher;
        this.dispatcherClient = dispatcherClient;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    public ComputeEngineRuntimeOverviewResponse overview(UUID engineId) {
        EngineRoute route = loadRoute(engineId);
        DispatcherRuntimeOverviewResponse response = dispatcherOverview(route);
        return new ComputeEngineRuntimeOverviewResponse(
                route.id(), response.dispatcherInstanceId(), response.backendType(), response.version(),
                response.registrationState(), response.dependencies(), response.admissionCapacity(),
                response.admissionUsage(), response.resourceConfiguration(), response.collectedAt()
        );
    }

    public PageResponse<ComputeEngineExecutionResponse> executions(
            UUID engineId,
            DispatcherExecutionScope scope,
            int page,
            int size
    ) {
        EngineRoute route = loadRoute(engineId);
        dispatcherOverview(route);
        PageResponse<DispatcherExecutionSummaryResponse> dispatcherPage;
        try {
            dispatcherPage = dispatcherClient.executions(
                    route.dispatcherBaseUrl(), credentialCipher.decrypt(route.tokenCiphertext()), scope, page, size);
        } catch (RuntimeException exception) {
            throw unavailable("无法读取 Dispatcher 执行列表", exception);
        }
        if (dispatcherPage == null) throw unavailable("Dispatcher 执行列表响应缺失", null);
        return enrich(dispatcherPage);
    }

    private PageResponse<ComputeEngineExecutionResponse> enrich(
            PageResponse<DispatcherExecutionSummaryResponse> page
    ) {
        return Objects.requireNonNull(readTransaction.execute(status -> {
            Collection<UUID> executionIds = page.content().stream()
                    .map(DispatcherExecutionSummaryResponse::executionId).toList();
            Map<UUID, TaskRun> runsByExecutionId = new HashMap<>();
            if (!executionIds.isEmpty()) {
                taskRunRepository.findAllByExternalExecutionIdIn(executionIds)
                        .forEach(run -> runsByExecutionId.put(run.getExternalExecutionId(), run));
            }
            Collection<UUID> taskIds = page.content().stream()
                    .map(DispatcherExecutionSummaryResponse::taskId).toList();
            Map<UUID, DataTask> tasksById = new HashMap<>();
            taskRepository.findAllById(taskIds).forEach(task -> tasksById.put(task.getId(), task));
            return new PageResponse<>(page.content().stream().map(value -> {
                TaskRun run = runsByExecutionId.get(value.executionId());
                DataTask task = tasksById.get(value.taskId());
                boolean synchronizedRun = run != null && Objects.equals(run.getExecutionRunId(), value.runId());
                return new ComputeEngineExecutionResponse(
                        value.executionId(), value.runId(), value.taskId(),
                        task == null ? null : task.getName(), value.taskType(), value.definitionVersion(), value.state(),
                        synchronizedRun ? run.getId() : null,
                        synchronizedRun ? run.getStatus() : null,
                        synchronizedRun ? run.getTriggerType() : null,
                        synchronizedRun, value.externalExecutionId(), value.trackingUrl(), value.deadlineAt(),
                        value.queuedAt(), value.submissionStartedAt(), value.submittedAt(), value.startedAt(),
                        value.endedAt(), value.lastObservedAt(), value.errorCode(), value.errorMessage(),
                        value.queuePosition()
                );
            }).toList(), page.totalElements(), page.totalPages(), page.page(), page.size());
        }));
    }

    private EngineRoute loadRoute(UUID id) {
        return Objects.requireNonNull(readTransaction.execute(status -> engineRepository.findById(id)
                .map(ComputeEngineRuntimeService::route)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "计算引擎不存在"))));
    }

    private DispatcherRuntimeOverviewResponse dispatcherOverview(EngineRoute route) {
        DispatcherRuntimeOverviewResponse response;
        try {
            response = dispatcherClient.runtimeOverview(
                    route.dispatcherBaseUrl(), credentialCipher.decrypt(route.tokenCiphertext()));
        } catch (RuntimeException exception) {
            throw unavailable("Dispatcher 当前不可用", exception);
        }
        if (response == null || response.dispatcherInstanceId() == null || response.backendType() == null) {
            throw unavailable("Dispatcher 运行态响应缺失", null);
        }
        boolean backendMismatch = !Objects.equals(response.backendType().name(), route.expectedBackendType().name());
        boolean instanceMismatch = route.dispatcherInstanceId() != null
                && !Objects.equals(route.dispatcherInstanceId(), response.dispatcherInstanceId());
        boolean engineMismatch = response.engineId() != null && !Objects.equals(route.id(), response.engineId());
        if (backendMismatch || instanceMismatch || engineMismatch) {
            throw unavailable("Dispatcher 身份或后端类型与计算引擎配置不一致", null);
        }
        return response;
    }

    private static EngineRoute route(ComputeEngine engine) {
        return new EngineRoute(engine.getId(), engine.getDispatcherBaseUrl(), engine.getAccessTokenCiphertext(),
                engine.getExpectedBackendType(), engine.getDispatcherInstanceId());
    }

    private static ResponseStatusException unavailable(String message, RuntimeException cause) {
        return cause == null
                ? new ResponseStatusException(HttpStatus.BAD_GATEWAY, message)
                : new ResponseStatusException(HttpStatus.BAD_GATEWAY, message, cause);
    }

    private record EngineRoute(
            UUID id,
            String dispatcherBaseUrl,
            String tokenCiphertext,
            ComputeBackendType expectedBackendType,
            String dispatcherInstanceId
    ) {
    }
}
