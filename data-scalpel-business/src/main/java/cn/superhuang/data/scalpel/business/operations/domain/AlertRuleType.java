package cn.superhuang.data.scalpel.business.operations.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "固定告警规则类型：RUN_FAILED 正式运行技术失败或超时；QUALITY_FAILED 质检技术成功但质量结论失败；QUEUE_TOO_LONG 排队超时；RUN_TOO_LONG 批任务执行超时；ENGINE_UNREACHABLE 激活引擎持续不可达；ENGINE_NOT_READY 激活引擎可达但依赖持续未就绪。")
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
