package cn.superhuang.data.scalpel.dispatcher.management;

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
