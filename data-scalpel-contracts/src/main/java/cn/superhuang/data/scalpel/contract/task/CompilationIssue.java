package cn.superhuang.data.scalpel.contract.task;

public record CompilationIssue(
        String code,
        CompilationSeverity severity,
        String message,
        String nodeId,
        String path
) {
    public static CompilationIssue canvas(String code, String message, String path) {
        return new CompilationIssue(code, CompilationSeverity.ERROR, message, null, path);
    }

    public static CompilationIssue node(String code, String message, String nodeId, String path) {
        return new CompilationIssue(code, CompilationSeverity.ERROR, message, nodeId, path);
    }

    public static CompilationIssue warning(String code, String message, String nodeId, String path) {
        return new CompilationIssue(code, CompilationSeverity.WARNING, message, nodeId, path);
    }
}
