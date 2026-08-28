package cn.superhuang.data.scalpel.contract.execution;

/** Effective admission limits applied by the currently registered Dispatcher. */
public record DispatcherAdmissionCapacity(
        int maxQueuedExecutions,
        int maxConcurrentSubmissions,
        int maxInFlightApplications
) {
}
