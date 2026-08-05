package cn.superhuang.data.scalpel.contract.service;

import java.util.List;

/** API Studio execution diagnostics returned unchanged through Engine and Admin. */
public record ScriptDraftExecutionResponse(
        Object data,
        String executionId,
        String status,
        Long startedAt,
        Long durationMs,
        ScriptExecutionError error,
        List<String> logs,
        List<ScriptLogEvent> logEvents,
        List<ScriptSqlTrace> sqlTraces,
        ScriptTransactionTrace transaction,
        Integer sqlCount,
        boolean truncated
) {

    public ScriptDraftExecutionResponse {
        logs = logs == null ? List.of() : List.copyOf(logs);
        logEvents = logEvents == null ? List.of() : List.copyOf(logEvents);
        sqlTraces = sqlTraces == null ? List.of() : List.copyOf(sqlTraces);
    }

    public record ScriptExecutionError(
            String code,
            String category,
            String title,
            String message,
            String hint,
            boolean retryable,
            ScriptSourceLocation source,
            ScriptDatabaseError database,
            String technicalMessage
    ) {
    }

    public record ScriptSourceLocation(
            String kind,
            Integer line,
            Integer column,
            Integer endLine,
            Integer endColumn,
            String snippet
    ) {
    }

    public record ScriptDatabaseError(
            String datasource,
            String sqlState,
            Integer vendorCode,
            String constraint
    ) {
    }

    public record ScriptLogEvent(Long timestamp, String level, String source, String message) {
    }

    public record ScriptSqlParameter(String name, String type, String valuePreview, boolean masked) {
    }

    public record ScriptSqlTrace(
            String statementId,
            String parentId,
            String phase,
            String datasource,
            String operation,
            String sqlTemplate,
            List<ScriptSqlParameter> parameters,
            Long durationMs,
            String status,
            Integer rowCount,
            Long rowsAffected,
            boolean cacheHit,
            ScriptExecutionError error,
            ScriptSourceLocation source
    ) {
        public ScriptSqlTrace {
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
        }
    }

    public record ScriptTransactionTrace(boolean enabled, String status) {
    }
}
