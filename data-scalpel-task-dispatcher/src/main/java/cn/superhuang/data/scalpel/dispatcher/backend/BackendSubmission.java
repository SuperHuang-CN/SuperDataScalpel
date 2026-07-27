package cn.superhuang.data.scalpel.dispatcher.backend;

public record BackendSubmission(ExternalExecutionHandle handle) {
    public BackendSubmission {
        if (handle == null) throw new IllegalArgumentException("Backend 提交结果不能为空");
    }
}
