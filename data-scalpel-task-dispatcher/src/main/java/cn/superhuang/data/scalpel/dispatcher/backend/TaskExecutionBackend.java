package cn.superhuang.data.scalpel.dispatcher.backend;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;

import java.util.Optional;

public interface TaskExecutionBackend {
    ExecutionBackendType type();
    BackendReadiness readiness();
    BackendSubmission submit(ExecutionLaunch launch) throws BackendException;
    BackendStatus inspect(ExternalExecutionHandle handle) throws BackendException;
    default BackendStatus inspect(ExternalExecutionHandle handle, ExecutionIdentity identity) throws BackendException {
        return inspect(handle);
    }
    void cancel(ExternalExecutionHandle handle) throws BackendException;
    default void cancel(ExternalExecutionHandle handle, ExecutionIdentity identity) throws BackendException {
        cancel(handle);
    }
    default void forceTerminate(ExternalExecutionHandle handle, ExecutionIdentity identity) throws BackendException {
        cancel(handle, identity);
    }
    BackendLog collectLog(ExternalExecutionHandle handle) throws BackendException;
    default BackendLog collectRecentLog(ExternalExecutionHandle handle) throws BackendException {
        return BackendLogWindow.recent(collectLog(handle), 2_000, 1024 * 1024);
    }
    Optional<ExternalExecutionHandle> recover(ExecutionIdentity identity) throws BackendException;
    default void cleanup(ExternalExecutionHandle handle) throws BackendException { }
    default void cleanup(ExternalExecutionHandle handle, ExecutionIdentity identity) throws BackendException {
        cleanup(handle);
    }
    default void cleanup(ExecutionIdentity identity) throws BackendException { }
}
