package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionError;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class RunnerFailureClassifier {

    TaskExecutionError classify(Throwable throwable, RunnerFailureContext context) {
        RunnerFailureContext safeContext = context == null
                ? RunnerFailureContext.task(ExecutionFailurePhase.PREPARE) : context;
        FileDatasetReadException fileFailure = findCause(throwable, FileDatasetReadException.class);
        if (fileFailure != null && fileFailure.nodeId() != null) {
            safeContext = new RunnerFailureContext(
                    fileFailure.nodeId(),
                    "FILE_DATASET_INPUT",
                    fileFailure.nodeName(),
                    ExecutionFailurePhase.READ,
                    null
            );
        }
        SQLException sqlException = findSqlException(throwable);
        String sqlState = normalizeSqlState(sqlException == null ? null : sqlException.getSQLState());
        Classification classification = classify(throwable, sqlException, sqlState, safeContext);
        return new TaskExecutionError(
                classification.code(), safeMessage(classification.code(), throwable, safeContext),
                classification.category(), classification.retryable(), safeContext.nodeId(),
                safeContext.nodeType(), safeContext.nodeName(), safeContext.phase(), sqlState, UUID.randomUUID());
    }

    private static Classification classify(
            Throwable throwable,
            SQLException sqlException,
            String sqlState,
            RunnerFailureContext context
    ) {
        if ("57014".equals(sqlState) || hasCause(throwable, SQLTimeoutException.class)
                || hasCause(throwable, SocketTimeoutException.class)) {
            return retryable("JDBC_TIMEOUT", ExecutionErrorCategory.TIMEOUT);
        }
        if ("42501".equals(sqlState)) return failure("JDBC_PERMISSION_DENIED", ExecutionErrorCategory.PERMISSION);
        if (startsWith(sqlState, "28")) return failure("JDBC_AUTHENTICATION_FAILED", ExecutionErrorCategory.AUTHENTICATION);
        if (startsWith(sqlState, "08")) return retryable("JDBC_CONNECTION_FAILED", ExecutionErrorCategory.CONNECTION);
        if (startsWith(sqlState, "23")) return failure("JDBC_CONSTRAINT_VIOLATION", ExecutionErrorCategory.CONSTRAINT);
        if (hasCause(throwable, SQLFeatureNotSupportedException.class) || unsupportedType(throwable)) {
            return failure("JDBC_UNSUPPORTED_TYPE", ExecutionErrorCategory.SCHEMA);
        }
        if (hasCause(throwable, OutOfMemoryError.class)) {
            return failure("RUNNER_RESOURCE_EXHAUSTED", ExecutionErrorCategory.RESOURCE);
        }
        if (hasCause(throwable, InterruptedException.class)) {
            return failure("EXECUTION_CANCELLED", ExecutionErrorCategory.CANCELLED);
        }
        FileDatasetReadException fileFailure = findCause(throwable, FileDatasetReadException.class);
        if (fileFailure != null) {
            ExecutionErrorCategory category = switch (fileFailure.code()) {
                case "FILE_DATASET_STORAGE_UNAVAILABLE" -> ExecutionErrorCategory.CONNECTION;
                case "FILE_DATASET_PARSE_FAILED" -> ExecutionErrorCategory.SCHEMA;
                default -> ExecutionErrorCategory.EXTERNAL_SYSTEM;
            };
            return new Classification(fileFailure.code(), category, fileFailure.retryable());
        }

        RunnerExecutionException runner = findCause(throwable, RunnerExecutionException.class);
        if (runner != null) {
            switch (runner.code()) {
                case "RUNTIME_SCHEMA_MISMATCH" -> {
                    return failure("RUNTIME_SCHEMA_MISMATCH", ExecutionErrorCategory.SCHEMA);
                }
                case "FILE_DATASET_INPUT_FAILED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.CONFIGURATION);
                }
                case "EXECUTION_DEADLINE_EXCEEDED" -> {
                    return failure("EXECUTION_DEADLINE_EXCEEDED", ExecutionErrorCategory.TIMEOUT);
                }
                case "EXECUTION_CANCELLED" -> {
                    return failure("EXECUTION_CANCELLED", ExecutionErrorCategory.CANCELLED);
                }
                case "API_AUTHENTICATION_FAILED", "API_TOKEN_MISSING" -> {
                    return failure(runner.code(), ExecutionErrorCategory.AUTHENTICATION);
                }
                case "API_NETWORK_ERROR", "API_TOKEN_REQUEST_FAILED", "API_HTTP_ERROR" -> {
                    return retryable(runner.code(), ExecutionErrorCategory.EXTERNAL_SYSTEM);
                }
                case "API_MAX_DURATION_EXCEEDED", "API_ASYNC_TIMEOUT" -> {
                    return failure(runner.code(), ExecutionErrorCategory.TIMEOUT);
                }
                case "API_REQUIRED_FIELD_MISSING", "API_FIELD_CONVERSION_FAILED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.SCHEMA);
                }
                case "API_BATCH_STAGING_FAILED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.RESOURCE);
                }
                case "MANIFEST_DOWNLOAD_FAILED", "RESULT_UPLOAD_FAILED" -> {
                    return retryable(runner.code(), ExecutionErrorCategory.EXTERNAL_SYSTEM);
                }
                case "INVALID_LAUNCH", "INVALID_MANIFEST", "INVALID_WORK_DIRECTORY",
                        "MANIFEST_IDENTITY_MISMATCH", "MANIFEST_DIGEST_MISMATCH",
                        "MANIFEST_TOO_LARGE",
                        "CANVAS_COMPILATION_FAILED", "TABLE_NOT_FOUND", "DUPLICATE_TABLE_NAME",
                        "RUNTIME_DATA_SOURCE_UNAVAILABLE", "MODEL_NOT_FOUND",
                        "OVERWRITE_REQUIRES_MANAGED_MODEL" -> {
                    return failure(runner.code(), ExecutionErrorCategory.CONFIGURATION);
                }
                default -> {
                    if (runner.code().matches("[A-Z][A-Z0-9_]{0,99}") && sqlException == null) {
                        return failure(runner.code(), fallbackCategory(context));
                    }
                }
            }
        }

        if (hasCause(throwable, ConnectException.class) || hasCause(throwable, SocketException.class)) {
            return retryable("JDBC_CONNECTION_FAILED", ExecutionErrorCategory.CONNECTION);
        }
        if (sqlException != null) {
            boolean transientFailure = sqlException instanceof SQLTransientException;
            return new Classification("JDBC_STATEMENT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM,
                    transientFailure);
        }
        if (context.phase() == ExecutionFailurePhase.DELIVERY
                || context.phase() == ExecutionFailurePhase.DISPATCH) {
            return retryable("RUNNER_DELIVERY_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }
        if (hasCause(throwable, IOException.class)) {
            return retryable("EXTERNAL_SYSTEM_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }
        if (isProcessor(context.nodeType())) {
            return failure("PROCESSOR_EXECUTION_FAILED", ExecutionErrorCategory.INTERNAL);
        }
        if ("JDBC_INPUT".equals(context.nodeType())) {
            return failure("JDBC_INPUT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }
        if ("MODEL_INPUT".equals(context.nodeType())) {
            return failure("MODEL_INPUT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }
        if ("HTTP_API_INPUT".equals(context.nodeType())) {
            return failure("HTTP_API_INPUT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }
        if ("FILE_DATASET_INPUT".equals(context.nodeType())) {
            return failure("FILE_DATASET_INPUT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }
        if ("JDBC_OUTPUT".equals(context.nodeType())) {
            return failure("JDBC_OUTPUT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }
        if ("MODEL_OUTPUT".equals(context.nodeType())) {
            return failure("MODEL_OUTPUT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }
        return failure("RUNNER_INTERNAL_ERROR", ExecutionErrorCategory.INTERNAL);
    }

    private static String safeMessage(String code, Throwable throwable, RunnerFailureContext context) {
        String resource = context.resourceName() == null || context.resourceName().isBlank()
                ? null : RunnerLogSanitizer.safeMessage(context.resourceName(), null);
        return switch (code) {
            case "JDBC_PERMISSION_DENIED" -> context.phase() == ExecutionFailurePhase.READ
                    ? withResource("数据源用户无权读取表", resource)
                    : withResource("数据源用户无权写入目标表", resource);
            case "JDBC_AUTHENTICATION_FAILED" -> "数据源认证失败";
            case "JDBC_CONNECTION_FAILED" -> "无法连接数据源";
            case "JDBC_TIMEOUT" -> "数据源操作超时";
            case "JDBC_CONSTRAINT_VIOLATION" -> "写入目标表时违反数据库约束";
            case "JDBC_UNSUPPORTED_TYPE" -> "数据源字段类型不受支持";
            case "RUNTIME_SCHEMA_MISMATCH" -> "运行时表结构与任务定义不一致";
            case "JDBC_STATEMENT_FAILED" -> context.phase() == ExecutionFailurePhase.READ
                    ? withResource("读取数据源表失败", resource)
                    : withResource("写入目标表失败", resource);
            case "PROCESSOR_EXECUTION_FAILED" -> "处理器节点执行失败";
            case "JDBC_INPUT_FAILED" -> "JDBC 输入节点执行失败";
            case "MODEL_INPUT_FAILED" -> "模型输入节点执行失败";
            case "HTTP_API_INPUT_FAILED" -> "HTTP API 输入节点执行失败";
            case "FILE_DATASET_OBJECT_NOT_FOUND" -> "文件数据集对象不存在";
            case "FILE_DATASET_STORAGE_UNAVAILABLE" -> "文件数据集存储暂不可用";
            case "FILE_DATASET_PARSE_FAILED" -> "文件数据集内容解析失败";
            case "FILE_DATASET_INPUT_FAILED" -> "文件数据集输入节点执行失败";
            case "JDBC_OUTPUT_FAILED" -> "JDBC 输出节点执行失败";
            case "MODEL_OUTPUT_FAILED" -> "模型输出节点执行失败";
            case "API_AUTHENTICATION_FAILED" -> "HTTP API 认证失败";
            case "API_TOKEN_MISSING", "API_TOKEN_REQUEST_FAILED" -> "HTTP API 运行时 Token 获取失败";
            case "API_NETWORK_ERROR" -> "无法连接 HTTP API";
            case "API_HTTP_ERROR" -> "HTTP API 返回错误状态";
            case "API_MAX_DURATION_EXCEEDED", "API_ASYNC_TIMEOUT" -> "HTTP API 请求超时";
            case "API_REQUIRED_FIELD_MISSING", "API_FIELD_CONVERSION_FAILED" ->
                    "HTTP API 响应与输出 Schema 不匹配";
            case "API_BATCH_STAGING_FAILED" -> "HTTP API 分批暂存失败";
            case "RUNNER_INTERNAL_ERROR" -> "Task Runner 内部错误";
            case "RUNNER_RESOURCE_EXHAUSTED" -> "Task Runner 运行资源不足";
            case "RUNNER_DELIVERY_FAILED" -> "Task Runner 无法投递执行结果";
            case "EXTERNAL_SYSTEM_FAILED" -> "外部系统操作失败";
            default -> RunnerLogSanitizer.safeMessage(
                    findCause(throwable, RunnerExecutionException.class) == null
                            ? null : findCause(throwable, RunnerExecutionException.class).getMessage(),
                    "任务执行失败");
        };
    }

    private static String withResource(String prefix, String resource) {
        return resource == null ? prefix : prefix + " " + resource;
    }

    private static ExecutionErrorCategory fallbackCategory(RunnerFailureContext context) {
        if (context.nodeId() == null) return ExecutionErrorCategory.INTERNAL;
        return isProcessor(context.nodeType())
                ? ExecutionErrorCategory.INTERNAL : ExecutionErrorCategory.EXTERNAL_SYSTEM;
    }

    private static boolean isProcessor(String nodeType) {
        return "JOIN".equals(nodeType) || "RENAME".equals(nodeType);
    }

    private static boolean unsupportedType(Throwable throwable) {
        for (Throwable current : causes(throwable)) {
            if (current instanceof UnsupportedOperationException) return true;
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("unsupported")
                    && message.toLowerCase(Locale.ROOT).contains("type")) return true;
        }
        return false;
    }

    private static SQLException findSqlException(Throwable throwable) {
        SQLException first = null;
        SQLException firstWithState = null;
        for (Throwable current : causes(throwable)) {
            if (current instanceof SQLException sql) {
                SQLException candidate = sql;
                while (candidate != null) {
                    if (first == null) first = candidate;
                    String state = normalizeSqlState(candidate.getSQLState());
                    if (firstWithState == null && state != null) firstWithState = candidate;
                    if ("42501".equals(state) || "57014".equals(state)
                            || startsWith(state, "08") || startsWith(state, "28")
                            || startsWith(state, "23")) return candidate;
                    candidate = candidate.getNextException();
                }
            }
        }
        return firstWithState == null ? first : firstWithState;
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        for (Throwable current : causes(throwable)) {
            if (type.isInstance(current)) return type.cast(current);
        }
        return null;
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        return findCause(throwable, type) != null;
    }

    private static Iterable<Throwable> causes(Throwable throwable) {
        if (throwable == null) return Collections.emptyList();
        ArrayDeque<Throwable> pending = new ArrayDeque<>();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        pending.add(throwable);
        java.util.List<Throwable> result = new java.util.ArrayList<>();
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            result.add(current);
            if (current.getCause() != null) pending.addLast(current.getCause());
            if (current instanceof SQLException sql && sql.getNextException() != null) {
                pending.addLast(sql.getNextException());
            }
        }
        return result;
    }

    private static String normalizeSqlState(String value) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.matches("[0-9A-Z]{5}") ? normalized : null;
    }

    private static boolean startsWith(String value, String prefix) {
        return value != null && value.startsWith(prefix);
    }

    private static Classification failure(String code, ExecutionErrorCategory category) {
        return new Classification(code, category, false);
    }

    private static Classification retryable(String code, ExecutionErrorCategory category) {
        return new Classification(code, category, true);
    }

    private record Classification(String code, ExecutionErrorCategory category, boolean retryable) {
    }
}
