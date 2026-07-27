package cn.superhuang.data.scalpel.dispatcher.backend;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("test")
public class FakeTaskExecutionBackend implements TaskExecutionBackend {
    private final Map<String, FakeExecution> executions = new ConcurrentHashMap<>();

    @Override public ExecutionBackendType type() { return ExecutionBackendType.LOCAL_DOCKER; }
    @Override public BackendReadiness readiness() { return BackendReadiness.up(); }

    @Override
    public BackendSubmission submit(ExecutionLaunch launch) {
        String id = "fake-" + launch.identity().executionId();
        executions.putIfAbsent(id, new FakeExecution(launch.identity()));
        return new BackendSubmission(new ExternalExecutionHandle(type(), id, null));
    }

    @Override
    public BackendStatus inspect(ExternalExecutionHandle handle) {
        FakeExecution execution = executions.get(handle.externalId());
        if (execution == null) return new BackendStatus(BackendExecutionState.UNKNOWN, null, null, null, null);
        int observation = execution.observations.incrementAndGet();
        if (execution.cancelled) return new BackendStatus(BackendExecutionState.CANCELLED, execution.startedAt, Instant.now(), null, null);
        if (observation == 1) {
            execution.startedAt = Instant.now();
            return new BackendStatus(BackendExecutionState.RUNNING, execution.startedAt, null, null, null);
        }
        return new BackendStatus(BackendExecutionState.SUCCEEDED, execution.startedAt, Instant.now(), null, null);
    }

    @Override
    public void cancel(ExternalExecutionHandle handle) {
        FakeExecution execution = executions.get(handle.externalId());
        if (execution != null) execution.cancelled = true;
    }

    @Override public BackendLog collectLog(ExternalExecutionHandle handle) { return new BackendLog(new byte[0], false); }

    @Override
    public Optional<ExternalExecutionHandle> recover(ExecutionIdentity identity) {
        return executions.entrySet().stream().filter(entry -> entry.getValue().identity.equals(identity)).findFirst()
                .map(entry -> new ExternalExecutionHandle(type(), entry.getKey(), null));
    }

    private static final class FakeExecution {
        private final ExecutionIdentity identity;
        private final AtomicInteger observations = new AtomicInteger();
        private volatile Instant startedAt;
        private volatile boolean cancelled;
        private FakeExecution(ExecutionIdentity identity) { this.identity = identity; }
    }
}
