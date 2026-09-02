package cn.superhuang.data.scalpel.business.task.web.response;

import java.util.List;

public record SparkJarOnlineCompilationResponse(
        Status status,
        long durationMs,
        SparkJarOnlineSourceResponse source,
        List<Diagnostic> diagnostics
) {
    public SparkJarOnlineCompilationResponse {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public enum Status { SUCCEEDED, FAILED }
    public enum Severity { ERROR, WARNING, NOTE }

    public record Diagnostic(
            Severity severity,
            String code,
            String message,
            long line,
            long column,
            long endLine,
            long endColumn
    ) {
    }
}
