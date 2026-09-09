package cn.superhuang.data.scalpel.business.operations.domain;

public enum AlertRuleType {
    RUN_FAILED(false, false, 0), QUALITY_FAILED(false, false, 0), QUEUE_TOO_LONG(true, false, 600),
    RUN_TOO_LONG(true, false, 0), ENGINE_UNREACHABLE(true, true, 90), ENGINE_NOT_READY(true, true, 90);
    private final boolean continuous;
    private final boolean engine;
    private final int defaultThreshold;
    AlertRuleType(boolean continuous, boolean engine, int defaultThreshold) {
        this.continuous = continuous; this.engine = engine; this.defaultThreshold = defaultThreshold;
    }
    public boolean continuous() { return continuous; }
    public boolean engine() { return engine; }
    public int defaultThreshold() { return defaultThreshold; }
    public String permission() { return engine ? "compute.engine.view" : "task.view"; }
}
