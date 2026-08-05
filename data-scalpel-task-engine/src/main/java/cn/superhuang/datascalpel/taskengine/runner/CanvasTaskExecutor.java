package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperationContext;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperationResult;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperatorRegistry;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperators;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedOutput;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedFileOutput;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasCompilation;
import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasGraphPlan;
import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasTaskCompiler;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.CompilationSeverity;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasFieldPredicate;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterCondition;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterGroup;
import cn.superhuang.data.scalpel.contract.task.FilterNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SelectColumnsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.BinaryExpression;
import cn.superhuang.data.scalpel.contract.task.CanvasExpression;
import cn.superhuang.data.scalpel.contract.task.CaseWhenExpression;
import cn.superhuang.data.scalpel.contract.task.ColumnExpression;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FunctionExpression;
import cn.superhuang.data.scalpel.contract.task.LiteralExpression;
import cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.AggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.UnionNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.DeduplicateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.DropNullRowsRule;
import cn.superhuang.data.scalpel.contract.task.FillNullLiteralRule;
import cn.superhuang.data.scalpel.contract.task.NullHandlingNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.RowsFrameBoundary;
import cn.superhuang.data.scalpel.contract.task.TopNNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ValueMappingNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.MaskFieldsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JsonExtractNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.MaskingRuleSource;
import cn.superhuang.data.scalpel.contract.task.WindowFunctionItem;
import cn.superhuang.data.scalpel.contract.task.WindowNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionState;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeJdbcConnection;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeS3Connection;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionError;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionState;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.DataFrameReader;
import org.apache.spark.sql.DataFrameWriter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.storage.StorageLevel;
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
        SparkSession.Builder builder = SedonaSparkSupport.builder()
                .appName("DataScalpel Task Runner " + manifest.execution().executionId())
                .config("spark.ui.enabled", "false")
                .config("spark.sql.shuffle.partitions", "4")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .config("spark.speculation", "false");
        if (sparkMode == RunnerSparkMode.LOCAL) builder.master("local[*]");
        SparkSession spark = SedonaSparkSupport.initialize(builder.getOrCreate());
        try (SparkOutputMetricsCollector metricsCollector = new SparkOutputMetricsCollector(spark)) {
            sparkStarted.accept(spark.sparkContext().applicationId());
            MetadataIndex metadataIndex = MetadataIndex.create(manifest.metadataSnapshot());
            CanvasCompilation compilation = compiler.compile(
                    manifest.task().definition(),
                    metadataIndex,
                    SedonaSparkSupport.childSession(spark),
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
            return executeCompiled(
                    manifest, metadataIndex, compilation, spark, metricsCollector, startedAt);
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
            CanvasCompilation compilation,
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
            logNodeStart(
                    manifest,
                    node,
                    phase,
                    nodeSummary(
                            node,
                            metadataIndex,
                            compilation.nodeResults().get(nodeIndex).inputTables())
            );
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
                } else if (node instanceof FileOutputNodeDefinition) {
                    if (operation.preparedFileOutput() == null) {
                        throw new RunnerExecutionException(
                                "OUTPUT_NOT_PREPARED", "文件输出节点未生成写入计划", node.id());
                    }
                    preparedOutputs.add(preparedFileOutput(operation.preparedFileOutput(), nodeStartedAt));
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
            Dataset<Row> cachedUpsert = null;
            try {
                checkDeadline(manifest);
                Dataset<Row> writeDataset = output.dataset();
                if (output.writeMode() == JdbcWriteMode.UPSERT) {
                    cachedUpsert = writeDataset.persist(StorageLevel.MEMORY_AND_DISK());
                    SpatialJdbcRuntimeSupport.validateUpsertKeys(output.jdbcOutput(), cachedUpsert);
                    writeDataset = cachedUpsert;
                }
                observed = metricsCollector.observe(
                        manifest.execution().executionId(), manifest.execution().attempt(),
                        node.id(), writeDataset);
                spark.sparkContext().setJobGroup(
                        jobGroup, "DataScalpel " + node.nodeType() + " " + node.id(), true);
                if (output.fileOutput() != null) {
                    writeFile(
                            spark,
                            output.fileOutput(),
                            observed.dataset(),
                            manifest.execution().executionId()
                    );
                } else {
                    if (output.writeMode() == JdbcWriteMode.OVERWRITE) {
                        truncate(output.runtimeDataSource(), output.qualifiedTableName());
                    }
                    if (output.writeMode() == JdbcWriteMode.UPSERT) {
                        SpatialJdbcRuntimeSupport.writeUpsert(output.jdbcOutput(), observed.dataset());
                    } else if (output.jdbcOutput() != null
                            && SpatialJdbcRuntimeSupport.requiresSpatialWriter(output.jdbcOutput())) {
                        SpatialJdbcRuntimeSupport.writeSpatial(output.jdbcOutput(), observed.dataset());
                    } else {
                        write(output.runtimeDataSource(), output.qualifiedTableName(), observed.dataset());
                    }
                }
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
                if (cachedUpsert != null) cachedUpsert.unpersist();
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
                        dataAccess,
                        CanvasExecutionMode.BATCH
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
                output,
                null,
                startedAt
        );
    }

    private static PreparedOutput preparedFileOutput(
            CanvasPreparedFileOutput output,
            Instant startedAt
    ) {
        return new PreparedOutput(
                output.node(),
                output.runtimeDataSource(),
                null,
                output.targetUri(),
                null,
                output.dataset(),
                null,
                output,
                startedAt
        );
    }

    private static void writeFile(
            SparkSession spark,
            CanvasPreparedFileOutput output,
            Dataset<Row> dataset,
            UUID executionId
    ) {
        RuntimeS3Connection connection = output.runtimeDataSource().s3Connection();
        configureBucketS3A(spark, connection);
        var configuration = output.node().configuration();
        switch (configuration.formatOptions()) {
            case FileOutputFormatOptions.Csv csv -> fileWriter(dataset, configuration.conflictPolicy())
                    .format("csv")
                    .option("encoding", "UTF-8")
                    .option("header", csv.header())
                    .option("delimiter", csv.delimiter())
                    .option("quote", csv.quote())
                    .option("escape", csv.escape())
                    .option("nullValue", csv.nullValue())
                    .save(output.targetUri());
            case FileOutputFormatOptions.JsonLines json -> fileWriter(dataset, configuration.conflictPolicy())
                    .format("json")
                    .option("encoding", "UTF-8")
                    .option("ignoreNullFields", json.ignoreNullFields())
                    .save(output.targetUri());
            case FileOutputFormatOptions.Parquet ignored -> fileWriter(dataset, configuration.conflictPolicy())
                    .format("parquet")
                    .option("compression", "snappy")
                    .save(output.targetUri());
            case FileOutputFormatOptions.Shapefile ignored ->
                    ShapefileFileOutputWriter.write(spark, output, dataset, executionId);
            case FileOutputFormatOptions.GeoParquet ignored ->
                    GeoParquetFileOutputWriter.write(output, dataset);
            case FileOutputFormatOptions.GeoJson ignored ->
                    GeoJsonFileOutputWriter.write(spark, output, dataset, executionId);
        }
    }

    private static DataFrameWriter<Row> fileWriter(
            Dataset<Row> dataset,
            FileOutputConflictPolicy conflictPolicy
    ) {
        return dataset.write().mode(
                conflictPolicy == FileOutputConflictPolicy.OVERWRITE
                        ? SaveMode.Overwrite : SaveMode.ErrorIfExists);
    }

    private static void configureBucketS3A(SparkSession spark, RuntimeS3Connection connection) {
        String prefix = "fs.s3a.bucket." + connection.bucket() + ".";
        org.apache.hadoop.conf.Configuration configuration =
                spark.sparkContext().hadoopConfiguration();
        configuration.set(prefix + "endpoint", connection.endpoint());
        configuration.set(prefix + "endpoint.region", connection.region());
        configuration.set(prefix + "access.key", connection.accessKey());
        configuration.set(prefix + "secret.key", connection.secretKey());
        configuration.set(prefix + "aws.credentials.provider",
                "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider");
        configuration.setBoolean(prefix + "path.style.access", connection.pathStyleAccess());
        configuration.setBoolean(prefix + "connection.ssl.enabled",
                connection.endpoint().toLowerCase(java.util.Locale.ROOT).startsWith("https://"));
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
            if (left.fieldType() == PlatformDataType.GEOMETRY
                    && !java.util.Objects.equals(left.geometry(), right.geometry())) {
                throw new RunnerExecutionException(
                        "SPATIAL_SCHEMA_DRIFT",
                        "Geometry 字段 kind、CRS 或 dimension 已变化：" + left.name(),
                        nodeId
                );
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
        if (source.connectionKind() == ConnectionKind.S3) {
            RuntimeS3Connection connection = source.s3Connection();
            if (source.databaseType() != null || source.connection() != null
                    || source.httpApiConnection() != null || source.kafkaConnection() != null
                    || connection == null || blank(connection.endpoint())
                    || blank(connection.region()) || blank(connection.bucket())
                    || blank(connection.accessKey()) || blank(connection.secretKey())) {
                throw new RunnerExecutionException("INVALID_MANIFEST", "S3 运行连接无效", null);
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
        ManifestVersionSupport.requireSupported(manifest);
        if (manifest.execution() == null || manifest.execution().executionId() == null
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
            case MODEL_INPUT, JDBC_INPUT, JDBC_QUERY_INPUT, FILE_DATASET_INPUT, HTTP_API_INPUT, SPATIAL_SERVICE_INPUT, KAFKA_INPUT ->
                    ExecutionFailurePhase.READ;
            case JOIN, GEOMETRY_CONSTRUCT, SPATIAL_TRANSFORM, GEOMETRY_VALIDATE,
                    GEOMETRY_REPAIR, GEOMETRY_BUFFER, GEOMETRY_EXPLODE,
                    SPATIAL_MEASURE, GEOMETRY_SERIALIZE, SPATIAL_CLIP,
                    SPATIAL_AGGREGATE, SPATIAL_JOIN, STREAM_JOIN,
                    RENAME, FILTER, SELECT_COLUMNS, DERIVE_COLUMNS, TYPE_CAST,
                    AGGREGATE, UNION, DEDUPLICATE, NULL_HANDLING, VALUE_MAPPING, MASK_FIELDS,
                            JSON_EXTRACT, WINDOW, TOP_N ->
                    ExecutionFailurePhase.PROCESS;
            case MODEL_OUTPUT, JDBC_OUTPUT, KAFKA_OUTPUT, FILE_OUTPUT -> ExecutionFailurePhase.WRITE;
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
            case JdbcQueryInputNodeDefinition input -> input.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition input -> {
                MetadataIndex.FileDatasetTableEntry table = metadataIndex.fileDatasetTable(
                        UUID.fromString(input.configuration().fileDatasetTableId()));
                yield table == null ? input.configuration().fileDatasetTableId() : table.metadata().code();
            }
            case cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition input ->
                    input.configuration().resourceId();
            case cn.superhuang.data.scalpel.contract.task.SpatialServiceInputNodeDefinition input ->
                    input.configuration().resourceId();
            case cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition input ->
                    input.configuration().topic();
            case JoinNodeDefinition join -> join.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometryConstructNodeDefinition construct ->
                    construct.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialTransformNodeDefinition transform ->
                    transform.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometryValidateNodeDefinition validate ->
                    validate.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometryRepairNodeDefinition repair ->
                    repair.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometryBufferNodeDefinition buffer ->
                    buffer.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometryExplodeNodeDefinition explode ->
                    explode.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialMeasureNodeDefinition measure ->
                    measure.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometrySerializeNodeDefinition serialize ->
                    serialize.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition clip ->
                    clip.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition aggregate ->
                    aggregate.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition join ->
                    join.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.StreamJoinNodeDefinition join ->
                    join.configuration().outputTableName();
            case RenameNodeDefinition rename -> rename.configuration().outputTableName();
            case FilterNodeDefinition filter -> filter.configuration().outputTableName();
            case SelectColumnsNodeDefinition selectColumns ->
                    selectColumns.configuration().outputTableName();
            case DeriveColumnsNodeDefinition deriveColumns ->
                    deriveColumns.configuration().outputTableName();
            case TypeCastNodeDefinition typeCast ->
                    typeCast.configuration().outputTableName();
            case AggregateNodeDefinition aggregate ->
                    aggregate.configuration().outputTableName();
            case UnionNodeDefinition union ->
                    union.configuration().outputTableName();
            case DeduplicateNodeDefinition deduplicate ->
                    deduplicate.configuration().outputTableName();
            case NullHandlingNodeDefinition nullHandling ->
                    nullHandling.configuration().outputTableName();
            case ValueMappingNodeDefinition valueMapping ->
                    valueMapping.configuration().outputTableName();
            case MaskFieldsNodeDefinition maskFields ->
                    maskFields.configuration().outputTableName();
            case JsonExtractNodeDefinition jsonExtract ->
                    jsonExtract.configuration().outputTableName();
            case WindowNodeDefinition window ->
                    window.configuration().outputTableName();
            case TopNNodeDefinition topN ->
                    topN.configuration().outputTableName();
            case ModelOutputNodeDefinition output -> displayModelTable(
                    runtimeSources, metadataIndex, output.configuration().targetModelId());
            case JdbcOutputNodeDefinition output -> displayTable(
                    runtimeSources, output.configuration().dataSourceId(), output.configuration().targetTableName());
            case cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition output ->
                    output.configuration().topic();
            case FileOutputNodeDefinition output -> output.configuration().targetPath();
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
            String modelId
    ) {
        MetadataIndex.ModelEntry model = metadataModel(metadataIndex, modelId);
        if (model == null) return modelId;
        RuntimeDataSource source = runtimeSources.get(model.metadata().dataSourceId());
        return source == null ? model.metadata().physicalTableName() : displayModelTable(source, model);
    }

    private static MetadataIndex.ModelEntry metadataModel(MetadataIndex metadataIndex, String modelId) {
        if (modelId == null || modelId.isBlank()) return null;
        try {
            return metadataIndex.model(UUID.fromString(modelId));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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

    static String nodeSummary(CanvasNodeDefinition node, MetadataIndex metadataIndex) {
        return nodeSummary(node, metadataIndex, List.of());
    }

    static String nodeSummary(
            CanvasNodeDefinition node,
            MetadataIndex metadataIndex,
            List<CanvasTableSchema> inputTables
    ) {
        return switch (node) {
            case ModelInputNodeDefinition input -> modelSummary(
                    "modelId=" + safeLogValue(input.configuration().modelId()),
                    metadataModel(metadataIndex, input.configuration().modelId()))
                    + " runtimeSchemaValidation=true";
            case JdbcInputNodeDefinition input -> "dataSourceId=" + safeLogValue(input.configuration().dataSourceId())
                    + " table=" + safeLogValue(input.configuration().tableName())
                    + " runtimeSchemaValidation=true";
            case JdbcQueryInputNodeDefinition input -> "dataSourceId="
                    + safeLogValue(input.configuration().dataSourceId())
                    + " outputTable=" + safeLogValue(input.configuration().outputTableName())
                    + " fieldCount=" + input.configuration().outputColumns().size()
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
            case cn.superhuang.data.scalpel.contract.task.SpatialServiceInputNodeDefinition input ->
                    "dataSourceId=" + safeLogValue(input.configuration().dataSourceId())
                            + " resourceId=" + safeLogValue(input.configuration().resourceId())
                            + " outputTable=" + safeLogValue(input.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition input ->
                    "dataSourceId=" + safeLogValue(input.configuration().dataSourceId())
                            + " topic=" + safeLogValue(input.configuration().topic())
                            + " outputTable=" + safeLogValue(input.configuration().outputTableName());
            case JoinNodeDefinition join -> "leftTable=" + safeLogValue(join.configuration().leftTableName())
                    + " rightTable=" + safeLogValue(join.configuration().rightTableName())
                    + " joinType=" + join.configuration().joinType()
                    + " conditionCount=" + join.configuration().conditions().size()
                    + " outputTable=" + safeLogValue(join.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.GeometryConstructNodeDefinition construct -> {
                String sourceSummary = switch (construct.configuration().source()) {
                    case cn.superhuang.data.scalpel.contract.task.GeometryConstructSource.Wkt source ->
                            "sourceKind=WKT sourceColumn=" + safeLogValue(source.columnName());
                    case cn.superhuang.data.scalpel.contract.task.GeometryConstructSource.Wkb source ->
                            "sourceKind=WKB sourceColumn=" + safeLogValue(source.columnName());
                    case cn.superhuang.data.scalpel.contract.task.GeometryConstructSource.GeoJson source ->
                            "sourceKind=GEOJSON sourceColumn=" + safeLogValue(source.columnName());
                    case cn.superhuang.data.scalpel.contract.task.GeometryConstructSource.PointFromXy source ->
                            "sourceKind=POINT_FROM_XY xColumn=" + safeLogValue(source.xColumnName())
                                    + " yColumn=" + safeLogValue(source.yColumnName());
                };
                yield "sourceTable=" + safeLogValue(construct.configuration().sourceTableName())
                        + " outputTable=" + safeLogValue(construct.configuration().outputTableName())
                        + " outputColumn=" + safeLogValue(construct.configuration().outputColumnName())
                        + " " + sourceSummary
                        + " targetKind=" + construct.configuration().targetGeometry().kind()
                        + " targetCrs=" + construct.configuration().targetGeometry().crs()
                        + " dimension=" + construct.configuration().targetGeometry().dimension();
            }
            case cn.superhuang.data.scalpel.contract.task.SpatialTransformNodeDefinition transform ->
                    "sourceTable=" + safeLogValue(transform.configuration().sourceTableName())
                            + " geometryColumn="
                            + safeLogValue(transform.configuration().geometryColumnName())
                            + " targetCrs=" + transform.configuration().targetCrs()
                            + " outputTable="
                            + safeLogValue(transform.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.GeometryValidateNodeDefinition validate ->
                    "sourceTable=" + safeLogValue(validate.configuration().sourceTableName())
                            + " outputTable=" + safeLogValue(validate.configuration().outputTableName())
                            + " geometryColumn="
                            + safeLogValue(validate.configuration().geometryColumnName())
                            + " validColumn=" + safeLogValue(validate.configuration().validColumnName())
                            + " reasonColumn=" + safeLogValue(validate.configuration().reasonColumnName());
            case cn.superhuang.data.scalpel.contract.task.GeometryRepairNodeDefinition repair ->
                    "sourceTable=" + safeLogValue(repair.configuration().sourceTableName())
                            + " outputTable=" + safeLogValue(repair.configuration().outputTableName())
                            + " geometryColumn="
                            + safeLogValue(repair.configuration().geometryColumnName())
                            + " outputColumn="
                            + safeLogValue(repair.configuration().outputColumnName());
            case cn.superhuang.data.scalpel.contract.task.GeometryBufferNodeDefinition buffer ->
                    "sourceTable=" + safeLogValue(buffer.configuration().sourceTableName())
                            + " outputTable=" + safeLogValue(buffer.configuration().outputTableName())
                            + " geometryColumn="
                            + safeLogValue(buffer.configuration().geometryColumnName())
                            + " outputColumn="
                            + safeLogValue(buffer.configuration().outputColumnName())
                            + " distance=" + buffer.configuration().distance()
                            + " mode=" + buffer.configuration().mode();
            case cn.superhuang.data.scalpel.contract.task.GeometryExplodeNodeDefinition explode ->
                    "sourceTable=" + safeLogValue(explode.configuration().sourceTableName())
                            + " outputTable=" + safeLogValue(explode.configuration().outputTableName())
                            + " geometryColumn="
                            + safeLogValue(explode.configuration().geometryColumnName())
                            + " outputColumn="
                            + safeLogValue(explode.configuration().outputColumnName())
                            + " partIndexColumn="
                            + safeLogValue(explode.configuration().partIndexColumnName());
            case cn.superhuang.data.scalpel.contract.task.SpatialMeasureNodeDefinition measure -> {
                String measurements = measure.configuration().measurements().stream()
                        .map(CanvasTaskExecutor::spatialMeasurementSummary)
                        .collect(java.util.stream.Collectors.joining(","));
                yield "sourceTable=" + safeLogValue(measure.configuration().sourceTableName())
                        + " outputTable=" + safeLogValue(measure.configuration().outputTableName())
                        + " measurementCount=" + measure.configuration().measurements().size()
                        + " measurements=" + safeLogValue(measurements);
            }
            case cn.superhuang.data.scalpel.contract.task.GeometrySerializeNodeDefinition serialize ->
                    "sourceTable=" + safeLogValue(serialize.configuration().sourceTableName())
                            + " outputTable=" + safeLogValue(serialize.configuration().outputTableName())
                            + " geometryColumn="
                            + safeLogValue(serialize.configuration().geometryColumnName())
                            + " outputColumn="
                            + safeLogValue(serialize.configuration().outputColumnName())
                            + " format=" + serialize.configuration().format();
            case cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition clip ->
                    "sourceTable=" + safeLogValue(clip.configuration().sourceTableName())
                            + " sourceGeometryColumn="
                            + safeLogValue(clip.configuration().sourceGeometryColumnName())
                            + " maskTable=" + safeLogValue(clip.configuration().maskTableName())
                            + " maskGeometryColumn="
                            + safeLogValue(clip.configuration().maskGeometryColumnName())
                            + " outputTable="
                            + safeLogValue(clip.configuration().outputTableName())
                            + " outputColumn="
                            + safeLogValue(clip.configuration().outputColumnName());
            case cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition aggregate -> {
                String aggregations = aggregate.configuration().aggregations().stream()
                        .map(item -> item.kind() + ":" + item.geometryColumnName()
                                + ":" + item.outputColumnName())
                        .collect(java.util.stream.Collectors.joining(","));
                yield "sourceTable="
                        + safeLogValue(aggregate.configuration().sourceTableName())
                        + " outputTable="
                        + safeLogValue(aggregate.configuration().outputTableName())
                        + " groupByCount=" + aggregate.configuration().groupByColumns().size()
                        + " groupBy="
                        + safeLogValue(String.join(",", aggregate.configuration().groupByColumns()))
                        + " aggregationCount=" + aggregate.configuration().aggregations().size()
                        + " aggregations=" + safeLogValue(aggregations);
            }
            case cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition join ->
                    "leftTable=" + safeLogValue(join.configuration().leftTableName())
                            + " rightTable=" + safeLogValue(join.configuration().rightTableName())
                            + " joinType=" + join.configuration().joinType()
                            + " conditionCount=" + join.configuration().conditions().size()
                            + " outputTable="
                            + safeLogValue(join.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.StreamJoinNodeDefinition join ->
                    "leftStream=" + safeLogValue(join.configuration().leftTableName())
                            + " rightStatic=" + safeLogValue(join.configuration().rightTableName())
                            + " joinType=" + join.configuration().joinType()
                            + " outputTable=" + safeLogValue(join.configuration().outputTableName());
            case RenameNodeDefinition rename -> "sourceTable="
                    + safeLogValue(rename.configuration().sourceTableName())
                    + " outputTable=" + safeLogValue(rename.configuration().outputTableName())
                    + " mappingCount=" + rename.configuration().columnMappings().size();
            case FilterNodeDefinition filter -> {
                FilterSummary summary = filterSummary(filter.configuration().condition());
                yield "sourceTable=" + safeLogValue(filter.configuration().sourceTableName())
                        + " outputTable=" + safeLogValue(filter.configuration().outputTableName())
                        + " predicateCount=" + summary.predicates()
                        + " groupCount=" + summary.groups()
                        + " operators=" + safeLogValue(String.join(",", summary.operators()));
            }
            case SelectColumnsNodeDefinition selectColumns ->
                    "sourceTable="
                            + safeLogValue(selectColumns.configuration().sourceTableName())
                            + " outputTable="
                            + safeLogValue(selectColumns.configuration().outputTableName())
                            + " columnCount="
                            + selectColumns.configuration().columns().size()
                            + " columns="
                            + safeLogValue(String.join(",", selectColumns.configuration().columns()));
            case DeriveColumnsNodeDefinition deriveColumns -> {
                DeriveSummary summary = deriveSummary(deriveColumns);
                yield "sourceTable="
                        + safeLogValue(deriveColumns.configuration().sourceTableName())
                        + " outputTable="
                        + safeLogValue(deriveColumns.configuration().outputTableName())
                        + " derivationCount=" + deriveColumns.configuration().derivations().size()
                        + " replaceCount=" + summary.replaceCount()
                        + " targets=" + safeLogValue(String.join(",", summary.targets()))
                        + " expressionKinds="
                        + safeLogValue(String.join(",", summary.expressionKinds()))
                        + " functions=" + safeLogValue(String.join(",", summary.functions()));
            }
            case TypeCastNodeDefinition typeCast -> {
                long setNullCount = typeCast.configuration().casts().stream()
                        .filter(cast -> cast.failureStrategy()
                                == cn.superhuang.data.scalpel.contract.task.CastFailureStrategy.SET_NULL)
                        .count();
                String castSummary = typeCast.configuration().casts().stream()
                        .map(cast -> cast.columnName()
                                + ":" + cast.targetType().type()
                                + ":" + cast.failureStrategy())
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
                yield "sourceTable="
                        + safeLogValue(typeCast.configuration().sourceTableName())
                        + " outputTable="
                        + safeLogValue(typeCast.configuration().outputTableName())
                        + " castCount=" + typeCast.configuration().casts().size()
                        + " failCount="
                        + (typeCast.configuration().casts().size() - setNullCount)
                        + " setNullCount=" + setNullCount
                        + " casts=" + safeLogValue(castSummary);
            }
            case AggregateNodeDefinition aggregate -> {
                String aggregationSummary = aggregate.configuration().aggregations().stream()
                        .map(item -> item.function()
                                + ":" + (item.sourceColumnName() == null
                                ? "*" : item.sourceColumnName())
                                + ":" + item.outputColumnName()
                                + ":" + item.distinct())
                        .collect(java.util.stream.Collectors.joining(","));
                yield "sourceTable="
                        + safeLogValue(aggregate.configuration().sourceTableName())
                        + " outputTable="
                        + safeLogValue(aggregate.configuration().outputTableName())
                        + " groupByCount=" + aggregate.configuration().groupByColumns().size()
                        + " groupBy="
                        + safeLogValue(String.join(",", aggregate.configuration().groupByColumns()))
                        + " aggregationCount="
                        + aggregate.configuration().aggregations().size()
                        + " aggregations=" + safeLogValue(aggregationSummary);
            }
            case UnionNodeDefinition union ->
                    "inputCount=" + union.configuration().inputTableNames().size()
                            + " inputs="
                            + safeLogValue(String.join(",", union.configuration().inputTableNames()))
                            + " outputTable="
                            + safeLogValue(union.configuration().outputTableName())
                            + " mode=" + union.configuration().mode();
            case DeduplicateNodeDefinition deduplicate -> {
                String sortSummary = deduplicate.configuration().orderBy().stream()
                        .map(sort -> sort.columnName()
                                + ":" + sort.direction()
                                + ":NULLS_" + sort.nullOrdering())
                        .collect(java.util.stream.Collectors.joining(","));
                yield "sourceTable="
                        + safeLogValue(deduplicate.configuration().sourceTableName())
                        + " outputTable="
                        + safeLogValue(deduplicate.configuration().outputTableName())
                        + " keyCount=" + deduplicate.configuration().keyColumns().size()
                        + " keys="
                        + safeLogValue(String.join(",", deduplicate.configuration().keyColumns()))
                        + " keepStrategy=" + deduplicate.configuration().keepStrategy()
                        + " orderBy=" + safeLogValue(sortSummary);
            }
            case NullHandlingNodeDefinition nullHandling -> {
                long dropCount = nullHandling.configuration().rules().stream()
                        .filter(DropNullRowsRule.class::isInstance)
                        .count();
                String ruleSummary = nullHandling.configuration().rules().stream()
                        .map(rule -> switch (rule) {
                            case DropNullRowsRule drop ->
                                    "DROP_ROW:" + String.join(",", drop.columnNames())
                                            + ":" + drop.matchMode();
                            case FillNullLiteralRule fill ->
                                    "FILL_LITERAL:" + fill.columnName()
                                            + ":" + fill.value().dataType();
                        })
                        .collect(java.util.stream.Collectors.joining(";"));
                yield "sourceTable="
                        + safeLogValue(nullHandling.configuration().sourceTableName())
                        + " outputTable="
                        + safeLogValue(nullHandling.configuration().outputTableName())
                        + " ruleCount=" + nullHandling.configuration().rules().size()
                        + " dropCount=" + dropCount
                        + " fillCount="
                        + (nullHandling.configuration().rules().size() - dropCount)
                        + " rules=" + safeLogValue(ruleSummary);
            }
            case ValueMappingNodeDefinition valueMapping -> {
                int entryCount = valueMapping.configuration().rules().stream()
                        .mapToInt(rule -> rule.entries().size())
                        .sum();
                String ruleSummary = valueMapping.configuration().rules().stream()
                        .map(rule -> rule.columnName()
                                + ":" + rule.entries().size()
                                + ":" + rule.unmatchedStrategy()
                                + ":mapsToNull="
                                + rule.entries().stream()
                                        .anyMatch(entry -> entry.targetValue() == null))
                        .collect(java.util.stream.Collectors.joining(","));
                yield "sourceTable="
                        + safeLogValue(valueMapping.configuration().sourceTableName())
                        + " outputTable="
                        + safeLogValue(valueMapping.configuration().outputTableName())
                        + " ruleCount=" + valueMapping.configuration().rules().size()
                        + " entryCount=" + entryCount
                        + " rules=" + safeLogValue(ruleSummary);
            }
            case MaskFieldsNodeDefinition maskFields -> {
                long globalCount = maskFields.configuration().fieldRules().stream()
                        .filter(rule -> rule.ruleSource() == MaskingRuleSource.GLOBAL)
                        .count();
                String strategies = maskFields.configuration().fieldRules().stream()
                        .map(rule -> rule.definition().strategy().name())
                        .distinct()
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
                yield "fieldCount=" + maskFields.configuration().fieldRules().size()
                        + " globalRuleCount=" + globalCount
                        + " inlineRuleCount="
                        + (maskFields.configuration().fieldRules().size() - globalCount)
                        + " strategies=" + safeLogValue(strategies);
            }
            case JsonExtractNodeDefinition jsonExtract -> {
                String targetTypes = jsonExtract.configuration().extractions().stream()
                        .map(extraction -> extraction.targetType().type().name())
                        .distinct()
                        .sorted()
                        .collect(java.util.stream.Collectors.joining(","));
                yield "sourceTable="
                        + safeLogValue(jsonExtract.configuration().sourceTableName())
                        + " outputTable="
                        + safeLogValue(jsonExtract.configuration().outputTableName())
                        + " sourceColumn="
                        + safeLogValue(jsonExtract.configuration().sourceColumnName())
                        + " extractionCount="
                        + jsonExtract.configuration().extractions().size()
                        + " targetTypes=" + safeLogValue(targetTypes)
                        + " failureStrategy="
                        + jsonExtract.configuration().failureStrategy();
            }
            case WindowNodeDefinition window -> {
                String sortSummary = window.configuration().orderBy().stream()
                        .map(sort -> sort.columnName()
                                + ":" + sort.direction()
                                + ":NULLS_" + sort.nullOrdering())
                        .collect(java.util.stream.Collectors.joining(","));
                String functionSummary = window.configuration().functions().stream()
                        .map(CanvasTaskExecutor::windowFunctionSummary)
                        .collect(java.util.stream.Collectors.joining(","));
                yield "sourceTable="
                        + safeLogValue(window.configuration().sourceTableName())
                        + " outputTable="
                        + safeLogValue(window.configuration().outputTableName())
                        + " partitions="
                        + safeLogValue(String.join(",", window.configuration().partitionByColumns()))
                        + " orderBy=" + safeLogValue(sortSummary)
                        + " functions=" + safeLogValue(functionSummary);
            }
            case TopNNodeDefinition topN -> {
                String sortSummary = topN.configuration().orderBy().stream()
                        .map(sort -> sort.columnName()
                                + ":" + sort.direction()
                                + ":NULLS_" + sort.nullOrdering())
                        .collect(java.util.stream.Collectors.joining(","));
                yield "sourceTable="
                        + safeLogValue(topN.configuration().sourceTableName())
                        + " outputTable="
                        + safeLogValue(topN.configuration().outputTableName())
                        + " partitions="
                        + safeLogValue(String.join(",", topN.configuration().partitionByColumns()))
                        + " orderBy=" + safeLogValue(sortSummary)
                        + " limit=" + topN.configuration().limit()
                        + " tieStrategy=" + topN.configuration().tieStrategy();
            }
            case ModelOutputNodeDefinition output -> "sourceTable="
                    + safeLogValue(output.configuration().sourceTableName())
                    + " " + modelSummary(
                    "targetModelId=" + safeLogValue(output.configuration().targetModelId()),
                    metadataModel(metadataIndex, output.configuration().targetModelId()))
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
                            + " dataSourceId=" + safeLogValue(output.configuration().dataSourceId())
                            + " topic=" + safeLogValue(output.configuration().topic());
            case FileOutputNodeDefinition output -> fileOutputSummary(output, inputTables);
        };
    }

    private static String fileOutputSummary(
            FileOutputNodeDefinition output,
            List<CanvasTableSchema> inputTables
    ) {
        String summary = "sourceTable=" + safeLogValue(output.configuration().sourceTableName())
                + " dataSourceId=" + safeLogValue(output.configuration().dataSourceId())
                + " targetPath=" + safeLogValue(output.configuration().targetPath())
                + " format=" + output.configuration().formatOptions().getClass().getSimpleName()
                + " conflictPolicy=" + output.configuration().conflictPolicy()
                + spatialFileSchemaSummary(output, inputTables);
        if (output.configuration().formatOptions() instanceof FileOutputFormatOptions.Shapefile shapefile) {
            String fields = shapefile.attributeMappings() == null ? "" : shapefile.attributeMappings().stream()
                    .map(cn.superhuang.data.scalpel.contract.task.ShapefileAttributeMapping::targetFieldName)
                    .collect(java.util.stream.Collectors.joining(","));
            return summary
                    + " packageMode=" + shapefile.packageMode()
                    + " geometryColumn=" + safeLogValue(shapefile.geometryColumnName())
                    + " targetShapeType=" + shapefile.targetShapeType()
                    + " attributeCount=" + (shapefile.attributeMappings() == null
                    ? 0 : shapefile.attributeMappings().size())
                    + " attributeFields=" + safeLogValue(fields);
        }
        if (output.configuration().formatOptions() instanceof FileOutputFormatOptions.GeoParquet geoParquet) {
            return summary
                    + " geometryColumn=" + safeLogValue(geoParquet.geometryColumnName())
                    + " compression=" + geoParquet.compression()
                    + " coveringMode=" + geoParquet.coveringMode();
        }
        if (output.configuration().formatOptions() instanceof FileOutputFormatOptions.GeoJson geoJson) {
            return summary
                    + " baseName=" + safeLogValue(geoJson.baseName())
                    + " geometryColumn=" + safeLogValue(geoJson.geometryColumnName())
                    + " featureIdConfigured=" + (geoJson.idColumnName() != null)
                    + " ignoreNullProperties=" + geoJson.ignoreNullProperties();
        }
        return summary;
    }

    private static String spatialFileSchemaSummary(
            FileOutputNodeDefinition output,
            List<CanvasTableSchema> inputTables
    ) {
        String geometryColumnName = switch (output.configuration().formatOptions()) {
            case FileOutputFormatOptions.Shapefile shapefile -> shapefile.geometryColumnName();
            case FileOutputFormatOptions.GeoParquet geoParquet -> geoParquet.geometryColumnName();
            case FileOutputFormatOptions.GeoJson geoJson -> geoJson.geometryColumnName();
            default -> null;
        };
        if (geometryColumnName == null) return "";
        CanvasColumnSchema geometryColumn = inputTables.stream()
                .filter(table -> table.name().equals(output.configuration().sourceTableName()))
                .flatMap(table -> table.columns().stream())
                .filter(column -> column.name().equals(geometryColumnName))
                .filter(column -> column.geometry() != null)
                .findFirst()
                .orElse(null);
        if (geometryColumn == null) return "";
        return " geometryKind=" + geometryColumn.geometry().kind()
                + " crs=" + safeLogValue(
                geometryColumn.geometry().crs().authority()
                        + ":" + geometryColumn.geometry().crs().code())
                + " dimension=" + geometryColumn.geometry().dimension();
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
        String prefix = node instanceof ModelOutputNodeDefinition
                ? "模型输出写入成功"
                : node instanceof FileOutputNodeDefinition ? "文件输出写入成功" : "JDBC 输出写入成功";
        return metricAvailable ? prefix : prefix + "，输出行数指标不可用";
    }

    private static String nodePreparedMessage(CanvasNodeDefinition node) {
        return switch (node.nodeType()) {
            case MODEL_INPUT -> "模型输入已准备";
            case JDBC_INPUT -> "JDBC 输入已准备";
            case JDBC_QUERY_INPUT -> "JDBC 查询输入已准备";
            case FILE_DATASET_INPUT -> "文件数据集输入已准备";
            case HTTP_API_INPUT -> "HTTP API 输入已读取";
            case SPATIAL_SERVICE_INPUT -> "空间服务输入已读取";
            case KAFKA_INPUT -> "Kafka 输入已准备";
            case JOIN -> "Join 已准备";
            case GEOMETRY_CONSTRUCT -> "Geometry 构造已准备";
            case SPATIAL_TRANSFORM -> "空间转换已准备";
            case GEOMETRY_VALIDATE -> "Geometry 校验已准备";
            case GEOMETRY_REPAIR -> "Geometry 修复已准备";
            case GEOMETRY_BUFFER -> "Geometry Buffer 已准备";
            case GEOMETRY_EXPLODE -> "Geometry 拆分已准备";
            case SPATIAL_MEASURE -> "空间测量已准备";
            case GEOMETRY_SERIALIZE -> "Geometry 序列化已准备";
            case SPATIAL_CLIP -> "空间裁剪已准备";
            case SPATIAL_AGGREGATE -> "空间聚合已准备";
            case SPATIAL_JOIN -> "空间连接已准备";
            case STREAM_JOIN -> "Stream Join 已准备";
            case RENAME -> "重命名已准备";
            case FILTER -> "筛选已准备";
            case SELECT_COLUMNS -> "字段选择已准备";
            case DERIVE_COLUMNS -> "派生字段已准备";
            case TYPE_CAST -> "类型转换已准备";
            case AGGREGATE -> "聚合已准备";
            case UNION -> "数据合并已准备";
            case DEDUPLICATE -> "去重已准备";
            case NULL_HANDLING -> "空值处理已准备";
            case VALUE_MAPPING -> "值映射已准备";
            case MASK_FIELDS -> "字段脱敏已准备";
            case JSON_EXTRACT -> "JSON 提取已准备";
            case WINDOW -> "窗口计算已准备";
            case TOP_N -> "Top N 已准备";
            case MODEL_OUTPUT, JDBC_OUTPUT, KAFKA_OUTPUT, FILE_OUTPUT ->
                    throw new IllegalArgumentException("Output success is recorded after writing");
        };
    }

    private static String spatialMeasurementSummary(
            cn.superhuang.data.scalpel.contract.task.SpatialMeasurement measurement
    ) {
        return switch (measurement) {
            case cn.superhuang.data.scalpel.contract.task.SpatialMeasurement.Area item ->
                    "AREA:" + item.geometryColumnName() + ":" + item.mode()
                            + "->" + item.outputColumnName();
            case cn.superhuang.data.scalpel.contract.task.SpatialMeasurement.Length item ->
                    "LENGTH:" + item.geometryColumnName() + ":" + item.mode()
                            + "->" + item.outputColumnName();
            case cn.superhuang.data.scalpel.contract.task.SpatialMeasurement.Perimeter item ->
                    "PERIMETER:" + item.geometryColumnName() + ":" + item.mode()
                            + "->" + item.outputColumnName();
            case cn.superhuang.data.scalpel.contract.task.SpatialMeasurement.Distance item ->
                    "DISTANCE:" + item.leftGeometryColumnName() + ":"
                            + item.rightGeometryColumnName() + ":" + item.mode()
                            + "->" + item.outputColumnName();
            case cn.superhuang.data.scalpel.contract.task.SpatialMeasurement.X item ->
                    "X:" + item.geometryColumnName() + "->" + item.outputColumnName();
            case cn.superhuang.data.scalpel.contract.task.SpatialMeasurement.Y item ->
                    "Y:" + item.geometryColumnName() + "->" + item.outputColumnName();
        };
    }

    private static FilterSummary filterSummary(CanvasFilterCondition condition) {
        return switch (condition) {
            case CanvasFilterGroup group -> {
                int predicates = 0;
                int groups = 1;
                java.util.Set<String> operators = new java.util.TreeSet<>();
                operators.add(group.operator().name());
                for (CanvasFilterCondition child : group.children()) {
                    FilterSummary childSummary = filterSummary(child);
                    predicates += childSummary.predicates();
                    groups += childSummary.groups();
                    operators.addAll(childSummary.operators());
                }
                yield new FilterSummary(predicates, groups, operators);
            }
            case CanvasFieldPredicate predicate -> new FilterSummary(
                    1,
                    0,
                    java.util.Set.of(predicate.operator().name())
            );
        };
    }

    private static String windowFunctionSummary(WindowFunctionItem item) {
        return switch (item) {
            case WindowFunctionItem.RowNumber rowNumber ->
                    "ROW_NUMBER:" + rowNumber.outputColumnName();
            case WindowFunctionItem.Rank rank ->
                    "RANK:" + rank.outputColumnName();
            case WindowFunctionItem.DenseRank denseRank ->
                    "DENSE_RANK:" + denseRank.outputColumnName();
            case WindowFunctionItem.Lag lag ->
                    "LAG:" + lag.sourceColumnName() + ":" + lag.offset()
                            + ":" + lag.outputColumnName();
            case WindowFunctionItem.Lead lead ->
                    "LEAD:" + lead.sourceColumnName() + ":" + lead.offset()
                            + ":" + lead.outputColumnName();
            case WindowFunctionItem.Count count ->
                    "COUNT:" + (count.sourceColumnName() == null
                            ? "*" : count.sourceColumnName())
                            + ":" + count.outputColumnName()
                            + ":" + frameSummary(count.frame());
            case WindowFunctionItem.Sum sum ->
                    "SUM:" + sum.sourceColumnName() + ":" + sum.outputColumnName()
                            + ":" + frameSummary(sum.frame());
            case WindowFunctionItem.Avg avg ->
                    "AVG:" + avg.sourceColumnName() + ":" + avg.outputColumnName()
                            + ":" + frameSummary(avg.frame());
            case WindowFunctionItem.Min min ->
                    "MIN:" + min.sourceColumnName() + ":" + min.outputColumnName()
                            + ":" + frameSummary(min.frame());
            case WindowFunctionItem.Max max ->
                    "MAX:" + max.sourceColumnName() + ":" + max.outputColumnName()
                            + ":" + frameSummary(max.frame());
            case WindowFunctionItem.FirstValue first ->
                    "FIRST_VALUE:" + first.sourceColumnName()
                            + ":ignoreNulls=" + first.ignoreNulls()
                            + ":" + first.outputColumnName()
                            + ":" + frameSummary(first.frame());
            case WindowFunctionItem.LastValue last ->
                    "LAST_VALUE:" + last.sourceColumnName()
                            + ":ignoreNulls=" + last.ignoreNulls()
                            + ":" + last.outputColumnName()
                            + ":" + frameSummary(last.frame());
        };
    }

    private static String frameSummary(
            cn.superhuang.data.scalpel.contract.task.RowsWindowFrame frame
    ) {
        return frame.type() + ":" + frameBoundarySummary(frame.start())
                + ":" + frameBoundarySummary(frame.end());
    }

    private static String frameBoundarySummary(RowsFrameBoundary boundary) {
        return switch (boundary) {
            case RowsFrameBoundary.UnboundedPreceding ignored ->
                    "UNBOUNDED_PRECEDING";
            case RowsFrameBoundary.Preceding preceding ->
                    "PRECEDING(" + preceding.offset() + ")";
            case RowsFrameBoundary.CurrentRow ignored -> "CURRENT_ROW";
            case RowsFrameBoundary.Following following ->
                    "FOLLOWING(" + following.offset() + ")";
            case RowsFrameBoundary.UnboundedFollowing ignored ->
                    "UNBOUNDED_FOLLOWING";
        };
    }

    private record FilterSummary(int predicates, int groups, java.util.Set<String> operators) {
    }

    private static DeriveSummary deriveSummary(DeriveColumnsNodeDefinition node) {
        int replaceCount = 0;
        java.util.Set<String> targets = new java.util.TreeSet<>();
        java.util.Set<String> expressionKinds = new java.util.TreeSet<>();
        java.util.Set<String> functions = new java.util.TreeSet<>();
        for (cn.superhuang.data.scalpel.contract.task.ColumnDerivation derivation
                : node.configuration().derivations()) {
            if (derivation.replaceExisting()) replaceCount++;
            targets.add(derivation.targetColumnName());
            collectExpressionSummary(
                    derivation.expression(),
                    expressionKinds,
                    functions
            );
        }
        return new DeriveSummary(replaceCount, targets, expressionKinds, functions);
    }

    private static void collectExpressionSummary(
            CanvasExpression expression,
            java.util.Set<String> kinds,
            java.util.Set<String> functions
    ) {
        switch (expression) {
            case ColumnExpression ignored -> kinds.add("COLUMN");
            case LiteralExpression ignored -> kinds.add("LITERAL");
            case BinaryExpression binary -> {
                kinds.add("BINARY");
                collectExpressionSummary(binary.left(), kinds, functions);
                collectExpressionSummary(binary.right(), kinds, functions);
            }
            case FunctionExpression function -> {
                kinds.add("FUNCTION");
                functions.add(function.function().name());
                function.arguments().forEach(
                        argument -> collectExpressionSummary(argument, kinds, functions)
                );
            }
            case CaseWhenExpression caseWhen -> {
                kinds.add("CASE_WHEN");
                caseWhen.branches().forEach(
                        branch -> collectExpressionSummary(branch.result(), kinds, functions)
                );
                if (caseWhen.elseExpression() != null) {
                    collectExpressionSummary(caseWhen.elseExpression(), kinds, functions);
                }
            }
        }
    }

    private record DeriveSummary(
            int replaceCount,
            java.util.Set<String> targets,
            java.util.Set<String> expressionKinds,
            java.util.Set<String> functions
    ) {
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
                error.sqlState(), error.diagnosticId(),
                RunnerLogSanitizer.spatialSafeStackTrace(throwable));
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
        LOGGER.error("{}\n{}", summary, RunnerLogSanitizer.spatialSafeStackTrace(throwable));
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
            CanvasPreparedOutput jdbcOutput,
            CanvasPreparedFileOutput fileOutput,
            Instant startedAt
    ) {
    }
}
