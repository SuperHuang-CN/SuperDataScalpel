package cn.superhuang.data.scalpel.business.task.web.response;

import java.util.List;

public record SparkJarTrialRunResponse(
        Status status,
        long compilationDurationMs,
        SparkJarOnlineSourceResponse source,
        List<SparkJarOnlineCompilationResponse.Diagnostic> diagnostics,
        TaskRunResponse run
) {
    public SparkJarTrialRunResponse {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public enum Status {
        COMPILE_FAILED,
        QUEUED
    }
}
