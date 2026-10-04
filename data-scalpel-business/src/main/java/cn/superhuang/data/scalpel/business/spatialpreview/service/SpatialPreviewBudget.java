package cn.superhuang.data.scalpel.business.spatialpreview.service;

/** Shared hard budgets. Reaching a limit is allowed only when the source has ended completely. */
public final class SpatialPreviewBudget {
    public static final int FEATURES=1_000_000;
    public static final long DATA_BYTES=2L*1024*1024*1024;
    public static final int FEATURE_BYTES=16*1024*1024;
    public static final int FEATURE_COORDINATES=1_000_000;
    public static final long DETAIL_BYTES=64L*1024*1024;
    public static final long DETAIL_COORDINATES=4_000_000;
    public static final long DISK_BYTES=10L*1024*1024*1024;
    private SpatialPreviewBudget() { }
    public static final class Exceeded extends RuntimeException {
        public Exceeded(String message) { super(message); }
    }
    public static void deadline(long deadline) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime()>deadline)
            throw new Exceeded("预览处理超过时间预算，请使用已发布的空间服务");
    }
}
