package cn.superhuang.data.scalpel.contract.execution;

/** Current execution counts derived from the Dispatcher execution store. */
public record DispatcherAdmissionUsage(
        long queued,
        long submitting,
        long submitted,
        long running,
        long cancelRequested,
        long inFlight
) {
}
