package cn.superhuang.data.scalpel.contract.task;

public record SparkJarSourceDiagnostic(
        SparkJarSourceDiagnosticSeverity severity,
        String code,
        String message,
        long line,
        long column,
        long endLine,
        long endColumn
) {
}
