package cn.superhuang.data.scalpel.business.compute.client;

public record DispatcherCapabilities(
        boolean cancellation,
        boolean logCollection,
        boolean restartReconciliation,
        boolean streaming,
        boolean durableCheckpoint
) {
    public DispatcherCapabilities(
            boolean cancellation,
            boolean logCollection,
            boolean restartReconciliation
    ) {
        this(cancellation, logCollection, restartReconciliation, false, false);
    }
}
