package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperationContext;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperationResult;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperatorRegistry;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperators;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedOutput;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasCompilation;
import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasGraphPlan;
import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasTaskCompiler;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.CompilationSeverity;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionState;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeJdbcConnection;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionError;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionState;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.DataFrameReader;
import org.apache.spark.sql.DataFrameWriter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class CanvasTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CanvasTaskExecutor.class);
    private final CanvasTaskCompiler compiler = new CanvasTaskCompiler();
    private final CanvasNodeOperatorRegistry nodeOperators = CanvasNodeOperators.builtInRegistry();
    private final RunnerFailureClassifier failureClassifier = new RunnerFailureClassifier();

    TaskExecutionResult execute(TaskExecutionManifest manifest) {
        return execute(manifest, RunnerSparkMode.LOCAL, ignored -> { });
    }

    TaskExecutionResult execute(
            TaskExecutionManifest manifest,
            RunnerSparkMode sparkMode,
            Consumer<String> sparkStarted
    ) {
        validateManifest(manifest);
        if (sparkMode == null || sparkStarted == null) {
            throw new RunnerExecutionException("INVALID_LAUNCH", "Runner Spark 启动模式无效", null);
        }
        Instant startedAt = Instant.now();
        SparkSession.Builder builder = SparkSession.builder()
                .appName("DataScalpel Task Runner " + manifest.execution().executionId())
                .config("spark.ui.enabled", "false")
                .config("spark.sql.shuffle.partitions", "4")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .config("spark.speculation", "false");
        if (sparkMode == RunnerSparkMode.LOCAL) builder.master("local[*]");
        SparkSession spark = builder.getOrCreate();
        try (SparkOutputMetricsCollector metricsCollector = new SparkOutputMetricsCollector(spark)) {
            sparkStarted.accept(spark.sparkContext().applicationId());
            MetadataIndex metadataIndex = MetadataIndex.create(manifest.metadataSnapshot());
            CanvasCompilation compilation = compiler.compile(
                    manifest.task().definition(),
                    metadataIndex,
                    spark.newSession(),
                    new AtomicBoolean()
            );
            checkDeadline(manifest);
            if (!compilation.valid()) {
                String message = compilation.canvasIssues().stream()
                        .filter(issue -> issue.severity() == CompilationSeverity.ERROR)
                        .map(issue -> issue.code() + ": " + issue.message())
                        .findFirst()
                        .orElseGet(() -> compilation.nodeResults().stream()
                                .flatMap(result -> result.issues().stream())
                                .filter(issue -> issue.severity() == CompilationSeverity.ERROR)
                                .map(issue -> issue.code() + ": " + issue.message())
                                .findFirst().orElse("Canvas 编译失败"));
                throw new RunnerExecutionException("CANVAS_COMPILATION_FAILED", message, null);
            }
            return executeCompiled(manifest, metadataIndex, spark, metricsCollector, startedAt);
        } catch (Throwable throwable) {
            TaskExecutionError error = failureClassifier.classify(
                    throwable, RunnerFailureContext.task(ExecutionFailurePhase.PREPARE));
            logTaskFailureDetail(manifest, error, throwable);
            return failedResult(manifest, startedAt, List.of(), error);
        } finally {
            spark.stop();
        }
    }

    private TaskExecutionResult executeCompiled(
            TaskExecutionManifest manifest,
            MetadataIndex metadataIndex,
            SparkSession spark,
            SparkOutputMetricsCollector metricsCollector,
            Instant startedAt
    ) {
        CanvasGraphPlan plan = CanvasGraphPlan.create(manifest.task().definition());
        Map<UUID, RuntimeDataSource> runtimeSources = runtimeSources(manifest.runtimeDataSources());
        List<Map<String, SparkCanvasTable>> propagated = new ArrayList<>();
        for (int ignored = 0; ignored < manifest.task().definition().nodes().size(); ignored++) propagated.add(Map.of());
        List<PreparedOutput> preparedOutputs = new ArrayList<>();
        List<NodeExecutionResult> nodeResults = new ArrayList<>();
        RuntimeCanvasNodeDataAccess dataAccess = new RuntimeCanvasNodeDataAccess(
                spark,
                runtimeSources,
                manifest.runtimeFileStorage(),
                manifest.runtimeFileInputs()
        );

        try {
        for (int nodeIndex : plan.topologicalOrder()) {
            CanvasNodeDefinition node = plan.nodeAt(nodeIndex);
            ExecutionFailurePhase phase = phase(node);
            RunnerFailureContext failureContext = RunnerFailureContext.node(
                    node, phase, resourceName(node, runtimeSources, metadataIndex));
            Instant nodeStartedAt = Instant.now();
            logNodeStart(manifest, node, phase, nodeSummary(node, metadataIndex));
            try {
                checkDeadline(manifest);
                Map<String, SparkCanvasTable> inputs = mergeInputs(
                        plan.predecessorsOf(nodeIndex), propagated, node.id());
                CanvasNodeOperationResult operation = executeNodeOperator(
                        node, inputs, spark, metadataIndex, dataAccess);
                if (node instanceof ModelOutputNodeDefinition
                        || node instanceof JdbcOutputNodeDefinition) {
                    if (operation.preparedOutput() == null) {
                        throw new RunnerExecutionException(
                                "OUTPUT_NOT_PREPARED", "输出节点未生成写入计划", node.id());
                    }
                    preparedOutputs.add(preparedOutput(operation.preparedOutput(), nodeStartedAt));
                } else {
                    propagated.set(nodeIndex, operation.propagatedTables());
                    nodeResults.add(success(
                            node, phase, nodeStartedAt, null, nodePreparedMessage(node)));
                    logNodeSuccess(manifest, node, phase, nodeStartedAt, null);
                }
            } catch (Throwable throwable) {
                TaskExecutionError error = failureClassifier.classify(throwable, failureContext);
                nodeResults.add(failed(node, phase, nodeStartedAt, error));
                logNodeFailure(manifest, node, phase, nodeStartedAt, error, throwable);
                return failedResult(manifest, startedAt, orderedResults(plan, nodeResults), error);
            }
        }

        Long affectedRows = 0L;
        for (PreparedOutput output : preparedOutputs) {
            CanvasNodeDefinition node = output.node();
            RunnerFailureContext failureContext = RunnerFailureContext.node(
                    node, ExecutionFailurePhase.WRITE,
                    output.displayTarget());
            String jobGroup = "datascalpel:" + manifest.execution().executionId()
                    + ":" + manifest.execution().attempt() + ":" + node.id();
            SparkOutputMetricsCollector.ObservedOutput observed = null;
            try {
                checkDeadline(manifest);
                observed = metricsCollector.observe(
                        manifest.execution().executionId(), manifest.execution().attempt(),
                        node.id(), output.dataset());
                spark.sparkContext().setJobGroup(
                        jobGroup, "DataScalpel " + node.nodeType() + " " + node.id(), true);
                if (output.writeMode() == JdbcWriteMode.OVERWRITE) {
                    truncate(output.runtimeDataSource(), output.qualifiedTableName());
                }
                write(output.runtimeDataSource(), output.qualifiedTableName(), observed.dataset());
                SparkOutputMetricsCollector.OutputWriteMetrics metrics = metricsCollector.completeSuccess(observed);
                Long previousAffectedRows = affectedRows;
                affectedRows = addAffectedRows(affectedRows, metrics.rowsWritten());
                if (metrics.metricAvailable() && previousAffectedRows != null && affectedRows == null) {
                    LOGGER.warn(
                            "event=OUTPUT_ROWS_OVERFLOW executionId={} nodeId={}",
                            manifest.execution().executionId(), safeLogValue(node.id()));
                }
                String message = outputSuccessMessage(node, metrics.metricAvailable());
                nodeResults.add(success(
                        node, ExecutionFailurePhase.WRITE, output.startedAt(), metrics.rowsWritten(), message));
                logNodeSuccess(
                        manifest, node, ExecutionFailurePhase.WRITE, output.startedAt(), metrics.rowsWritten());
            } catch (Throwable throwable) {
                if (observed != null) metricsCollector.completeFailure(observed);
                TaskExecutionError error = failureClassifier.classify(throwable, failureContext);
                nodeResults.add(failed(node, ExecutionFailurePhase.WRITE, output.startedAt(), error));
                logNodeFailure(
                        manifest, node, ExecutionFailurePhase.WRITE, output.startedAt(), error, throwable);
                return failedResult(manifest, startedAt, orderedResults(plan, nodeResults), error);
            } finally {
                spark.sparkContext().clearJobGroup();
            }
        }

        Instant endedAt = Instant.now();
        return new TaskExecutionResult(
                TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                manifest.execution().executionId(),
                manifest.execution().runId(),
                manifest.execution().attempt(),
                TaskExecutionState.SUCCESS,
                startedAt,
                endedAt,
                Duration.between(startedAt, endedAt).toMillis(),
                affectedRows,
                orderedResults(plan, nodeResults),
                null
        );
        } finally {
            dataAccess.close();
        }
    }

    static Long addAffectedRows(Long currentTotal, Long rowsWritten) {
        if (currentTotal == null || rowsWritten == null || currentTotal < 0 || rowsWritten < 0) return null;
        try {
            return Math.addExact(currentTotal, rowsWritten);
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    private CanvasNodeOperationResult executeNodeOperator(
            CanvasNodeDefinition node,
            Map<String, SparkCanvasTable> inputs,
            SparkSession spark,
            MetadataIndex metadataIndex,
            RuntimeCanvasNodeDataAccess dataAccess
    ) {
        return nodeOperators.apply(
                node,
                inputs,
                new CanvasNodeOperationContext(
                        spark,
                        metadataIndex,
                        new RunnerCanvasNodeIssueSink(node.id()),
                        dataAccess
                )
        );
    }

    private static PreparedOutput preparedOutput(
            CanvasPreparedOutput output,
            Instant startedAt
    ) {
        return new PreparedOutput(
                output.node(),
                output.runtimeDataSource(),
                output.qualifiedTableName(),
                output.displayTarget(),
                output.writeMode(),
                output.dataset(),
                startedAt
        );
    }

    static DataFrameReader reader(SparkSession spark, RuntimeDataSource source) {
        RuntimeJdbcConnection connection = source.connection();
        DataFrameReader reader = spark.read().format("jdbc")
                .option("url", connection.jdbcUrl())
                .option("driver", connection.driverClassName())
                .option("user", connection.username())
                .option("password", connection.password());
        connection.properties().forEach(reader::option);
        return reader;
    }

    static void write(RuntimeDataSource source, String qualifiedTableName, Dataset<Row> dataset) {
        RuntimeJdbcConnection connection = source.connection();
        DataFrameWriter<Row> writer = dataset.write().format("jdbc")
                .mode(SaveMode.Append)
                .option("url", connection.jdbcUrl())
                .option("dbtable", qualifiedTableName)
                .option("driver", connection.driverClassName())
                .option("user", connection.username())
                .option("password", connection.password());
        connection.properties().forEach(writer::option);
        writer.save();
    }

    private static void truncate(RuntimeDataSource source, String qualifiedTableName) throws Exception {
        RuntimeJdbcConnection runtime = source.connection();
        Class.forName(runtime.driverClassName());
        Properties properties = new Properties();
        properties.setProperty("user", runtime.username());
        if (runtime.password() != null) properties.setProperty("password", runtime.password());
        runtime.properties().forEach(properties::setProperty);
        try (Connection connection = DriverManager.getConnection(runtime.jdbcUrl(), properties);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("TRUNCATE TABLE " + qualifiedTableName);
        }
    }

    static String qualifiedTable(RuntimeDataSource source, String tableName) {
        RuntimeJdbcConnection connection = source.connection();
        return qualifiedTable(
                source,
                connection.catalogName(),
                connection.schemaName(),
                tableName
        );
    }

    static String qualifiedModelTable(
            RuntimeDataSource source,
            MetadataIndex.ModelEntry model
    ) {
        return qualifiedTable(
                source,
                model.metadata().catalogName(),
                model.metadata().schemaName(),
                model.metadata().physicalTableName()
        );
    }

    private static String qualifiedTable(
            RuntimeDataSource source,
            String catalogName,
            String schemaName,
            String tableName
    ) {
        return switch (source.databaseType()) {
            case POSTGRESQL -> {
                yield schemaName == null || schemaName.isBlank()
                        ? quote(source.databaseType(), tableName)
                        : quote(source.databaseType(), schemaName) + "." + quote(source.databaseType(), tableName);
            }
            case MYSQL -> {
                yield catalogName == null || catalogName.isBlank()
                        ? quote(source.databaseType(), tableName)
                        : quote(source.databaseType(), catalogName) + "." + quote(source.databaseType(), tableName);
            }
        };
    }

    private static String quote(RuntimeDatabaseType type, String value) {
        return switch (type) {
            case POSTGRESQL -> "\"" + value.replace("\"", "\"\"") + "\"";
            case MYSQL -> "`" + value.replace("`", "``") + "`";
        };
    }

    static void validateRuntimeSchema(
            List<CanvasColumnSchema> expected,
            Dataset<Row> actual,
            String nodeId
    ) {
        List<CanvasColumnSchema> actualColumns = SparkTypeMapper.fromStructType(actual.schema(), expected);
        if (expected.size() != actualColumns.size()) {
            throw new RunnerExecutionException("RUNTIME_SCHEMA_MISMATCH", "物理表字段数量已变化", nodeId);
        }
        for (int index = 0; index < expected.size(); index++) {
            CanvasColumnSchema left = expected.get(index);
            CanvasColumnSchema right = actualColumns.get(index);
            if (!left.name().equals(right.name()) || left.fieldType() != right.fieldType()) {
                throw new RunnerExecutionException(
                        "RUNTIME_SCHEMA_MISMATCH", "物理表字段已变化：" + left.name(), nodeId);
            }
            if (left.fieldType() == PlatformDataType.DECIMAL
                    && (!left.precision().equals(right.precision()) || !left.scale().equals(right.scale()))) {
                throw new RunnerExecutionException(
                        "RUNTIME_SCHEMA_MISMATCH", "物理表 Decimal 精度已变化：" + left.name(), nodeId);
            }
        }
    }

    static Map<String, SparkCanvasTable> mergeInputs(
            List<Integer> predecessors,
            List<Map<String, SparkCanvasTable>> propagated,
            String nodeId
    ) {
        Map<String, SparkCanvasTable> merged = new LinkedHashMap<>();
        for (int predecessor : predecessors) {
            propagated.get(predecessor).forEach((name, table) -> {
                if (merged.putIfAbsent(name, table) != null) {
                    throw new RunnerExecutionException("DUPLICATE_TABLE_NAME", "上游存在同名表：" + name, nodeId);
                }
            });
        }
        return Map.copyOf(merged);
    }

    static Map<UUID, RuntimeDataSource> runtimeSources(List<RuntimeDataSource> sources) {
        Map<UUID, RuntimeDataSource> result = new LinkedHashMap<>();
        for (RuntimeDataSource source : sources) {
            if (source == null || source.dataSourceId() == null || source.connectionKind() == null) {
                throw new RunnerExecutionException("INVALID_MANIFEST", "runtimeDataSources 配置不完整", null);
            }
            validateConnection(source);
            if (result.putIfAbsent(source.dataSourceId(), source) != null) {
                throw new RunnerExecutionException("INVALID_MANIFEST", "运行数据源 ID 重复", null);
            }
        }
        return Map.copyOf(result);
    }

    private static void validateConnection(RuntimeDataSource source) {
        if (source.connectionKind() == ConnectionKind.HTTP_API) {
            if (source.databaseType() != null || source.connection() != null || source.httpApiConnection() == null
                    || source.httpApiConnection().configuration() == null
                    || source.httpApiConnection().credentials() == null || source.apiResources().isEmpty()) {
                throw new RunnerExecutionException("INVALID_MANIFEST", "HTTP API 运行连接无效", null);
            }
            return;
        }
        if (source.connectionKind() == ConnectionKind.KAFKA) {
            if (source.databaseType() != null || source.connection() != null
                    || source.httpApiConnection() != null || source.kafkaConnection() == null
                    || blank(source.kafkaConnection().bootstrapServers())
                    || source.kafkaConnection().securityProtocol() == null) {
                throw new RunnerExecutionException("INVALID_MANIFEST", "Kafka 运行连接无效", null);
            }
            return;
        }
        if (source.databaseType() == null || source.connection() == null) {
            throw new RunnerExecutionException("INVALID_MANIFEST", "JDBC 运行连接无效", null);
        }
        RuntimeJdbcConnection connection = source.connection();
        String expectedDriver = source.databaseType() == RuntimeDatabaseType.POSTGRESQL
                ? "org.postgresql.Driver" : "com.mysql.cj.jdbc.Driver";
        String expectedPrefix = source.databaseType() == RuntimeDatabaseType.POSTGRESQL
                ? "jdbc:postgresql://" : "jdbc:mysql://";
        if (!expectedDriver.equals(connection.driverClassName())
                || connection.jdbcUrl() == null || !connection.jdbcUrl().startsWith(expectedPrefix)
                || blank(connection.username())) {
            throw new RunnerExecutionException("INVALID_MANIFEST", "JDBC 运行连接无效", null);
        }
    }

    static RuntimeDataSource requireRuntimeSource(
            Map<UUID, RuntimeDataSource> sources,
            UUID dataSourceId,
            DataSourcePurpose purpose,
            String nodeId
    ) {
        RuntimeDataSource source = sources.get(dataSourceId);
        if (source == null || !source.purposes().contains(purpose)) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "运行数据源不存在或用途不匹配", nodeId);
        }
        return source;
    }

    static RuntimeDataSource requireRuntimeSourceForModelRead(
            Map<UUID, RuntimeDataSource> sources,
            UUID dataSourceId,
            String nodeId
    ) {
        RuntimeDataSource source = sources.get(dataSourceId);
        if (source == null || !source.purposes().contains(DataSourcePurpose.SOURCE)
                && !source.purposes().contains(DataSourcePurpose.STORAGE)) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE",
                    "模型运行数据源不存在或不具有 SOURCE/STORAGE 用途",
                    nodeId
            );
        }
        return source;
    }

    static UUID uuid(String value, String message, String nodeId) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new RunnerExecutionException("INVALID_MANIFEST", message, nodeId, exception);
        }
    }

    private static void validateManifest(TaskExecutionManifest manifest) {
        if (manifest == null || manifest.manifestVersion() == null
                || manifest.manifestVersion() != TaskExecutionManifest.CURRENT_MANIFEST_VERSION
                || manifest.execution() == null || manifest.execution().executionId() == null
                || manifest.execution().runId() == null || manifest.execution().taskId() == null
                || manifest.execution().attempt() == null || manifest.execution().attempt() != 1
                || manifest.execution().definitionVersion() == null || manifest.execution().definitionVersion() < 1
                || manifest.execution().createdAt() == null || manifest.execution().deadlineAt() == null
                || manifest.task() == null || manifest.task().type() != TaskType.CANVAS
                || manifest.task().definition() == null || manifest.metadataSnapshot() == null
                || manifest.metadataSnapshot().models() == null
                || manifest.metadataSnapshot().fileDatasetTables() == null
                || manifest.runtimeDataSources() == null
                || manifest.runtimeFileInputs() == null
                || !manifest.runtimeFileInputs().isEmpty() && manifest.runtimeFileStorage() == null) {
            throw new RunnerExecutionException("INVALID_MANIFEST", "任务运行 manifest 不完整", null);
        }
        checkDeadline(manifest);
    }

    private static void checkDeadline(TaskExecutionManifest manifest) {
        if (Instant.now().isAfter(manifest.execution().deadlineAt())) {
            throw new RunnerExecutionException("EXECUTION_DEADLINE_EXCEEDED", "任务已超过执行截止时间", null);
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new RunnerExecutionException("EXECUTION_CANCELLED", "任务执行已取消", null);
        }
    }

    private static NodeExecutionResult success(
            CanvasNodeDefinition node,
            ExecutionFailurePhase phase,
            Instant startedAt,
            Long rowsWritten,
            String message
    ) {
        Instant endedAt = Instant.now();
        return new NodeExecutionResult(
                node.id(), node.nodeType().name(), node.name(), NodeExecutionState.SUCCESS, phase,
                startedAt, endedAt, elapsedMillis(startedAt, endedAt), rowsWritten, message, null);
    }

    private static NodeExecutionResult failed(
            CanvasNodeDefinition node,
            ExecutionFailurePhase phase,
            Instant startedAt,
            TaskExecutionError error
    ) {
        Instant endedAt = Instant.now();
        return new NodeExecutionResult(
                node.id(), node.nodeType().name(), node.name(), NodeExecutionState.FAILED, phase,
                startedAt, endedAt, elapsedMillis(startedAt, endedAt), null, error.message(), error);
    }

    static TaskExecutionResult failure(
            UUID executionId,
            UUID runId,
            int attempt,
            Instant startedAt,
            Throwable throwable
    ) {
        TaskExecutionError error = new RunnerFailureClassifier().classify(
                throwable, RunnerFailureContext.task(ExecutionFailurePhase.PREPARE));
        return failedResult(executionId, runId, attempt, startedAt, List.of(), error);
    }

    private static TaskExecutionResult failedResult(
            TaskExecutionManifest manifest,
            Instant startedAt,
            List<NodeExecutionResult> nodeResults,
            TaskExecutionError error
    ) {
        return failedResult(
                manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                startedAt, nodeResults, error);
    }

    private static TaskExecutionResult failedResult(
            UUID executionId,
            UUID runId,
            int attempt,
            Instant startedAt,
            List<NodeExecutionResult> nodeResults,
            TaskExecutionError error
    ) {
        Instant endedAt = Instant.now();
        return new TaskExecutionResult(
                TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                executionId,
                runId,
                attempt,
                failureState(error),
                startedAt,
                endedAt,
                elapsedMillis(startedAt, endedAt),
                null,
                nodeResults,
                error
        );
    }

    private static TaskExecutionState failureState(TaskExecutionError error) {
        return switch (error.code()) {
            case "EXECUTION_DEADLINE_EXCEEDED" -> TaskExecutionState.TIMED_OUT;
            case "EXECUTION_CANCELLED" -> TaskExecutionState.CANCELLED;
            default -> TaskExecutionState.FAILED;
        };
    }

    private static List<NodeExecutionResult> orderedResults(
            CanvasGraphPlan plan,
            List<NodeExecutionResult> results
    ) {
        Map<String, Integer> order = new LinkedHashMap<>();
        int ordinal = 0;
        for (int nodeIndex : plan.topologicalOrder()) {
            order.put(plan.nodeAt(nodeIndex).id(), ordinal++);
        }
        return results.stream()
                .sorted(Comparator.comparingInt(result -> order.getOrDefault(result.nodeId(), Integer.MAX_VALUE)))
                .toList();
    }

    private static ExecutionFailurePhase phase(CanvasNodeDefinition node) {
        return switch (node.nodeType()) {
            case MODEL_INPUT, JDBC_INPUT, FILE_DATASET_INPUT, HTTP_API_INPUT, KAFKA_INPUT ->
                    ExecutionFailurePhase.READ;
            case JOIN, STREAM_JOIN, RENAME -> ExecutionFailurePhase.PROCESS;
            case MODEL_OUTPUT, JDBC_OUTPUT, KAFKA_OUTPUT -> ExecutionFailurePhase.WRITE;
        };
    }

    private static String resourceName(
            CanvasNodeDefinition node,
            Map<UUID, RuntimeDataSource> runtimeSources,
            MetadataIndex metadataIndex
    ) {
        return switch (node) {
            case ModelInputNodeDefinition input -> displayModelTable(
                    runtimeSources, metadataIndex, input.configuration().modelId());
            case JdbcInputNodeDefinition input -> displayTable(
                    runtimeSources, input.configuration().dataSourceId(), input.configuration().tableName());
            case cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition input -> {
                MetadataIndex.FileDatasetTableEntry table = metadataIndex.fileDatasetTable(
                        UUID.fromString(input.configuration().fileDatasetTableId()));
                yield table == null ? input.configuration().fileDatasetTableId() : table.metadata().code();
            }
            case cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition input ->
                    input.configuration().resourceId();
            case cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition input ->
                    input.configuration().topic();
            case JoinNodeDefinition join -> join.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.StreamJoinNodeDefinition join ->
                    join.configuration().outputTableName();
            case RenameNodeDefinition rename -> rename.configuration().outputTableName();
            case ModelOutputNodeDefinition output -> displayModelTable(
                    runtimeSources, metadataIndex, output.configuration().targetModelId());
            case JdbcOutputNodeDefinition output -> displayTable(
                    runtimeSources, output.configuration().dataSourceId(), output.configuration().targetTableName());
            case cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition output ->
                    output.configuration().topic();
        };
    }

    private static String displayTable(
            Map<UUID, RuntimeDataSource> runtimeSources,
            String dataSourceId,
            String tableName
    ) {
        try {
            RuntimeDataSource source = runtimeSources.get(UUID.fromString(dataSourceId));
            return source == null ? tableName : displayTable(source, tableName);
        } catch (RuntimeException ignored) {
            return tableName;
        }
    }

    static String displayTable(RuntimeDataSource source, String tableName) {
        if (source.connection() == null) return tableName;
        String namespace = source.databaseType() == RuntimeDatabaseType.POSTGRESQL
                ? source.connection().schemaName() : source.connection().catalogName();
        return namespace == null || namespace.isBlank() ? tableName : namespace + "." + tableName;
    }

    private static String displayModelTable(
            Map<UUID, RuntimeDataSource> runtimeSources,
            MetadataIndex metadataIndex,
            UUID modelId
    ) {
        MetadataIndex.ModelEntry model = metadataIndex.model(modelId);
        if (model == null) return modelId == null ? null : modelId.toString();
        RuntimeDataSource source = runtimeSources.get(model.metadata().dataSourceId());
        return source == null ? model.metadata().physicalTableName() : displayModelTable(source, model);
    }

    static String displayModelTable(
            RuntimeDataSource source,
            MetadataIndex.ModelEntry model
    ) {
        String namespace = source.databaseType() == RuntimeDatabaseType.POSTGRESQL
                ? model.metadata().schemaName() : model.metadata().catalogName();
        return namespace == null || namespace.isBlank()
                ? model.metadata().physicalTableName()
                : namespace + "." + model.metadata().physicalTableName();
    }

    private static String nodeSummary(CanvasNodeDefinition node, MetadataIndex metadataIndex) {
        return switch (node) {
            case ModelInputNodeDefinition input -> modelSummary(
                    "modelId=" + safeLogValue(input.configuration().modelId().toString()),
                    metadataIndex.model(input.configuration().modelId()))
                    + " runtimeSchemaValidation=true";
            case JdbcInputNodeDefinition input -> "dataSourceId=" + safeLogValue(input.configuration().dataSourceId())
                    + " table=" + safeLogValue(input.configuration().tableName())
                    + " runtimeSchemaValidation=true";
            case cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition input -> {
                MetadataIndex.FileDatasetTableEntry table = metadataIndex.fileDatasetTable(
                        UUID.fromString(input.configuration().fileDatasetTableId()));
                yield "fileDatasetTableId=" + safeLogValue(input.configuration().fileDatasetTableId())
                        + (table == null
                        ? ""
                        : " tableCode=" + safeLogValue(table.metadata().code())
                                + " format=" + table.metadata().datasetType()
                                + " fieldCount=" + table.metadata().columns().size());
            }
            case cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition input ->
                    "dataSourceId=" + safeLogValue(input.configuration().dataSourceId().toString())
                            + " resourceId=" + safeLogValue(input.configuration().resourceId())
                            + " outputTable=" + safeLogValue(input.configuration().outputTableName())
                            + " runtimeParameterCount=" + input.configuration().runtimeParameters().size();
            case cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition input ->
                    "dataSourceId=" + safeLogValue(input.configuration().dataSourceId().toString())
                            + " topic=" + safeLogValue(input.configuration().topic())
                            + " outputTable=" + safeLogValue(input.configuration().outputTableName());
            case JoinNodeDefinition join -> "leftTable=" + safeLogValue(join.configuration().leftTableName())
                    + " rightTable=" + safeLogValue(join.configuration().rightTableName())
                    + " joinType=" + join.configuration().joinType()
                    + " conditionCount=" + join.configuration().conditions().size()
                    + " outputTable=" + safeLogValue(join.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.StreamJoinNodeDefinition join ->
                    "leftStream=" + safeLogValue(join.configuration().leftTableName())
                            + " rightStatic=" + safeLogValue(join.configuration().rightTableName())
                            + " joinType=" + join.configuration().joinType()
                            + " outputTable=" + safeLogValue(join.configuration().outputTableName());
            case RenameNodeDefinition rename -> "sourceTable="
                    + safeLogValue(rename.configuration().sourceTableName())
                    + " outputTable=" + safeLogValue(rename.configuration().outputTableName())
                    + " mappingCount=" + rename.configuration().columnMappings().size();
            case ModelOutputNodeDefinition output -> "sourceTable="
                    + safeLogValue(output.configuration().sourceTableName())
                    + " " + modelSummary(
                    "targetModelId=" + safeLogValue(output.configuration().targetModelId().toString()),
                    metadataIndex.model(output.configuration().targetModelId()))
                    + " writeMode=" + output.configuration().writeMode()
                    + " mappingMode=" + output.configuration().columnMappingMode()
                    + " mappingCount=" + output.configuration().columnMappings().size()
                    + " stages=TARGET_SCHEMA,MATERIALIZE,TRUNCATE_IF_REQUIRED,WRITE";
            case JdbcOutputNodeDefinition output -> "sourceTable="
                    + safeLogValue(output.configuration().sourceTableName())
                    + " dataSourceId=" + safeLogValue(output.configuration().dataSourceId())
                    + " targetTable=" + safeLogValue(output.configuration().targetTableName())
                    + " writeMode=" + output.configuration().writeMode()
                    + " stages=TARGET_SCHEMA,MATERIALIZE,TRUNCATE_IF_REQUIRED,WRITE";
            case cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition output ->
                    "sourceTable=" + safeLogValue(output.configuration().sourceTableName())
                            + " dataSourceId=" + safeLogValue(output.configuration().dataSourceId().toString())
                            + " topic=" + safeLogValue(output.configuration().topic());
        };
    }

    private static String modelSummary(String prefix, MetadataIndex.ModelEntry model) {
        if (model == null) return prefix;
        return prefix
                + " modelCode=" + safeLogValue(model.metadata().code())
                + " modelSchemaVersion=" + model.metadata().schemaVersion()
                + " dataSourceId=" + model.metadata().dataSourceId()
                + " physicalTable=" + safeLogValue(model.metadata().physicalTableName());
    }

    private static String outputSuccessMessage(CanvasNodeDefinition node, boolean metricAvailable) {
        String prefix = node instanceof ModelOutputNodeDefinition ? "模型输出写入成功" : "JDBC 输出写入成功";
        return metricAvailable ? prefix : prefix + "，输出行数指标不可用";
    }

    private static String nodePreparedMessage(CanvasNodeDefinition node) {
        return switch (node.nodeType()) {
            case MODEL_INPUT -> "模型输入已准备";
            case JDBC_INPUT -> "JDBC 输入已准备";
            case FILE_DATASET_INPUT -> "文件数据集输入已准备";
            case HTTP_API_INPUT -> "HTTP API 输入已读取";
            case KAFKA_INPUT -> "Kafka 输入已准备";
            case JOIN -> "Join 已准备";
            case STREAM_JOIN -> "Stream Join 已准备";
            case RENAME -> "重命名已准备";
            case MODEL_OUTPUT, JDBC_OUTPUT, KAFKA_OUTPUT ->
                    throw new IllegalArgumentException("Output success is recorded after writing");
        };
    }

    private static void logNodeStart(
            TaskExecutionManifest manifest,
            CanvasNodeDefinition node,
            ExecutionFailurePhase phase,
            String summary
    ) {
        LOGGER.info(
                "event=NODE_START executionId={} runId={} attempt={} nodeId={} nodeType={} nodeName={} phase={} {}",
                manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                safeLogValue(node.id()), node.nodeType(), safeLogValue(node.name()), phase, summary);
    }

    private static void logNodeSuccess(
            TaskExecutionManifest manifest,
            CanvasNodeDefinition node,
            ExecutionFailurePhase phase,
            Instant startedAt,
            Long rowsWritten
    ) {
        LOGGER.info(
                "event=NODE_SUCCESS executionId={} runId={} attempt={} nodeId={} nodeType={} nodeName={} phase={} durationMs={} rowsWritten={}",
                manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                safeLogValue(node.id()), node.nodeType(), safeLogValue(node.name()), phase,
                elapsedMillis(startedAt, Instant.now()), rowsWritten);
    }

    private static void logNodeFailure(
            TaskExecutionManifest manifest,
            CanvasNodeDefinition node,
            ExecutionFailurePhase phase,
            Instant startedAt,
            TaskExecutionError error,
            Throwable throwable
    ) {
        LOGGER.error(
                "event=NODE_FAILED executionId={} runId={} attempt={} nodeId={} nodeType={} nodeName={} phase={} durationMs={} code={} category={} retryable={} sqlState={} diagnosticId={}\n{}",
                manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                safeLogValue(node.id()), node.nodeType(), safeLogValue(node.name()), phase,
                elapsedMillis(startedAt, Instant.now()), error.code(), error.category(), error.retryable(),
                error.sqlState(), error.diagnosticId(), RunnerLogSanitizer.stackTrace(throwable));
    }

    private static void logTaskFailureDetail(
            TaskExecutionManifest manifest,
            TaskExecutionError error,
            Throwable throwable
    ) {
        String summary = "event=TASK_FAILURE_DETAIL executionId=" + manifest.execution().executionId()
                + " runId=" + manifest.execution().runId()
                + " attempt=" + manifest.execution().attempt()
                + " code=" + error.code()
                + " category=" + error.category()
                + " phase=" + error.phase()
                + " diagnosticId=" + error.diagnosticId();
        LOGGER.error("{}\n{}", summary, RunnerLogSanitizer.stackTrace(throwable));
    }

    private static String safeLogValue(String value) {
        if (value == null) return "-";
        return RunnerLogSanitizer.sanitize(value).replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }

    private static long elapsedMillis(Instant startedAt, Instant endedAt) {
        return Math.max(0L, Duration.between(startedAt, endedAt).toMillis());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record PreparedOutput(
            CanvasNodeDefinition node,
            RuntimeDataSource runtimeDataSource,
            String qualifiedTableName,
            String displayTarget,
            JdbcWriteMode writeMode,
            Dataset<Row> dataset,
            Instant startedAt
    ) {
    }
}
