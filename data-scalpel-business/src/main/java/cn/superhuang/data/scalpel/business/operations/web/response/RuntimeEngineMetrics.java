package cn.superhuang.data.scalpel.business.operations.web.response;
public record RuntimeEngineMetrics(long total, long active, long unreachable, long notReady, long unknown) {}
