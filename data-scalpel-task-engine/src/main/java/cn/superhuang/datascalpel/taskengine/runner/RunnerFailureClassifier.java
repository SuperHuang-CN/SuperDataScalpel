package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionErrorCategory;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionError;
import cn.superhuang.datascalpel.taskengine.tdengine.tmq.TdEngineTmqException;
import cn.superhuang.datascalpel.taskengine.jdbc.incremental.JdbcIncrementalException;
import org.apache.spark.sql.AnalysisException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        if ("VALUE_MAPPING".equals(context.nodeType())
                && causeMessages(throwable).contains("value_mapping_unmatched_value")) {
            return failure(
                    "VALUE_MAPPING_UNMATCHED_VALUE",
                    ExecutionErrorCategory.CONSTRAINT
            );
        }
        String causeText = causeMessages(throwable);
        TdEngineTmqException tmqFailure = findCause(throwable, TdEngineTmqException.class);
        if (tmqFailure != null) {
            ExecutionErrorCategory category = switch (tmqFailure.code()) {
                case "TDENGINE_TMQ_AUTHENTICATION_FAILED" -> ExecutionErrorCategory.AUTHENTICATION;
                case "TDENGINE_TMQ_NETWORK_ERROR" -> ExecutionErrorCategory.CONNECTION;
                case "TDENGINE_TMQ_ASSIGNMENT_TIMEOUT" -> ExecutionErrorCategory.TIMEOUT;
                case "TDENGINE_TMQ_SCHEMA_MISMATCH", "TDENGINE_TMQ_MESSAGE_TYPE_UNSUPPORTED" ->
                        ExecutionErrorCategory.SCHEMA;
                case "TDENGINE_TMQ_TOPIC_NOT_FOUND", "TDENGINE_TMQ_TOPIC_UNSUPPORTED",
                        "TDENGINE_TMQ_TOPIC_CHANGED", "TDENGINE_TMQ_OFFSET_EXPIRED",
                        "TDENGINE_TMQ_VGROUP_CHANGED" -> ExecutionErrorCategory.CONFIGURATION;
                default -> ExecutionErrorCategory.EXTERNAL_SYSTEM;
            };
            return new Classification(tmqFailure.code(), category, tmqFailure.retryable());
        }
        JdbcIncrementalException incrementalFailure = findCause(
                throwable, JdbcIncrementalException.class);
        if (incrementalFailure != null) {
            ExecutionErrorCategory category = switch (incrementalFailure.code()) {
                case "JDBC_INCREMENTAL_AUTHENTICATION_FAILED" -> ExecutionErrorCategory.AUTHENTICATION;
                case "JDBC_INCREMENTAL_NETWORK_ERROR" -> ExecutionErrorCategory.CONNECTION;
                case "JDBC_INCREMENTAL_TIMEOUT" -> ExecutionErrorCategory.TIMEOUT;
                case "JDBC_INCREMENTAL_SCHEMA_CHANGED" -> ExecutionErrorCategory.SCHEMA;
                case "JDBC_INCREMENTAL_CLOCK_REGRESSION", "JDBC_INCREMENTAL_SOURCE_CHANGED",
                        "JDBC_INCREMENTAL_CHECKPOINT_INVALID", "JDBC_INCREMENTAL_DATABASE_UNSUPPORTED",
                        "JDBC_INCREMENTAL_DRIVER_UNAVAILABLE" -> ExecutionErrorCategory.CONFIGURATION;
                default -> ExecutionErrorCategory.EXTERNAL_SYSTEM;
            };
            return new Classification(
                    incrementalFailure.code(), category, incrementalFailure.retryable());
        }
        AnalysisException analysisFailure = findCause(throwable, AnalysisException.class);
        if (analysisFailure != null && !analysisFailure.isInternalError()) {
            String condition = analysisFailure.getCondition();
            if (condition != null && condition.startsWith("UNRESOLVED_COLUMN.")) {
                return failure("SPARK_UNRESOLVED_COLUMN", ExecutionErrorCategory.SCHEMA);
            }
            if (condition != null && condition.startsWith("AMBIGUOUS_REFERENCE")) {
                return failure("SPARK_AMBIGUOUS_REFERENCE", ExecutionErrorCategory.SCHEMA);
            }
            if (condition != null && condition.startsWith("DATATYPE_MISMATCH")) {
                return failure("SPARK_DATATYPE_MISMATCH", ExecutionErrorCategory.SCHEMA);
            }
            return failure("SPARK_ANALYSIS_FAILED", ExecutionErrorCategory.SCHEMA);
        }
        RunnerExecutionException declaredRunnerFailure = findCause(
                throwable, RunnerExecutionException.class);
        if (declaredRunnerFailure != null
                && ("USER_JOB_CLASS_LOAD_FAILED".equals(declaredRunnerFailure.code())
                || "USER_JOB_CONSTRUCTION_FAILED".equals(declaredRunnerFailure.code()))) {
            return failure(declaredRunnerFailure.code(), ExecutionErrorCategory.CONFIGURATION);
        }
        if (declaredRunnerFailure != null
                && "SNAPSHOT_SYNC_LOCK_TIMEOUT".equals(declaredRunnerFailure.code())) {
            return retryable(declaredRunnerFailure.code(), ExecutionErrorCategory.TIMEOUT);
        }
        if (declaredRunnerFailure != null
                && sqlException == null
                && (declaredRunnerFailure.code().startsWith("SNAPSHOT_SYNC_")
                || "MODEL_SNAPSHOT_SYNC_OUTPUT_FAILED".equals(declaredRunnerFailure.code()))) {
            String code = declaredRunnerFailure.code();
            if ("SNAPSHOT_SYNC_SOURCE_ROW_LIMIT_EXCEEDED".equals(code)
                    || "SNAPSHOT_SYNC_TARGET_ROW_LIMIT_EXCEEDED".equals(code)
                    || "SNAPSHOT_SYNC_MEMORY_LIMIT_EXCEEDED".equals(code)) {
                return failure(code, ExecutionErrorCategory.RESOURCE);
            }
            if ("SNAPSHOT_SYNC_GEOMETRY_COMPARISON_FAILED".equals(code)) {
                return failure(code, ExecutionErrorCategory.SCHEMA);
            }
            if ("SNAPSHOT_SYNC_MAPPING_INVALID".equals(code)
                    || "SNAPSHOT_SYNC_VALUE_CONVERSION_FAILED".equals(code)) {
                return failure(code, ExecutionErrorCategory.SCHEMA);
            }
            if (code.endsWith("_KEY_NULL") || code.endsWith("_KEY_DUPLICATE")
                    || code.contains("DELETE_") || code.contains("EMPTY_SOURCE")) {
                return failure(code, ExecutionErrorCategory.CONSTRAINT);
            }
            return failure(code, ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }
        if (causeText.contains("geometry_construct_kind_mismatch")) {
            return failure(
                    "GEOMETRY_CONSTRUCT_KIND_MISMATCH",
                    ExecutionErrorCategory.SCHEMA
            );
        }
        if (causeText.contains("org.locationtech.jts.io.parseexception")
                || causeText.contains("org.apache.sedona.common.utils.formatutils")
                        && causeText.contains("parse")
                || causeText.contains("invalid wkt")
                || causeText.contains("invalid wkb")
                || causeText.contains("invalid geojson")) {
            return failure(
                    "GEOMETRY_CONSTRUCT_PARSE_FAILED",
                    ExecutionErrorCategory.SCHEMA
            );
        }
        if ("GEOMETRY_REPAIR".equals(context.nodeType())
                && spatialLibraryFailure(causeText)) {
            return failure("GEOMETRY_REPAIR_FAILED", ExecutionErrorCategory.SCHEMA);
        }
        if ("GEOMETRY_BUFFER".equals(context.nodeType())
                && spatialLibraryFailure(causeText)) {
            return failure("GEOMETRY_BUFFER_FAILED", ExecutionErrorCategory.SCHEMA);
        }
        if ("GEOMETRY_EXPLODE".equals(context.nodeType())
                && spatialLibraryFailure(causeText)) {
            return failure("GEOMETRY_EXPLODE_FAILED", ExecutionErrorCategory.SCHEMA);
        }
        if ("SPATIAL_CLIP".equals(context.nodeType())
                && spatialLibraryFailure(causeText)) {
            return failure("SPATIAL_CLIP_FAILED", ExecutionErrorCategory.SCHEMA);
        }
        if ("SPATIAL_AGGREGATE".equals(context.nodeType())
                && spatialLibraryFailure(causeText)) {
            return failure("SPATIAL_AGGREGATE_FAILED", ExecutionErrorCategory.SCHEMA);
        }
        if ("FILE_OUTPUT".equals(context.nodeType())
                && hasCause(throwable, SocketTimeoutException.class)) {
            return retryable("FILE_OUTPUT_CONNECTION_FAILED", ExecutionErrorCategory.CONNECTION);
        }
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
                case "JDBC_QUERY_SCHEMA_STALE", "OUTPUT_MAPPING_INVALID" -> {
                    return failure(runner.code(), ExecutionErrorCategory.CONFIGURATION);
                }
                case "OVERWRITE_DATABASE_NOT_SUPPORTED", "UPSERT_DATABASE_NOT_SUPPORTED",
                        "SPATIAL_JDBC_UNSUPPORTED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.CONFIGURATION);
                }
                case "SPATIAL_TARGET_METADATA_UNAVAILABLE" -> {
                    return failure(runner.code(), ExecutionErrorCategory.SCHEMA);
                }
                case "UPSERT_KEY_NULL", "UPSERT_DUPLICATE_KEY" -> {
                    return failure(runner.code(), ExecutionErrorCategory.CONSTRAINT);
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
                case "USER_JAR_DOWNLOAD_FAILED" -> {
                    return retryable(runner.code(), ExecutionErrorCategory.EXTERNAL_SYSTEM);
                }
                case "USER_JAR_DOWNLOAD_MISSING", "USER_JAR_SIZE_MISMATCH",
                        "USER_JAR_DIGEST_MISMATCH", "USER_JOB_CLASS_NOT_FOUND",
                        "USER_JOB_CLASS_INVALID", "USER_JOB_CLASS_LOAD_FAILED",
                        "USER_JOB_CONSTRUCTOR_INVALID", "USER_JOB_CONSTRUCTION_FAILED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.CONFIGURATION);
                }
                case "USER_JOB_EXECUTION_FAILED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.EXTERNAL_SYSTEM);
                }
                case "STREAMING_JOB_START_TIMEOUT", "STREAMING_STOP_TIMEOUT" -> {
                    return failure(runner.code(), ExecutionErrorCategory.TIMEOUT);
                }
                case "INVALID_SPARK_STREAMING_JAR_MANIFEST",
                        "USER_STREAMING_JOB_CLASS_INVALID",
                        "STREAMING_QUERY_NAME_INVALID", "STREAMING_QUERY_NAME_DUPLICATE",
                        "STREAMING_QUERY_REGISTRATION_INVALID", "STREAMING_QUERY_REQUIRED",
                        "UNREGISTERED_STREAMING_QUERY", "SDK_KAFKA_BINDING_UNAVAILABLE",
                        "STREAMING_SDK_OVERWRITE_NOT_ALLOWED", "STREAMING_SDK_GEOMETRY_NOT_ALLOWED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.CONFIGURATION);
                }
                case "USER_STREAMING_JOB_EXECUTION_FAILED", "USER_STREAMING_JOB_START_FAILED",
                        "USER_STREAMING_JOB_START_INTERRUPTED", "STREAMING_JOB_ON_STOP_FAILED",
                        "STREAMING_QUERY_FAILED", "STREAMING_QUERY_STOPPED_UNEXPECTEDLY" -> {
                    return failure(runner.code(), ExecutionErrorCategory.EXTERNAL_SYSTEM);
                }
                case "FILE_OUTPUT_TARGET_EXISTS" -> {
                    return failure(runner.code(), ExecutionErrorCategory.CONSTRAINT);
                }
                case "SHAPEFILE_GEOMETRY_TYPE_MISMATCH",
                        "SHAPEFILE_EMPTY_GEOMETRY_UNSUPPORTED",
                        "SHAPEFILE_ATTRIBUTE_VALUE_TOO_LONG",
                        "SHAPEFILE_NUMERIC_OVERFLOW" -> {
                    return failure(runner.code(), ExecutionErrorCategory.SCHEMA);
                }
                case "SHAPEFILE_SIZE_LIMIT_EXCEEDED",
                        "SHAPEFILE_LOCAL_STORAGE_EXHAUSTED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.RESOURCE);
                }
                case "GEOPARQUET_GEOMETRY_TYPE_MISMATCH",
                        "GEOPARQUET_EMPTY_GEOMETRY_UNSUPPORTED",
                        "GEOPARQUET_COORDINATE_INVALID",
                        "GEOJSON_GEOMETRY_TYPE_MISMATCH",
                        "GEOJSON_EMPTY_GEOMETRY_UNSUPPORTED",
                        "GEOJSON_COORDINATE_INVALID",
                        "GEOJSON_COORDINATE_OUT_OF_RANGE",
                        "GEOJSON_NON_FINITE_NUMBER" -> {
                    return failure(runner.code(), ExecutionErrorCategory.SCHEMA);
                }
                case "GEOJSON_SIZE_LIMIT_EXCEEDED",
                        "GEOJSON_LOCAL_STORAGE_EXHAUSTED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.RESOURCE);
                }
                case "SHAPEFILE_UPLOAD_FAILED", "GEOJSON_UPLOAD_FAILED" -> {
                    Classification storageFailure = classifyFileOutputStorageFailure(
                            throwable, causeText);
                    return storageFailure == null
                            ? retryable(runner.code(), ExecutionErrorCategory.EXTERNAL_SYSTEM)
                            : storageFailure;
                }
                case "GEOPARQUET_WRITE_FAILED" -> {
                    Classification storageFailure = classifyFileOutputStorageFailure(
                            throwable, causeText);
                    return storageFailure == null
                            ? failure(runner.code(), ExecutionErrorCategory.EXTERNAL_SYSTEM)
                            : storageFailure;
                }
                case "SHAPEFILE_WRITE_FAILED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.EXTERNAL_SYSTEM);
                }
                case "GEOJSON_WRITE_FAILED" -> {
                    return failure(runner.code(), ExecutionErrorCategory.EXTERNAL_SYSTEM);
                }
                case "INVALID_LAUNCH", "INVALID_MANIFEST", "INVALID_WORK_DIRECTORY",
                        "MANIFEST_IDENTITY_MISMATCH", "MANIFEST_DIGEST_MISMATCH",
                        "MANIFEST_TOO_LARGE",
                        "CANVAS_COMPILATION_FAILED", "TABLE_NOT_FOUND", "DUPLICATE_TABLE_NAME",
                        "RUNTIME_DATA_SOURCE_UNAVAILABLE", "MODEL_NOT_FOUND",
                        "OVERWRITE_REQUIRES_MANAGED_MODEL" -> {
                    return failure(runner.code(), ExecutionErrorCategory.CONFIGURATION);
                }
                case "RUNTIME_CONTEXT_UNAVAILABLE" -> {
                    return failure(runner.code(), ExecutionErrorCategory.INTERNAL);
                }
                default -> {
                    if (runner.code().matches("[A-Z][A-Z0-9_]{0,99}") && sqlException == null) {
                        return failure(runner.code(), fallbackCategory(context));
                    }
                }
            }
        }

        if ("FILE_OUTPUT".equals(context.nodeType())) {
            String messages = causeMessages(throwable);
            if (messages.contains("filealreadyexistsexception")
                    || messages.contains("already exists")
                    || messages.contains("path exists")) {
                return failure("FILE_OUTPUT_TARGET_EXISTS", ExecutionErrorCategory.CONSTRAINT);
            }
            if (messages.contains("invalidaccesskeyid")
                    || messages.contains("signaturedoesnotmatch")
                    || messages.contains("invalid access key")) {
                return failure("FILE_OUTPUT_AUTHENTICATION_FAILED", ExecutionErrorCategory.AUTHENTICATION);
            }
            if (messages.contains("accessdenied")
                    || messages.contains("access denied")
                    || messages.contains("status code: 403")) {
                return failure("FILE_OUTPUT_PERMISSION_DENIED", ExecutionErrorCategory.PERMISSION);
            }
            if (hasCause(throwable, ConnectException.class)
                    || hasCause(throwable, SocketException.class)
                    || hasCause(throwable, IOException.class)) {
                return retryable("FILE_OUTPUT_CONNECTION_FAILED", ExecutionErrorCategory.CONNECTION);
            }
            return failure("FILE_OUTPUT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }

        if (hasCause(throwable, ConnectException.class) || hasCause(throwable, SocketException.class)) {
            return retryable("JDBC_CONNECTION_FAILED", ExecutionErrorCategory.CONNECTION);
        }
        if (declaredRunnerFailure != null
                && (declaredRunnerFailure.code().startsWith("SNAPSHOT_SYNC_")
                || "MODEL_SNAPSHOT_SYNC_OUTPUT_FAILED".equals(declaredRunnerFailure.code()))) {
            return failure(declaredRunnerFailure.code(), ExecutionErrorCategory.EXTERNAL_SYSTEM);
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
        if ("JDBC_QUERY_INPUT".equals(context.nodeType())) {
            return failure("JDBC_QUERY_INPUT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
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
        if ("JDBC_SNAPSHOT_SYNC_OUTPUT".equals(context.nodeType())) {
            return failure("SNAPSHOT_SYNC_OUTPUT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
        }
        if ("MODEL_SNAPSHOT_SYNC_OUTPUT".equals(context.nodeType())) {
            return failure("MODEL_SNAPSHOT_SYNC_OUTPUT_FAILED", ExecutionErrorCategory.EXTERNAL_SYSTEM);
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
            case "SPARK_UNRESOLVED_COLUMN" -> unresolvedColumnMessage(
                    findCause(throwable, AnalysisException.class));
            case "SPARK_AMBIGUOUS_REFERENCE" -> "Spark 代码引用的字段存在歧义，请使用 Dataset alias 限定来源";
            case "SPARK_DATATYPE_MISMATCH" -> "Spark 代码中的字段或表达式类型不匹配，请检查参与计算的字段类型";
            case "SPARK_ANALYSIS_FAILED" -> "Spark 无法分析当前代码，请检查字段、表达式和数据类型";
            case "JDBC_STATEMENT_FAILED" -> context.phase() == ExecutionFailurePhase.READ
                    ? withResource("读取数据源表失败", resource)
                    : withResource("写入目标表失败", resource);
            case "TDENGINE_TMQ_TOPIC_NOT_FOUND" -> "TDengine TMQ Topic 不存在";
            case "TDENGINE_TMQ_TOPIC_UNSUPPORTED" -> "TDengine TMQ Topic 类型不受支持";
            case "TDENGINE_TMQ_TOPIC_CHANGED" -> "TDengine TMQ Topic 定义已变化";
            case "TDENGINE_TMQ_AUTHENTICATION_FAILED" -> "TDengine TMQ 认证失败";
            case "TDENGINE_TMQ_NETWORK_ERROR" -> "TDengine TMQ 网络连接失败";
            case "TDENGINE_TMQ_ASSIGNMENT_TIMEOUT" -> "等待 TDengine TMQ VGroup 分配超时";
            case "TDENGINE_TMQ_OFFSET_EXPIRED" -> "TDengine TMQ Checkpoint Offset 已过期";
            case "TDENGINE_TMQ_VGROUP_CHANGED" -> "TDengine TMQ VGroup 已变化，需要新建部署";
            case "TDENGINE_TMQ_SCHEMA_MISMATCH" -> "TDengine TMQ 消息结构与超级表快照不一致";
            case "TDENGINE_TMQ_MESSAGE_TYPE_UNSUPPORTED" -> "TDengine TMQ 返回了不支持的消息类型";
            case "TDENGINE_TMQ_POLL_FAILED" -> "TDengine TMQ 消费失败";
            case "PROCESSOR_EXECUTION_FAILED" -> "处理器节点执行失败";
            case "VALUE_MAPPING_UNMATCHED_VALUE" -> "值映射遇到未配置的非 NULL 值";
            case "GEOMETRY_CONSTRUCT_PARSE_FAILED" -> "Geometry 来源内容解析失败";
            case "GEOMETRY_CONSTRUCT_KIND_MISMATCH" -> "Geometry 实际类型与目标类型不一致";
            case "GEOMETRY_REPAIR_FAILED" -> "Geometry 修复失败";
            case "GEOMETRY_BUFFER_FAILED" -> "Geometry Buffer 计算失败";
            case "GEOMETRY_EXPLODE_FAILED" -> "Geometry 拆分失败";
            case "SPATIAL_CLIP_FAILED" -> "空间裁剪失败";
            case "SPATIAL_AGGREGATE_FAILED" -> "空间聚合失败";
            case "JDBC_INPUT_FAILED" -> "JDBC 输入节点执行失败";
            case "JDBC_QUERY_INPUT_FAILED" -> "JDBC 查询输入节点执行失败";
            case "JDBC_QUERY_SCHEMA_STALE" -> "JDBC 查询 SQL 已修改，需要重新分析";
            case "OUTPUT_MAPPING_INVALID" -> "输出字段映射无法生成写入计划";
            case "OVERWRITE_DATABASE_NOT_SUPPORTED" -> "当前运行数据源不支持普通 JDBC OVERWRITE";
            case "UPSERT_DATABASE_NOT_SUPPORTED" -> "当前数据库不支持 UPSERT";
            case "SPATIAL_JDBC_UNSUPPORTED" -> "当前数据库不支持 Geometry JDBC 读写";
            case "SPATIAL_TARGET_METADATA_UNAVAILABLE" -> "目标 Geometry 字段缺少写入所需元数据";
            case "UPSERT_KEY_NULL" -> "UPSERT Key 不能包含 NULL";
            case "UPSERT_DUPLICATE_KEY" -> "当前批次存在重复 UPSERT Key";
            case "SNAPSHOT_SYNC_SOURCE_KEY_NULL" -> "来源快照 Key 不能包含 NULL";
            case "SNAPSHOT_SYNC_SOURCE_KEY_DUPLICATE" -> "来源快照包含重复 Key";
            case "SNAPSHOT_SYNC_TARGET_KEY_NULL" -> "目标快照 Key 不能包含 NULL";
            case "SNAPSHOT_SYNC_TARGET_KEY_DUPLICATE" -> "目标快照包含重复 Key";
            case "SNAPSHOT_SYNC_SOURCE_ROW_LIMIT_EXCEEDED" -> "来源快照超过单侧行数限制";
            case "SNAPSHOT_SYNC_TARGET_ROW_LIMIT_EXCEEDED" -> "目标快照超过单侧行数限制";
            case "SNAPSHOT_SYNC_MEMORY_LIMIT_EXCEEDED" -> "快照同步估算内存超过限制";
            case "SNAPSHOT_SYNC_EMPTY_SOURCE_DELETE_BLOCKED" -> "来源为空时禁止删除非空目标";
            case "SNAPSHOT_SYNC_DELETE_ROWS_EXCEEDED" -> "候选删除数量超过安全阈值";
            case "SNAPSHOT_SYNC_DELETE_RATIO_EXCEEDED" -> "候选删除比例超过安全阈值";
            case "SNAPSHOT_SYNC_LOCK_TIMEOUT" -> "等待快照同步目标表写锁失败或超时";
            case "SNAPSHOT_SYNC_GEOMETRY_COMPARISON_FAILED" -> "Geometry 无法进行拓扑比较";
            case "SNAPSHOT_SYNC_MAPPING_INVALID" -> "快照同步字段映射无效";
            case "SNAPSHOT_SYNC_VALUE_CONVERSION_FAILED" -> "快照同步字段值无法转换";
            case "SNAPSHOT_SYNC_OUTPUT_FAILED" -> "JDBC 快照同步失败";
            case "MODEL_SNAPSHOT_SYNC_OUTPUT_FAILED" -> "模型快照同步失败";
            case "MODEL_INPUT_FAILED" -> "模型输入节点执行失败";
            case "HTTP_API_INPUT_FAILED" -> "HTTP API 输入节点执行失败";
            case "FILE_DATASET_OBJECT_NOT_FOUND" -> "文件数据集对象不存在";
            case "FILE_DATASET_STORAGE_UNAVAILABLE" -> "文件数据集存储暂不可用";
            case "FILE_DATASET_PARSE_FAILED" -> "文件数据集内容解析失败";
            case "FILE_DATASET_INPUT_FAILED" -> "文件数据集输入节点执行失败";
            case "JDBC_OUTPUT_FAILED" -> "JDBC 输出节点执行失败";
            case "MODEL_OUTPUT_FAILED" -> "模型输出节点执行失败";
            case "FILE_OUTPUT_TARGET_EXISTS" -> "File Output 目标目录已存在";
            case "FILE_OUTPUT_AUTHENTICATION_FAILED" -> "File Output S3 认证失败";
            case "FILE_OUTPUT_PERMISSION_DENIED" -> "File Output S3 权限不足";
            case "FILE_OUTPUT_CONNECTION_FAILED" -> "File Output 无法连接 S3";
            case "FILE_OUTPUT_FAILED" -> "File Output 写入失败";
            case "SHAPEFILE_GEOMETRY_TYPE_MISMATCH" -> "Shapefile Geometry 与目标 Shape 类型不一致";
            case "SHAPEFILE_EMPTY_GEOMETRY_UNSUPPORTED" -> "Shapefile 不支持 Empty Geometry";
            case "SHAPEFILE_ATTRIBUTE_VALUE_TOO_LONG" -> "Shapefile DBF 属性值超过字段宽度";
            case "SHAPEFILE_NUMERIC_OVERFLOW" -> "Shapefile DBF 数值超过字段精度或宽度";
            case "SHAPEFILE_SIZE_LIMIT_EXCEEDED" -> "Shapefile 制品达到 1.8GB 安全限制";
            case "SHAPEFILE_LOCAL_STORAGE_EXHAUSTED" -> "Shapefile 本地暂存空间不足";
            case "SHAPEFILE_WRITE_FAILED" -> "Shapefile 本地制品写入失败";
            case "SHAPEFILE_UPLOAD_FAILED" -> "Shapefile S3 制品提交失败";
            case "GEOPARQUET_GEOMETRY_TYPE_MISMATCH" -> "GeoParquet Geometry 类型与声明不一致";
            case "GEOPARQUET_EMPTY_GEOMETRY_UNSUPPORTED" -> "GeoParquet 不支持 Empty Geometry";
            case "GEOPARQUET_COORDINATE_INVALID" -> "GeoParquet Geometry 包含无效坐标";
            case "GEOPARQUET_WRITE_FAILED" -> "GeoParquet 写出失败";
            case "GEOJSON_GEOMETRY_TYPE_MISMATCH" -> "GeoJSON Geometry 类型与声明不一致";
            case "GEOJSON_EMPTY_GEOMETRY_UNSUPPORTED" -> "GeoJSON 不支持 Empty Geometry";
            case "GEOJSON_COORDINATE_INVALID" -> "GeoJSON Geometry 坐标结构无效";
            case "GEOJSON_COORDINATE_OUT_OF_RANGE" -> "GeoJSON 坐标超出 WGS84 经纬度范围";
            case "GEOJSON_NON_FINITE_NUMBER" -> "GeoJSON 包含 NaN 或 Infinity";
            case "GEOJSON_SIZE_LIMIT_EXCEEDED" -> "GeoJSON 文件达到 1.8GB 安全限制";
            case "GEOJSON_LOCAL_STORAGE_EXHAUSTED" -> "GeoJSON 本地暂存空间不足";
            case "GEOJSON_WRITE_FAILED" -> "GeoJSON 本地制品写入失败";
            case "GEOJSON_UPLOAD_FAILED" -> "GeoJSON S3 制品提交失败";
            case "API_AUTHENTICATION_FAILED" -> "HTTP API 认证失败";
            case "API_TOKEN_MISSING", "API_TOKEN_REQUEST_FAILED" -> "HTTP API 运行时 Token 获取失败";
            case "API_NETWORK_ERROR" -> "无法连接 HTTP API";
            case "API_HTTP_ERROR" -> "HTTP API 返回错误状态";
            case "API_MAX_DURATION_EXCEEDED", "API_ASYNC_TIMEOUT" -> "HTTP API 请求超时";
            case "API_REQUIRED_FIELD_MISSING", "API_FIELD_CONVERSION_FAILED" ->
                    "HTTP API 响应与输出 Schema 不匹配";
            case "API_BATCH_STAGING_FAILED" -> "HTTP API 分批暂存失败";
            case "USER_JAR_DOWNLOAD_FAILED" -> "用户 JAR 下载失败";
            case "USER_JAR_DOWNLOAD_MISSING" -> "Spark JAR 任务缺少用户 JAR 下载信息";
            case "USER_JAR_SIZE_MISMATCH" -> "用户 JAR 大小校验失败";
            case "USER_JAR_DIGEST_MISMATCH" -> "用户 JAR SHA-256 校验失败";
            case "USER_JOB_CLASS_NOT_FOUND" -> "用户 Job Class 无法加载";
            case "USER_JOB_CLASS_LOAD_FAILED" -> "用户 Job Class 初始化或链接失败";
            case "USER_JOB_CLASS_INVALID" -> "用户 Job Class 必须是 public 并实现 SparkBatchJob";
            case "USER_JOB_CONSTRUCTOR_INVALID" -> "用户 Job Class 必须提供 public 无参构造方法";
            case "USER_JOB_CONSTRUCTION_FAILED" -> "用户 Job Class 构造失败";
            case "USER_JOB_EXECUTION_FAILED" -> "用户 Spark 作业执行失败";
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

    /**
     * Spark's formatted exception message can contain a logical plan, SQL text and literals. The
     * condition parameters for an unresolved column only carry schema identifiers, so expose a
     * deliberately small, validated subset instead of the raw Spark message.
     */
    private static String unresolvedColumnMessage(AnalysisException exception) {
        Map<String, String> parameters = exception == null || exception.getMessageParameters() == null
                ? Map.of() : exception.getMessageParameters();
        String column = safeSparkIdentifier(parameters.get("objectName"));
        if (column == null) {
            return "Spark 代码引用了不存在的字段，请检查 Dataset Schema";
        }
        List<String> proposals = safeSparkIdentifiers(parameters.get("proposal"), 5);
        if (proposals.isEmpty()) {
            return "Spark 代码引用的字段 %s 不存在，请检查 Dataset Schema".formatted(column);
        }
        return "Spark 代码引用的字段 %s 不存在。可用字段建议：%s。"
                .formatted(column, String.join("、", proposals));
    }

    private static List<String> safeSparkIdentifiers(String value, int maximum) {
        if (value == null || value.isBlank() || maximum < 1) return List.of();
        List<String> result = new ArrayList<>();
        for (String token : value.split(",")) {
            String identifier = safeSparkIdentifier(token);
            if (identifier != null && !result.contains(identifier)) {
                result.add(identifier);
                if (result.size() == maximum) break;
            }
        }
        return List.copyOf(result);
    }

    private static String safeSparkIdentifier(String value) {
        if (value == null) return null;
        String identifier = value.trim();
        while (identifier.length() >= 2
                && ((identifier.startsWith("`") && identifier.endsWith("`"))
                || (identifier.startsWith("[") && identifier.endsWith("]")))) {
            identifier = identifier.substring(1, identifier.length() - 1).trim();
        }
        return identifier.matches("[\\p{L}\\p{N}_$]{1,128}") ? identifier : null;
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
        return "JOIN".equals(nodeType)
                || "GEOMETRY_CONSTRUCT".equals(nodeType)
                || "SPATIAL_TRANSFORM".equals(nodeType)
                || "GEOMETRY_VALIDATE".equals(nodeType)
                || "GEOMETRY_REPAIR".equals(nodeType)
                || "GEOMETRY_BUFFER".equals(nodeType)
                || "GEOMETRY_EXPLODE".equals(nodeType)
                || "SPATIAL_MEASURE".equals(nodeType)
                || "GEOMETRY_SERIALIZE".equals(nodeType)
                || "SPATIAL_CLIP".equals(nodeType)
                || "SPATIAL_AGGREGATE".equals(nodeType)
                || "SPATIAL_JOIN".equals(nodeType)
                || "STREAM_JOIN".equals(nodeType)
                || "RENAME".equals(nodeType)
                || "FILTER".equals(nodeType)
                || "SELECT_COLUMNS".equals(nodeType)
                || "DERIVE_COLUMNS".equals(nodeType)
                || "TYPE_CAST".equals(nodeType)
                || "AGGREGATE".equals(nodeType)
                || "UNION".equals(nodeType)
                || "DEDUPLICATE".equals(nodeType)
                || "NULL_HANDLING".equals(nodeType)
                || "VALUE_MAPPING".equals(nodeType)
                || "MASK_FIELDS".equals(nodeType)
                || "JSON_EXTRACT".equals(nodeType)
                || "WINDOW".equals(nodeType)
                || "TOP_N".equals(nodeType);
    }

    private static boolean spatialLibraryFailure(String causeText) {
        return causeText.contains("org.apache.sedona.")
                || causeText.contains("org.locationtech.jts.");
    }

    private static Classification classifyFileOutputStorageFailure(
            Throwable throwable,
            String causeText
    ) {
        if (causeText.contains("filealreadyexistsexception")
                || causeText.contains("already exists")
                || causeText.contains("path exists")) {
            return failure("FILE_OUTPUT_TARGET_EXISTS", ExecutionErrorCategory.CONSTRAINT);
        }
        if (causeText.contains("invalidaccesskeyid")
                || causeText.contains("signaturedoesnotmatch")
                || causeText.contains("invalid access key")) {
            return failure(
                    "FILE_OUTPUT_AUTHENTICATION_FAILED",
                    ExecutionErrorCategory.AUTHENTICATION
            );
        }
        if (causeText.contains("accessdenied")
                || causeText.contains("access denied")
                || causeText.contains("status code: 403")) {
            return failure("FILE_OUTPUT_PERMISSION_DENIED", ExecutionErrorCategory.PERMISSION);
        }
        if (hasCause(throwable, ConnectException.class)
                || hasCause(throwable, SocketException.class)
                || hasCause(throwable, IOException.class)) {
            return retryable("FILE_OUTPUT_CONNECTION_FAILED", ExecutionErrorCategory.CONNECTION);
        }
        return null;
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

    private static String causeMessages(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        for (Throwable current : causes(throwable)) {
            result.append(current.getClass().getName()).append(' ');
            if (current.getMessage() != null) {
                result.append(current.getMessage()).append(' ');
            }
        }
        return result.toString().toLowerCase(Locale.ROOT);
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
