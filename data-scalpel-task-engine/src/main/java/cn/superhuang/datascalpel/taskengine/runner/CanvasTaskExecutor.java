package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperationContext;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperationResult;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperatorRegistry;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperators;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasRuntimeValues;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedOutput;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedSnapshotSyncOutput;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedFileOutput;
import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialPreview;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialSpec;
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
import cn.superhuang.data.scalpel.contract.task.JdbcInputTableSelection;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcSnapshotSyncOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasFieldPredicate;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterCondition;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterGroup;
import cn.superhuang.data.scalpel.contract.task.FilterConditionMode;
import cn.superhuang.data.scalpel.contract.task.FilterNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FilterOperation;
import cn.superhuang.data.scalpel.contract.task.SqlTransformNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SelectColumnsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.BinaryExpression;
import cn.superhuang.data.scalpel.contract.task.CanvasExpression;
import cn.superhuang.data.scalpel.contract.task.CaseWhenExpression;
import cn.superhuang.data.scalpel.contract.task.ColumnExpression;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FunctionExpression;
import cn.superhuang.data.scalpel.contract.task.LiteralExpression;
import cn.superhuang.data.scalpel.contract.task.RuntimeValueExpression;
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
import cn.superhuang.data.scalpel.contract.task.ModelSnapshotSyncOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionMetrics;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.NodeExecutionState;
import cn.superhuang.datascalpel.taskengine.contract.OutputWriteExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.OutputWriteExecutionState;
import cn.superhuang.datascalpel.taskengine.contract.OutputWritesMetrics;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeJdbcConnection;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeS3Connection;
import cn.superhuang.datascalpel.taskengine.contract.SnapshotSyncMetrics;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionError;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionState;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import org.apache.spark.sql.DataFrameReader;
import org.apache.spark.sql.DataFrameWriter;
import org.apache.spark.sql.Column;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class CanvasTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(CanvasTaskExecutor.class);
    private static final DialectRegistry JDBC_DIALECTS = BuiltInDialects.registry();
    private final CanvasTaskCompiler compiler = new CanvasTaskCompiler();
    private final CanvasNodeOperatorRegistry nodeOperators = CanvasNodeOperators.builtInRegistry();
    private final RunnerFailureClassifier failureClassifier = new RunnerFailureClassifier();
    private final JdbcSnapshotSyncExecutor snapshotSyncExecutor = new JdbcSnapshotSyncExecutor();

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
        CanvasRuntimeValues runtimeValues = runtimeValues(manifest, startedAt);
        SparkSession.Builder builder = SedonaSparkSupport.builder()
                .appName("DataScalpel Task Runner " + manifest.execution().executionId())
                .config("spark.ui.enabled", "false")
                .config("spark.sql.shuffle.partitions", "4")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .config("spark.speculation", "false");
        String redactionRegex = jdbcReadOptionRedactionRegex(manifest);
        builder.config("spark.redaction.regex", redactionRegex)
                .config("spark.sql.redaction.options.regex", redactionRegex);
        if (sparkMode == RunnerSparkMode.LOCAL) builder.master("local[*]");
        SparkSession spark = SedonaSparkSupport.initialize(builder.getOrCreate());
        try (SparkOutputMetricsCollector metricsCollector = new SparkOutputMetricsCollector(spark)) {
            sparkStarted.accept(spark.sparkContext().applicationId());
            MetadataIndex metadataIndex = MetadataIndex.create(manifest.metadataSnapshot());
            CanvasCompilation compilation = manifest.canvasTrial() == null
                    ? compiler.compile(
                    manifest.task().definition(), metadataIndex,
                    SedonaSparkSupport.childSession(spark), new AtomicBoolean())
                    : compiler.compileTrial(
                    manifest.task().definition(), CanvasExecutionMode.BATCH, metadataIndex,
                    SedonaSparkSupport.childSession(spark), new AtomicBoolean(),
                    manifest.canvasTrial().targetNodeId());
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
                    manifest, metadataIndex, compilation, spark, metricsCollector, startedAt, runtimeValues);
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
            Instant startedAt,
            CanvasRuntimeValues runtimeValues
    ) {
        CanvasGraphPlan plan = manifest.canvasTrial() == null
                ? CanvasGraphPlan.create(manifest.task().definition())
                : CanvasGraphPlan.createForTrial(
                manifest.task().definition(), CanvasExecutionMode.BATCH,
                manifest.canvasTrial().targetNodeId());
        Map<UUID, RuntimeDataSource> runtimeSources = runtimeSources(manifest.runtimeDataSources());
        List<Map<String, SparkCanvasTable>> propagated = new ArrayList<>();
        for (int ignored = 0; ignored < manifest.task().definition().nodes().size(); ignored++) propagated.add(Map.of());
        List<PreparedOutput> preparedOutputs = new ArrayList<>();
        List<NodeExecutionResult> nodeResults = new ArrayList<>();
        RuntimeCanvasNodeDataAccess dataAccess = new RuntimeCanvasNodeDataAccess(
                spark,
                runtimeSources,
                manifest.runtimeFileStorage(),
                manifest.runtimeFileInputs(),
                manifest.execution().executionId().toString(),
                manifest.execution().taskId().toString(),
                manifest.execution().attempt(),
                null,
                null,
                null
        );

        try {
        for (int nodeIndex : plan.topologicalOrder()) {
            CanvasNodeDefinition node = plan.nodeAt(nodeIndex);
            ExecutionFailurePhase phase = phase(node);
            RunnerFailureContext failureContext = RunnerFailureContext.node(node, phase, null);
            Instant nodeStartedAt = Instant.now();
            try {
                failureContext = RunnerFailureContext.node(
                        node, phase, resourceName(node, runtimeSources, metadataIndex));
                logNodeStart(
                        manifest,
                        node,
                        phase,
                        nodeSummary(
                                node,
                                metadataIndex,
                                compilation.nodeResults().get(nodeIndex).inputTables(),
                                compilation.nodeResults().get(nodeIndex).outputTables())
                );
                checkDeadline(manifest);
                Map<String, SparkCanvasTable> inputs = mergeInputs(
                        plan.predecessorsOf(nodeIndex), propagated, node.id());
                CanvasNodeOperationResult operation = executeNodeOperator(
                        node, inputs, spark, metadataIndex, dataAccess, runtimeValues);
                if (node instanceof ModelOutputNodeDefinition
                        || node instanceof JdbcOutputNodeDefinition) {
                    if (operation.preparedOutputs().isEmpty()) {
                        throw new RunnerExecutionException(
                                "OUTPUT_NOT_PREPARED", "输出节点未生成写入计划", node.id());
                    }
                    operation.preparedOutputs().forEach(output ->
                            preparedOutputs.add(preparedOutput(output, nodeStartedAt)));
                } else if (node instanceof ModelSnapshotSyncOutputNodeDefinition
                        || node instanceof JdbcSnapshotSyncOutputNodeDefinition) {
                    if (operation.preparedSnapshotSyncOutput() == null) {
                        throw new RunnerExecutionException(
                                "OUTPUT_NOT_PREPARED", "快照同步节点未生成写入计划", node.id());
                    }
                    preparedOutputs.add(preparedSnapshotSyncOutput(
                            operation.preparedSnapshotSyncOutput(), nodeStartedAt));
                } else if (node instanceof FileOutputNodeDefinition) {
                    if (operation.preparedFileOutputs().isEmpty()) {
                        throw new RunnerExecutionException(
                                "OUTPUT_NOT_PREPARED", "文件输出节点未生成写入计划", node.id());
                    }
                    operation.preparedFileOutputs().forEach(output ->
                            preparedOutputs.add(preparedFileOutput(output, nodeStartedAt)));
                } else {
                    propagated.set(nodeIndex, operation.propagatedTables());
                    if (manifest.canvasTrial() != null
                            && manifest.canvasTrial().targetNodeId().equals(node.id())) {
                        CanvasTrialPreview preview = createCanvasTrialPreview(
                                node, operation.propagatedTables(), manifest.canvasTrial());
                        nodeResults.add(success(
                                node, phase, nodeStartedAt, null, "节点试运行完成"));
                        logNodeSuccess(manifest, node, phase, nodeStartedAt, null);
                        return successfulTrialResult(
                                manifest, startedAt, orderedResults(plan, nodeResults), preview);
                    }
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
        for (int outputIndex = 0; outputIndex < preparedOutputs.size();) {
            PreparedOutput first = preparedOutputs.get(outputIndex);
            CanvasNodeDefinition node = first.node();
            if (first.snapshotSyncOutput() != null) {
                try {
                    checkDeadline(manifest);
                    SnapshotSyncMetrics metrics = snapshotSyncExecutor.execute(
                            first.snapshotSyncOutput(), manifest.snapshotSyncLimits());
                    long rowsWritten = metrics.rowsWritten();
                    affectedRows = addAffectedRows(affectedRows, rowsWritten);
                    String message = node instanceof ModelSnapshotSyncOutputNodeDefinition
                            ? "模型快照同步完成" : "JDBC 快照同步完成";
                    nodeResults.add(success(
                            node, ExecutionFailurePhase.WRITE, first.startedAt(),
                            rowsWritten, metrics, message));
                    logNodeSuccess(
                            manifest, node, ExecutionFailurePhase.WRITE,
                            first.startedAt(), rowsWritten);
                } catch (Throwable throwable) {
                    TaskExecutionError error = failureClassifier.classify(
                            throwable, RunnerFailureContext.node(
                                    node, ExecutionFailurePhase.WRITE, first.displayTarget()));
                    nodeResults.add(failed(node, ExecutionFailurePhase.WRITE, first.startedAt(), error));
                    logNodeFailure(
                            manifest, node, ExecutionFailurePhase.WRITE,
                            first.startedAt(), error, throwable);
                    return failedResult(
                            manifest, startedAt, orderedResults(plan, nodeResults), affectedRows, error);
                }
                outputIndex++;
                continue;
            }

            int groupEnd = outputIndex + 1;
            while (groupEnd < preparedOutputs.size()
                    && preparedOutputs.get(groupEnd).snapshotSyncOutput() == null
                    && node.id().equals(preparedOutputs.get(groupEnd).node().id())) {
                groupEnd++;
            }
            List<PreparedOutput> group = preparedOutputs.subList(outputIndex, groupEnd);
            List<OutputWriteExecutionResult> writeResults = new ArrayList<>(group.size());
            Long nodeAffectedRows = 0L;
            for (int writeIndex = 0; writeIndex < group.size(); writeIndex++) {
                PreparedOutput output = group.get(writeIndex);
                SparkOutputMetricsCollector.ObservedOutput observed = null;
                Dataset<Row> cachedUpsert = null;
                String jobGroup = "datascalpel:" + manifest.execution().executionId()
                        + ":" + manifest.execution().attempt() + ":" + node.id()
                        + ":" + output.writeId();
                try {
                    checkDeadline(manifest);
                    logOutputWriteStart(manifest, output);
                    spark.sparkContext().setJobGroup(
                            jobGroup, "DataScalpel " + node.nodeType() + " " + node.id(), true);
                    Dataset<Row> writeDataset = output.dataset();
                    if (output.writeMode() == JdbcWriteMode.UPSERT) {
                        cachedUpsert = writeDataset.persist(StorageLevel.MEMORY_AND_DISK());
                        SpatialJdbcRuntimeSupport.validateUpsertKeys(output.jdbcOutput(), cachedUpsert);
                        writeDataset = cachedUpsert;
                    }
                    observed = metricsCollector.observe(
                            manifest.execution().executionId(), manifest.execution().attempt(),
                            node.id() + "." + output.writeId(), writeDataset);
                    executePreparedOutput(spark, manifest, output, observed.dataset());
                    SparkOutputMetricsCollector.OutputWriteMetrics metrics =
                            metricsCollector.completeSuccess(observed);
                    Long previousAffectedRows = affectedRows;
                    affectedRows = addAffectedRows(affectedRows, metrics.rowsWritten());
                    nodeAffectedRows = addAffectedRows(nodeAffectedRows, metrics.rowsWritten());
                    if (metrics.metricAvailable() && previousAffectedRows != null && affectedRows == null) {
                        LOGGER.warn(
                                "event=OUTPUT_ROWS_OVERFLOW executionId={} nodeId={} writeId={}",
                                manifest.execution().executionId(), safeLogValue(node.id()),
                                safeLogValue(output.writeId()));
                    }
                    writeResults.add(outputWriteResult(
                            output, OutputWriteExecutionState.SUCCESS, metrics.rowsWritten(), null));
                    logOutputWriteSuccess(manifest, output, metrics.rowsWritten());
                } catch (Throwable throwable) {
                    if (observed != null) metricsCollector.completeFailure(observed);
                    TaskExecutionError error = failureClassifier.classify(
                            throwable, RunnerFailureContext.node(
                                    node, ExecutionFailurePhase.WRITE, output.displayTarget()));
                    writeResults.add(outputWriteResult(
                            output, OutputWriteExecutionState.FAILED, null, error.code()));
                    for (int skipped = writeIndex + 1; skipped < group.size(); skipped++) {
                        writeResults.add(outputWriteResult(
                                group.get(skipped), OutputWriteExecutionState.SKIPPED, null, null));
                    }
                    OutputWritesMetrics outputMetrics = new OutputWritesMetrics(writeResults);
                    nodeResults.add(failed(
                            node, ExecutionFailurePhase.WRITE, first.startedAt(),
                            nodeAffectedRows, outputMetrics, error));
                    logOutputWriteFailure(manifest, output, error);
                    logNodeFailure(
                            manifest, node, ExecutionFailurePhase.WRITE,
                            first.startedAt(), error, throwable);
                    return failedResult(
                            manifest, startedAt, orderedResults(plan, nodeResults), affectedRows, error);
                } finally {
                    if (cachedUpsert != null) cachedUpsert.unpersist();
                    spark.sparkContext().clearJobGroup();
                }
            }
            OutputWritesMetrics outputMetrics = new OutputWritesMetrics(writeResults);
            String message = outputSuccessMessage(node, nodeAffectedRows != null);
            nodeResults.add(success(
                    node, ExecutionFailurePhase.WRITE, first.startedAt(),
                    nodeAffectedRows, outputMetrics, message));
            logNodeSuccess(
                    manifest, node, ExecutionFailurePhase.WRITE, first.startedAt(), nodeAffectedRows);
            outputIndex = groupEnd;
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

    private static CanvasTrialPreview createCanvasTrialPreview(
            CanvasNodeDefinition node,
            Map<String, SparkCanvasTable> outputTables,
            CanvasTrialSpec trialSpec
    ) {
        SparkCanvasTable table = outputTables.get(trialSpec.tableName());
        if (table == null) {
            throw new RunnerExecutionException(
                    "CANVAS_TRIAL_TABLE_NOT_FOUND", "试运行目标表已变化", node.id());
        }
        Set<String> requested = new HashSet<>(trialSpec.columnNames());
        List<CanvasColumnSchema> selectedColumns = table.schema().columns().stream()
                .filter(column -> requested.contains(column.name()))
                .toList();
        if (selectedColumns.size() != requested.size()) {
            throw new RunnerExecutionException(
                    "CANVAS_TRIAL_COLUMN_NOT_FOUND", "试运行目标字段已变化", node.id());
        }
        Column[] projection = selectedColumns.stream()
                .map(column -> table.dataset().col(sparkIdentifier(column.name())))
                .toArray(Column[]::new);
        List<String> captured = table.dataset().select(projection)
                .limit(CanvasTrialPreview.MAX_ROWS + 1)
                .toJSON()
                .collectAsList();
        boolean truncated = captured.size() > CanvasTrialPreview.MAX_ROWS;
        List<String> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int bytes = 0;
        for (String row : captured.subList(0, Math.min(captured.size(), CanvasTrialPreview.MAX_ROWS))) {
            int rowBytes = row.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + rowBytes > CanvasTrialPreview.MAX_CONTENT_CHARACTERS) {
                truncated = true;
                warnings.add("预览数据达到 4 MB 上限，部分数据行未返回");
                break;
            }
            rows.add(row);
            bytes += rowBytes;
        }
        warnings.add("未显式排序时，预览行顺序不保证稳定");
        boolean eventTimeSelected = table.schema().eventTimeColumn() != null
                && requested.contains(table.schema().eventTimeColumn());
        CanvasTableSchema selectedSchema = new CanvasTableSchema(
                table.schema().name(),
                null,
                selectedColumns,
                table.schema().datasetKind(),
                eventTimeSelected ? table.schema().eventTimeColumn() : null,
                eventTimeSelected ? table.schema().watermarkDelay() : null
        );
        return new CanvasTrialPreview(
                node.id(), node.name(), selectedSchema, rows, truncated, warnings);
    }

    private static String sparkIdentifier(String value) {
        return "`" + value.replace("`", "``") + "`";
    }

    private static TaskExecutionResult successfulTrialResult(
            TaskExecutionManifest manifest,
            Instant startedAt,
            List<NodeExecutionResult> nodeResults,
            CanvasTrialPreview preview
    ) {
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
                null,
                nodeResults,
                cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_CANVAS,
                null,
                null,
                null,
                null,
                preview,
                null
        );
    }

    private CanvasNodeOperationResult executeNodeOperator(
            CanvasNodeDefinition node,
            Map<String, SparkCanvasTable> inputs,
            SparkSession spark,
            MetadataIndex metadataIndex,
            RuntimeCanvasNodeDataAccess dataAccess,
            CanvasRuntimeValues runtimeValues
    ) {
        return nodeOperators.apply(
                node,
                inputs,
                new CanvasNodeOperationContext(
                        spark,
                        metadataIndex,
                        new RunnerCanvasNodeIssueSink(node.id()),
                        dataAccess,
                        CanvasExecutionMode.BATCH,
                        runtimeValues
                )
        );
    }

    private static CanvasRuntimeValues runtimeValues(
            TaskExecutionManifest manifest,
            Instant startedAt
    ) {
        if (manifest == null || manifest.execution() == null
                || manifest.execution().executionId() == null || startedAt == null) {
            throw new RunnerExecutionException(
                    "RUNTIME_CONTEXT_UNAVAILABLE",
                    "任务运行上下文不完整，无法生成派生字段",
                    null
            );
        }
        return CanvasRuntimeValues.execution(manifest.execution().executionId(), startedAt);
    }

    private static PreparedOutput preparedOutput(
            CanvasPreparedOutput output,
            Instant startedAt
    ) {
        return new PreparedOutput(
                output.node(),
                output.writeId(),
                output.sourceTableName(),
                output.runtimeDataSource(),
                output.qualifiedTableName(),
                output.displayTarget(),
                output.writeMode(),
                output.dataset(),
                output,
                null,
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
                output.writeId(),
                output.sourceTableName(),
                output.runtimeDataSource(),
                null,
                output.targetDisplayName(),
                null,
                output.dataset(),
                null,
                output,
                null,
                startedAt
        );
    }

    private static PreparedOutput preparedSnapshotSyncOutput(
            CanvasPreparedSnapshotSyncOutput output,
            Instant startedAt
    ) {
        return new PreparedOutput(
                output.node(),
                null,
                null,
                output.runtimeDataSource(),
                null,
                output.displayTarget(),
                null,
                output.dataset(),
                null,
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
        switch (output.formatOptions()) {
            case FileOutputFormatOptions.Csv csv -> fileWriter(dataset, output.conflictPolicy())
                    .format("csv")
                    .option("encoding", "UTF-8")
                    .option("header", csv.header())
                    .option("delimiter", csv.delimiter())
                    .option("quote", csv.quote())
                    .option("escape", csv.escape())
                    .option("nullValue", csv.nullValue())
                    .save(output.targetUri());
            case FileOutputFormatOptions.JsonLines json -> fileWriter(dataset, output.conflictPolicy())
                    .format("json")
                    .option("encoding", "UTF-8")
                    .option("ignoreNullFields", json.ignoreNullFields())
                    .save(output.targetUri());
            case FileOutputFormatOptions.Parquet ignored -> fileWriter(dataset, output.conflictPolicy())
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

    private static void executePreparedOutput(
            SparkSession spark,
            TaskExecutionManifest manifest,
            PreparedOutput output,
            Dataset<Row> dataset
    ) throws Exception {
        if (output.fileOutput() != null) {
            writeFile(spark, output.fileOutput(), dataset, manifest.execution().executionId());
            return;
        }
        if (output.writeMode() == JdbcWriteMode.OVERWRITE) {
            requireOverwriteSupported(output.runtimeDataSource());
            truncate(output.runtimeDataSource(), output.qualifiedTableName());
        }
        if (output.writeMode() == JdbcWriteMode.UPSERT) {
            SpatialJdbcRuntimeSupport.writeUpsert(output.jdbcOutput(), dataset);
        } else if (output.jdbcOutput() != null
                && SpatialJdbcRuntimeSupport.requiresSpatialWriter(output.jdbcOutput())) {
            SpatialJdbcRuntimeSupport.writeSpatial(output.jdbcOutput(), dataset);
        } else {
            write(output.runtimeDataSource(), output.qualifiedTableName(), dataset);
        }
    }

    private static OutputWriteExecutionResult outputWriteResult(
            PreparedOutput output,
            OutputWriteExecutionState state,
            Long affectedRows,
            String errorCode
    ) {
        return new OutputWriteExecutionResult(
                output.writeId(), output.sourceTableName(), output.displayTarget(),
                state, affectedRows, errorCode);
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

    static void truncate(RuntimeDataSource source, String qualifiedTableName) throws Exception {
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

    static void requireOverwriteSupported(RuntimeDataSource source) {
        switch (source.databaseType()) {
            case TDENGINE_WEBSOCKET, TDENGINE_RESTFUL -> throw new RunnerExecutionException(
                    "OVERWRITE_DATABASE_NOT_SUPPORTED",
                    "TDengine 不支持普通 JDBC OVERWRITE 输出",
                    null
            );
            default -> {
                return;
            }
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

    static String qualifiedTable(
            RuntimeDataSource source,
            String catalogName,
            String schemaName,
            String tableName
    ) {
        return JDBC_DIALECTS.require(source.databaseType().name()).qualifiedName(
                new TableIdentifier(catalogName, schemaName, tableName)
        );
    }

    private static String quote(RuntimeDatabaseType type, String value) {
        return JDBC_DIALECTS.require(type.name()).quoteIdentifier(value);
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
        String expectedDriver = JDBC_DIALECTS.require(source.databaseType().name()).driverClassName();
        String expectedPrefix = source.databaseType().jdbcUrlPrefix();
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
                || manifest.executionTaskType() != cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_CANVAS
                || manifest.modelQuality() != null
                || manifest.task() == null || manifest.task().type() != TaskType.CANVAS
                || manifest.task().definition() == null || manifest.metadataSnapshot() == null
                || manifest.metadataSnapshot().models() == null
                || manifest.metadataSnapshot().fileDatasetTables() == null
                || manifest.runtimeDataSources() == null
                || manifest.runtimeFileInputs() == null
                || manifest.snapshotSyncLimits() == null
                || !manifest.runtimeFileInputs().isEmpty() && manifest.runtimeFileStorage() == null) {
            throw new RunnerExecutionException("INVALID_MANIFEST", "任务运行 manifest 不完整", null);
        }
        if (manifest.canvasTrial() != null) {
            long targetCount = manifest.task().definition().nodes().stream()
                    .filter(node -> manifest.canvasTrial().targetNodeId().equals(node.id()))
                    .count();
            boolean containsOutput = manifest.task().definition().nodes().stream()
                    .anyMatch(node -> switch (node.nodeType()) {
                        case MODEL_OUTPUT, MODEL_SNAPSHOT_SYNC_OUTPUT,
                                JDBC_OUTPUT, JDBC_SNAPSHOT_SYNC_OUTPUT,
                                KAFKA_OUTPUT, FILE_OUTPUT -> true;
                        default -> false;
                    });
            if (targetCount != 1 || containsOutput) {
                throw new RunnerExecutionException(
                        "INVALID_MANIFEST", "Canvas 试运行闭包或目标节点无效", null);
            }
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
        return success(node, phase, startedAt, rowsWritten, null, message);
    }

    private static NodeExecutionResult success(
            CanvasNodeDefinition node,
            ExecutionFailurePhase phase,
            Instant startedAt,
            Long rowsWritten,
            NodeExecutionMetrics metrics,
            String message
    ) {
        Instant endedAt = Instant.now();
        return new NodeExecutionResult(
                node.id(), node.nodeType().name(), node.name(), NodeExecutionState.SUCCESS, phase,
                startedAt, endedAt, elapsedMillis(startedAt, endedAt), rowsWritten, metrics, message, null);
    }

    private static NodeExecutionResult failed(
            CanvasNodeDefinition node,
            ExecutionFailurePhase phase,
            Instant startedAt,
            TaskExecutionError error
    ) {
        return failed(node, phase, startedAt, null, null, error);
    }

    private static NodeExecutionResult failed(
            CanvasNodeDefinition node,
            ExecutionFailurePhase phase,
            Instant startedAt,
            Long rowsWritten,
            NodeExecutionMetrics metrics,
            TaskExecutionError error
    ) {
        Instant endedAt = Instant.now();
        return new NodeExecutionResult(
                node.id(), node.nodeType().name(), node.name(), NodeExecutionState.FAILED, phase,
                startedAt, endedAt, elapsedMillis(startedAt, endedAt), rowsWritten,
                metrics, error.message(), error);
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
        return failedResult(manifest, startedAt, nodeResults, null, error);
    }

    private static TaskExecutionResult failedResult(
            TaskExecutionManifest manifest,
            Instant startedAt,
            List<NodeExecutionResult> nodeResults,
            Long affectedRows,
            TaskExecutionError error
    ) {
        return failedResult(
                manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                startedAt, nodeResults, affectedRows, error);
    }

    private static TaskExecutionResult failedResult(
            UUID executionId,
            UUID runId,
            int attempt,
            Instant startedAt,
            List<NodeExecutionResult> nodeResults,
            TaskExecutionError error
    ) {
        return failedResult(executionId, runId, attempt, startedAt, nodeResults, null, error);
    }

    private static TaskExecutionResult failedResult(
            UUID executionId,
            UUID runId,
            int attempt,
            Instant startedAt,
            List<NodeExecutionResult> nodeResults,
            Long affectedRows,
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
                affectedRows,
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
            case MODEL_INPUT, JDBC_INPUT, JDBC_INCREMENTAL_INPUT, JDBC_QUERY_INPUT,
                    FILE_DATASET_INPUT, HTTP_API_INPUT,
                    SPATIAL_SERVICE_INPUT, KAFKA_INPUT, TDENGINE_TMQ_INPUT ->
                    ExecutionFailurePhase.READ;
            case JOIN, GEOMETRY_CONSTRUCT, SPATIAL_TRANSFORM, GEOMETRY_VALIDATE,
                    GEOMETRY_REPAIR, GEOMETRY_DERIVE, GEOMETRY_SIMPLIFY, SPATIAL_NEAREST,
                    SPATIAL_SUMMARIZE_WITHIN, SPATIAL_OVERLAY,
                    TRACK_RECONSTRUCT, TRACK_MOTION_STATISTICS, TRACK_FIND_DWELL,
                    TRACK_DETECT_INCIDENTS,
                    SPATIAL_BIN_AGGREGATE, SPATIAL_POINT_CLUSTER, SPATIAL_CENTER_DISPERSION,
                    GEOMETRY_BUFFER, GEOMETRY_EXPLODE,
                    SPATIAL_MEASURE, GEOMETRY_SERIALIZE, SPATIAL_CLIP,
                    SPATIAL_AGGREGATE, SPATIAL_JOIN, STREAM_JOIN,
                    RENAME, FILTER, SQL_TRANSFORM, SELECT_COLUMNS, DERIVE_COLUMNS, TYPE_CAST,
                    AGGREGATE, UNION, DEDUPLICATE, NULL_HANDLING, VALUE_MAPPING, MASK_FIELDS,
                            JSON_EXTRACT, WINDOW, TOP_N ->
                    ExecutionFailurePhase.PROCESS;
            case MODEL_OUTPUT, MODEL_SNAPSHOT_SYNC_OUTPUT,
                    JDBC_OUTPUT, JDBC_SNAPSHOT_SYNC_OUTPUT,
                    KAFKA_OUTPUT, FILE_OUTPUT -> ExecutionFailurePhase.WRITE;
        };
    }

    private static String resourceName(
            CanvasNodeDefinition node,
            Map<UUID, RuntimeDataSource> runtimeSources,
            MetadataIndex metadataIndex
    ) {
        return switch (node) {
            case ModelInputNodeDefinition input -> input.configuration().models().stream()
                    .map(selection -> displayModelTable(runtimeSources, metadataIndex, selection.modelId()))
                    .collect(java.util.stream.Collectors.joining(", "));
            case JdbcInputNodeDefinition input -> displayJdbcInputTables(input);
            case cn.superhuang.data.scalpel.contract.task.JdbcIncrementalInputNodeDefinition input -> displayTable(
                    runtimeSources, input.configuration().dataSourceId(), input.configuration().tableName());
            case JdbcQueryInputNodeDefinition input -> input.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition input -> {
                yield input.configuration().tables().stream().map(selection -> {
                    MetadataIndex.FileDatasetTableEntry table = metadataIndex.fileDatasetTable(
                            UUID.fromString(selection.fileDatasetTableId()));
                    return table == null ? selection.fileDatasetTableId() : table.metadata().code();
                }).collect(java.util.stream.Collectors.joining(", "));
            }
            case cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition input ->
                    input.configuration().resources().stream()
                            .map(cn.superhuang.data.scalpel.contract.task.HttpApiInputResourceSelection::outputTableName)
                            .collect(java.util.stream.Collectors.joining(", "));
            case cn.superhuang.data.scalpel.contract.task.SpatialServiceInputNodeDefinition input ->
                    input.configuration().resources().stream()
                            .map(cn.superhuang.data.scalpel.contract.task.SpatialServiceInputResourceSelection::outputTableName)
                            .collect(java.util.stream.Collectors.joining(", "));
            case cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition input ->
                    input.configuration().topic();
            case TdEngineTmqInputNodeDefinition input -> input.configuration().topicName();
            case JoinNodeDefinition join -> join.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometryConstructNodeDefinition construct ->
                    construct.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialTransformNodeDefinition transform ->
                    transform.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometryValidateNodeDefinition validate ->
                    validate.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometryRepairNodeDefinition repair ->
                    repair.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometryDeriveNodeDefinition derive ->
                    derive.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.GeometrySimplifyNodeDefinition simplify ->
                    simplify.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialNearestNodeDefinition nearest ->
                    nearest.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition summarize ->
                    summarize.configuration().outputTableName() + (summarize.configuration().usesLinkedGroupResult()
                            ? ", " + summarize.configuration().groupResult().outputTableName() : "");
            case cn.superhuang.data.scalpel.contract.task.SpatialOverlayNodeDefinition overlay ->
                    overlay.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition track ->
                    track.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.TrackMotionStatisticsNodeDefinition track ->
                    track.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.TrackFindDwellNodeDefinition track ->
                    track.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition track ->
                    track.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition aggregate ->
                    aggregate.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialPointClusterNodeDefinition cluster ->
                    cluster.configuration().outputTableName();
            case cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionNodeDefinition analysis ->
                    analysis.configuration().outputTableName();
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
            case RenameNodeDefinition rename -> operationOutputPreview(rename.configuration().operations());
            case FilterNodeDefinition filter -> operationOutputPreview(filter.configuration().operations());
            case SqlTransformNodeDefinition sqlTransform -> sqlTransform.configuration().outputTableName();
            case SelectColumnsNodeDefinition selectColumns ->
                    operationOutputPreview(selectColumns.configuration().operations());
            case DeriveColumnsNodeDefinition deriveColumns ->
                    operationOutputPreview(deriveColumns.configuration().operations());
            case TypeCastNodeDefinition typeCast ->
                    operationOutputPreview(typeCast.configuration().operations());
            case AggregateNodeDefinition aggregate ->
                    aggregate.configuration().outputTableName();
            case UnionNodeDefinition union ->
                    union.configuration().outputTableName();
            case DeduplicateNodeDefinition deduplicate ->
                    operationOutputPreview(deduplicate.configuration().operations());
            case NullHandlingNodeDefinition nullHandling ->
                    operationOutputPreview(nullHandling.configuration().operations());
            case ValueMappingNodeDefinition valueMapping ->
                    operationOutputPreview(valueMapping.configuration().operations());
            case MaskFieldsNodeDefinition maskFields ->
                    operationOutputPreview(maskFields.configuration().operations());
            case JsonExtractNodeDefinition jsonExtract ->
                    operationOutputPreview(jsonExtract.configuration().operations());
            case WindowNodeDefinition window ->
                    window.configuration().outputTableName();
            case TopNNodeDefinition topN ->
                    operationOutputPreview(topN.configuration().operations());
            case ModelOutputNodeDefinition output -> safePreview(output.configuration().writes().stream()
                    .map(write -> displayModelName(metadataIndex, write.targetModelId())).toList());
            case ModelSnapshotSyncOutputNodeDefinition output -> displayModelTable(
                    runtimeSources, metadataIndex, output.configuration().targetModelId());
            case JdbcOutputNodeDefinition output -> safePreview(output.configuration().writes().stream()
                    .map(cn.superhuang.data.scalpel.contract.task.JdbcOutputWrite::targetTableName).toList());
            case JdbcSnapshotSyncOutputNodeDefinition output -> displayTable(
                    runtimeSources, output.configuration().dataSourceId(), output.configuration().targetTableName());
            case cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition output ->
                    safePreview(output.configuration().writes().stream()
                            .map(cn.superhuang.data.scalpel.contract.task.KafkaOutputWrite::topic).toList());
            case FileOutputNodeDefinition output -> safePreview(output.configuration().writes().stream()
                    .map(cn.superhuang.data.scalpel.contract.task.FileOutputWrite::targetPath).toList());
        };
    }

    private static String operationOutputPreview(
            List<? extends cn.superhuang.data.scalpel.contract.task.ProcessorOperation> operations
    ) {
        if (operations == null) return null;
        return safePreview(operations.stream()
                .filter(java.util.Objects::nonNull)
                .map(operation -> operation.output() == null
                        || blank(operation.output().outputTableName())
                        ? operation.sourceTableName() : operation.output().outputTableName())
                .toList());
    }

    private static String safePreview(List<String> values) {
        if (values == null) return null;
        List<String> populated = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
        String preview = populated.stream().limit(2)
                .collect(java.util.stream.Collectors.joining(", "));
        if (populated.size() > 2) return preview + " 等 " + populated.size() + " 项";
        return preview.isBlank() ? null : preview;
    }

    private static String displayModelName(MetadataIndex metadataIndex, String modelId) {
        MetadataIndex.ModelEntry model = metadataModel(metadataIndex, modelId);
        if (model == null) return modelId;
        return model.metadata().name() + " · " + model.metadata().code();
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
        String namespace = displayNamespace(
                source,
                source.connection().catalogName(),
                source.connection().schemaName()
        );
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
        String namespace = displayNamespace(
                source,
                model.metadata().catalogName(),
                model.metadata().schemaName()
        );
        return namespace == null || namespace.isBlank()
                ? model.metadata().physicalTableName()
                : namespace + "." + model.metadata().physicalTableName();
    }

    private static String displayNamespace(
            RuntimeDataSource source,
            String catalogName,
            String schemaName
    ) {
        return switch (JDBC_DIALECTS.require(source.databaseType().name()).definition().namespaceMode()) {
            case CATALOG -> catalogName;
            case SCHEMA -> schemaName;
            case CATALOG_AND_SCHEMA -> {
                if (catalogName == null || catalogName.isBlank()) yield schemaName;
                if (schemaName == null || schemaName.isBlank()) yield catalogName;
                yield catalogName + "." + schemaName;
            }
        };
    }

    static String nodeSummary(CanvasNodeDefinition node, MetadataIndex metadataIndex) {
        return nodeSummary(node, metadataIndex, List.of(), List.of());
    }

    static String nodeSummary(
            CanvasNodeDefinition node,
            MetadataIndex metadataIndex,
            List<CanvasTableSchema> inputTables
    ) {
        return nodeSummary(node, metadataIndex, inputTables, List.of());
    }

    static String nodeSummary(
            CanvasNodeDefinition node,
            MetadataIndex metadataIndex,
            List<CanvasTableSchema> inputTables,
            List<CanvasTableSchema> outputTables
    ) {
        return switch (node) {
            case ModelInputNodeDefinition input -> "modelCount=" + input.configuration().models().size()
                    + " models=" + safeLogValue(input.configuration().models().stream()
                    .map(cn.superhuang.data.scalpel.contract.task.ModelInputSelection::modelId)
                    .collect(java.util.stream.Collectors.joining(",")))
                    + " runtimeSchemaValidation=true";
            case JdbcInputNodeDefinition input -> "dataSourceId=" + safeLogValue(input.configuration().dataSourceId())
                    + " tableCount=" + jdbcInputTableNames(input).size()
                    + " configuredReadOptionTableCount=" + jdbcInputConfiguredTableCount(input)
                    + " readOptionCount=" + jdbcInputReadOptionCount(input)
                    + " tables=" + safeLogValue(jdbcInputTablePreview(input))
                    + " runtimeSchemaValidation=true";
            case cn.superhuang.data.scalpel.contract.task.JdbcIncrementalInputNodeDefinition input ->
                    "dataSourceId=" + safeLogValue(input.configuration().dataSourceId())
                            + " table=" + safeLogValue(input.configuration().tableName())
                            + " cursorColumn=" + safeLogValue(input.configuration().incrementalTimeColumn())
                            + " triggerIntervalSeconds=" + input.configuration().triggerIntervalSeconds()
                            + " visibilityDelaySeconds=" + input.configuration().visibilityDelaySeconds()
                            + " runtimeSchemaValidation=true";
            case JdbcQueryInputNodeDefinition input -> "dataSourceId="
                    + safeLogValue(input.configuration().dataSourceId())
                    + " outputTable=" + safeLogValue(input.configuration().outputTableName())
                    + " fieldCount=" + input.configuration().outputColumns().size()
                    + " runtimeSchemaValidation=true";
            case cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition input -> {
                yield "fileDatasetId=" + safeLogValue(input.configuration().fileDatasetId())
                        + " tableCount=" + input.configuration().tables().size()
                        + " fileDatasetTableIds=" + safeLogValue(input.configuration().tables().stream()
                        .map(cn.superhuang.data.scalpel.contract.task.FileDatasetInputTableSelection::fileDatasetTableId)
                        .collect(java.util.stream.Collectors.joining(",")));
            }
            case cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition input ->
                    "dataSourceId=" + safeLogValue(input.configuration().dataSourceId())
                            + " resourceCount=" + input.configuration().resources().size()
                            + " runtimeParameterCount=" + input.configuration().resources().stream()
                            .mapToInt(resource -> resource.runtimeParameters().size()).sum();
            case cn.superhuang.data.scalpel.contract.task.SpatialServiceInputNodeDefinition input ->
                    "dataSourceId=" + safeLogValue(input.configuration().dataSourceId())
                            + " resourceCount=" + input.configuration().resources().size();
            case cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition input ->
                    kafkaInputSummary(input.configuration());
            case TdEngineTmqInputNodeDefinition input ->
                    "dataSourceId=" + safeLogValue(input.configuration().dataSourceId())
                            + " topic=" + safeLogValue(input.configuration().topicName())
                            + " supertable=" + safeLogValue(input.configuration().supertableName())
                            + " startingOffsets=" + input.configuration().startingOffsets()
                            + " fieldCount=" + java.util.Optional.ofNullable(
                                    metadataIndex.dataSource(UUID.fromString(input.configuration().dataSourceId())))
                            .map(source -> source.tdEngineTmqTopic(input.configuration().topicName()))
                            .map(topic -> topic.columns().size()).orElse(0);
            case JoinNodeDefinition join -> "leftTable=" + safeLogValue(join.configuration().leftTableName())
                    + " rightTable=" + safeLogValue(join.configuration().rightTableName())
                    + " joinType=" + join.configuration().joinType()
                    + " conditionCount=" + join.configuration().conditions().size()
                    + " outputFieldCount=" + join.configuration().outputColumns().stream()
                    .filter(cn.superhuang.data.scalpel.contract.task.JoinOutputColumn::included).count()
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
            case cn.superhuang.data.scalpel.contract.task.GeometryDeriveNodeDefinition derive -> {
                String derivations = derive.configuration().derivations().stream()
                        .map(item -> item.kind() + ":" + item.sourceColumnName()
                                + ":" + item.outputColumnName() + ":" + item.geometryPolicy())
                        .collect(java.util.stream.Collectors.joining(","));
                yield "sourceTable=" + safeLogValue(derive.configuration().sourceTableName())
                        + " outputTable=" + safeLogValue(derive.configuration().outputTableName())
                        + " derivationCount=" + derive.configuration().derivations().size()
                        + " derivations=" + safeLogValue(derivations);
            }
            case cn.superhuang.data.scalpel.contract.task.GeometrySimplifyNodeDefinition simplify ->
                    "sourceTable=" + safeLogValue(simplify.configuration().sourceTableName())
                            + " outputTable=" + safeLogValue(simplify.configuration().outputTableName())
                            + " geometryColumn="
                            + safeLogValue(simplify.configuration().geometryColumnName())
                            + " outputColumn="
                            + safeLogValue(simplify.configuration().outputColumnName())
                            + " algorithm=" + simplify.configuration().algorithm()
                            + " geometryPolicy=" + simplify.configuration().geometryPolicy()
                            + " toleranceUnit=" + simplify.configuration().toleranceUnit();
            case cn.superhuang.data.scalpel.contract.task.SpatialNearestNodeDefinition nearest ->
                    "sourceTable=" + safeLogValue(nearest.configuration().sourceTableName())
                            + " sourceGeometry="
                            + safeLogValue(nearest.configuration().sourceGeometryColumnName())
                            + " candidateTable="
                            + safeLogValue(nearest.configuration().candidateTableName())
                            + " candidateGeometry="
                            + safeLogValue(nearest.configuration().candidateGeometryColumnName())
                            + " candidateId="
                            + safeLogValue(nearest.configuration().candidateIdColumnName())
                            + " method=" + nearest.configuration().distanceMethod()
                            + " matching=" + (nearest.configuration().usesExactMatching() ? "EXACT_DISTANCE" : "LEGACY_KNN")
                            + " sourceId=" + safeLogValue(nearest.configuration().matching() == null ? null : nearest.configuration().matching().sourceIdColumnName())
                            + " connectionLines=" + nearest.configuration().outputsConnectionLines()
                            + " nearestCount=" + nearest.configuration().nearestCount()
                            + " outputFieldCount=" + nearest.configuration().outputColumns().stream()
                            .filter(cn.superhuang.data.scalpel.contract.task.JoinOutputColumn::included).count()
                            + " outputTable="
                            + safeLogValue(nearest.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition summarize ->
                    "regionMode=" + (summarize.configuration().usesGridRegions() ? "PLANAR_GRID" : "AREA_TABLE")
                            + " areaTable=" + (summarize.configuration().usesGridRegions() ? "GENERATED_GRID" : safeLogValue(summarize.configuration().areaTableName()))
                            + " areaGeometry="
                            + (summarize.configuration().usesGridRegions() ? "GENERATED" : safeLogValue(summarize.configuration().areaGeometryColumnName()))
                            + " summaryTable="
                            + safeLogValue(summarize.configuration().summaryTableName())
                            + " summaryGeometry="
                            + safeLogValue(summarize.configuration().summaryGeometryColumnName())
                            + " statisticCount=" + summarize.configuration().statistics().size()
                            + " apportionedCount=" + summarize.configuration().statistics().stream()
                            .filter(cn.superhuang.data.scalpel.contract.task.SpatialWithinStatistic::apportionsTotal).count()
                            + " weightedCount=" + summarize.configuration().statistics().stream()
                            .filter(cn.superhuang.data.scalpel.contract.task.SpatialWithinStatistic::usesGeographicWeight).count()
                            + " grouped=" + (summarize.configuration().groupSummary() != null)
                            + " linkedGroupResult=" + summarize.configuration().usesLinkedGroupResult()
                            + " resultTableCount=" + (summarize.configuration().usesLinkedGroupResult() ? 2 : 1)
                            + " temporal=" + (summarize.configuration().temporalSlicing() != null)
                            + " calendar=" + (summarize.configuration().temporalSlicing() != null && summarize.configuration().temporalSlicing().usesCalendar())
                            + " outputTable="
                            + safeLogValue(summarize.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.SpatialOverlayNodeDefinition overlay ->
                    "leftTable=" + safeLogValue(overlay.configuration().leftTableName())
                            + " leftGeometry=" + safeLogValue(overlay.configuration().leftGeometryColumnName())
                            + " rightTable=" + safeLogValue(overlay.configuration().rightTableName())
                            + " rightGeometry=" + safeLogValue(overlay.configuration().rightGeometryColumnName())
                            + " operation=" + overlay.configuration().operation()
                            + " familyGeometry=" + overlay.configuration().usesFamilyGeometry()
                            + " outputFieldCount=" + overlay.configuration().outputColumns().stream()
                            .filter(cn.superhuang.data.scalpel.contract.task.JoinOutputColumn::included).count()
                            + " outputTable=" + safeLogValue(overlay.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition track ->
                    "sourceTable=" + safeLogValue(track.configuration().sourceTableName())
                            + " pointGeometry=" + safeLogValue(track.configuration().pointGeometryColumnName())
                            + " trackIdFieldCount=" + track.configuration().trackIdColumns().size()
                            + " summaryCount=" + track.configuration().summaryStatistics().size()
                            + " orderedSegments=" + track.configuration().usesOrderedReconstruction()
                            + " methodPath=" + track.configuration().usesMethodPath()
                            + " areaGeometry=" + track.configuration().usesAreaGeometry()
                            + " distanceMethod=" + track.configuration().distanceMethod()
                            + " areaSamplingConfigured=" + (track.configuration().usesAreaGeometry()
                                && track.configuration().distanceMethod() == cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod.GEODESIC
                                && track.configuration().reconstruction().areaGeometry().geodesicBoundary() != null)
                            + " bufferWindowCount=" + (track.configuration().usesAreaGeometry()
                                && track.configuration().reconstruction().areaGeometry().bufferMode() == cn.superhuang.data.scalpel.contract.task.TrackBufferMode.EXPRESSION
                                ? track.configuration().reconstruction().areaGeometry().windowBindings().size() : 0)
                            + " bufferMode=" + (track.configuration().usesAreaGeometry()
                                ? track.configuration().reconstruction().areaGeometry().bufferMode() : "INACTIVE")
                            + " splitExpression=" + (track.configuration().usesOrderedReconstruction()
                                && track.configuration().reconstruction().splitExpression() != null
                                && track.configuration().reconstruction().splitExpression().active())
                            + " splitBoundary=" + (track.configuration().usesOrderedReconstruction()
                                ? track.configuration().reconstruction().effectiveBoundaryOption() : "LEGACY")
                            + " outputTable=" + safeLogValue(track.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.TrackMotionStatisticsNodeDefinition track ->
                    "sourceTable=" + safeLogValue(track.configuration().sourceTableName())
                            + " pointGeometry=" + safeLogValue(track.configuration().pointGeometryColumnName())
                            + " trackIdFieldCount=" + track.configuration().trackIdColumns().size()
                            + " metricCount=" + track.configuration().metrics().size()
                            + " observationWindow=" + track.configuration().usesObservationWindow()
                            + " windowStatisticCount=" + (track.configuration().windowOptions() == null ? 0 : track.configuration().windowOptions().statistics().size())
                            + " orderFieldCount=" + (track.configuration().windowOptions() == null ? 0 : track.configuration().windowOptions().orderByColumns().size())
                            + " outputTable=" + safeLogValue(track.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.TrackFindDwellNodeDefinition track ->
                    "sourceTable=" + safeLogValue(track.configuration().sourceTableName())
                            + " pointGeometry=" + safeLogValue(track.configuration().pointGeometryColumnName())
                            + " trackIdFieldCount=" + track.configuration().trackIdColumns().size()
                            + " summaryCount=" + track.configuration().summaryStatistics().size()
                            + " referenceCenter=" + track.configuration().usesReferenceCenter()
                            + " resultMode=" + (track.configuration().usesReferenceCenter() && track.configuration().rangeOptions() != null
                                ? track.configuration().rangeOptions().resultMode() : "LEGACY")
                            + " orderFieldCount=" + (track.configuration().rangeOptions() == null
                                ? 0 : track.configuration().rangeOptions().orderByColumns().size())
                            + " outputGeometryKind=" + track.configuration().outputGeometryKind()
                            + " outputTable=" + safeLogValue(track.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition track ->
                    "sourceTable=" + safeLogValue(track.configuration().sourceTableName())
                            + " trackIdFieldCount=" + track.configuration().trackIdColumns().size()
                            + " hasEndCondition=" + (track.configuration().endCondition() != null)
                            + " incidentSemantics=" + track.configuration().effectiveIncidentSemantics()
                            + " conditionWindowCount=" + track.configuration().conditionWindows().size()
                            + " orderFieldCount=" + track.configuration().orderByColumns().size()
                            + " fixedTimeBoundary=" + (track.configuration().boundaries().fixedTimeBoundary() != null)
                            + " resultMode=" + track.configuration().resultMode()
                            + " outputTable=" + safeLogValue(track.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition aggregate ->
                    "sourceTable=" + safeLogValue(aggregate.configuration().sourceTableName())
                            + " pointGeometry="
                            + safeLogValue(aggregate.configuration().pointGeometryColumnName())
                            + " binShape=" + aggregate.configuration().binShape()
                            + " h3Mode=" + (aggregate.configuration().h3() == null ? null : aggregate.configuration().h3().mode())
                            + " h3Resolution=" + (aggregate.configuration().h3() == null ? null : aggregate.configuration().h3().resolution())
                            + " binSizeSemantics=" + aggregate.configuration().effectiveBinSizeSemantics()
                            + " planarGridConfigured=" + (aggregate.configuration().planarGrid() != null)
                            + " explicitPlanarBounds=" + (aggregate.configuration().binShape() != cn.superhuang.data.scalpel.contract.task.SpatialBinShape.H3
                                && aggregate.configuration().planarGrid() != null && aggregate.configuration().planarGrid().usesExplicitBounds())
                            + " binSizeUnit=" + aggregate.configuration().binSizeUnit()
                            + " includeEmptyBins=" + aggregate.configuration().includeEmptyBins()
                            + " statisticCount=" + aggregate.configuration().statistics().size()
                            + " grouped=" + (aggregate.configuration().groupSummary() != null)
                            + " temporal=" + (aggregate.configuration().temporalSlicing() != null)
                            + " calendar=" + (aggregate.configuration().temporalSlicing() != null && aggregate.configuration().temporalSlicing().usesCalendar())
                            + " outputTable=" + safeLogValue(aggregate.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.SpatialPointClusterNodeDefinition cluster ->
                    "sourceTable=" + safeLogValue(cluster.configuration().sourceTableName())
                            + " pointGeometry="
                            + safeLogValue(cluster.configuration().pointGeometryColumnName())
                            + " featureId=" + safeLogValue(cluster.configuration().featureIdColumnName())
                            + " dbscanMode=" + (cluster.configuration().parameters() instanceof cn.superhuang.data.scalpel.contract.task.SpatialPointClusterParameters.Dbscan
                            ? cluster.configuration().dbscan() == null ? "LEGACY_SPATIAL" : cluster.configuration().dbscan().mode() : "INACTIVE")
                            + " diagnosticCount=" + (cluster.configuration().parameters() instanceof cn.superhuang.data.scalpel.contract.task.SpatialPointClusterParameters.Hdbscan && cluster.configuration().hdbscan() != null ? 4 : 0)
                            + " distanceMethod=" + cluster.configuration().distanceMethod()
                            + " algorithm=" + (cluster.configuration().parameters() == null
                            ? null : cluster.configuration().parameters().getClass().getSimpleName())
                            + " outputTable=" + safeLogValue(cluster.configuration().outputTableName());
            case cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionNodeDefinition analysis ->
                    "sourceTable=" + safeLogValue(analysis.configuration().sourceTableName())
                            + " pointGeometry="
                            + safeLogValue(analysis.configuration().pointGeometryColumnName())
                            + " groupFieldCount=" + analysis.configuration().groupByColumns().size()
                            + " weighted=" + !blank(analysis.configuration().weightColumnName())
                            + " analysisCount=" + analysis.configuration().analyses().size()
                            + " resultMode=" + analysis.configuration().resultMode()
                            + " projectedOriginalFieldCount=" + (analysis.configuration().separateResults()
                            ? analysis.configuration().analyses().stream().filter(a -> a.kind() == cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionKind.CENTRAL_FEATURE && a.centralFeatureColumns() != null)
                            .flatMap(a -> a.centralFeatureColumns().stream()).filter(c -> c != null && c.included()).count() : 0)
                            + " resultTableCount=" + (analysis.configuration().separateResults() ? analysis.configuration().analyses().size() : 1)
                            + " outputTable=" + safeLogValue(analysis.configuration().outputTableName());
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
                            + " conditionCount=" + join.configuration().conditions().size()
                            + " outputFieldCount=" + join.configuration().outputColumns().stream()
                            .filter(cn.superhuang.data.scalpel.contract.task.JoinOutputColumn::included).count()
                            + " outputTable=" + safeLogValue(join.configuration().outputTableName());
            case RenameNodeDefinition rename -> processorOperationsSummary(
                    rename.configuration().operations());
            case FilterNodeDefinition filter -> filterOperationsSummary(
                    filter.configuration().operations());
            case SqlTransformNodeDefinition sqlTransform -> sqlTransformSummary(
                    sqlTransform, inputTables, outputTables);
            case SelectColumnsNodeDefinition selectColumns -> processorOperationsSummary(
                    selectColumns.configuration().operations());
            case DeriveColumnsNodeDefinition deriveColumns -> {
                DeriveSummary summary = deriveSummary(deriveColumns);
                yield "tableCount=" + summary.tableCount()
                        + " globalDerivationCount=" + summary.globalDerivationCount()
                        + " localDerivationCount=" + summary.localDerivationCount()
                        + " effectiveDerivationCount=" + summary.effectiveDerivationCount()
                        + " targets=" + safeLogValue(String.join(",", summary.targets()))
                        + " expressionKinds="
                        + safeLogValue(String.join(",", summary.expressionKinds()))
                        + " runtimeValues="
                        + safeLogValue(String.join(",", summary.runtimeValues()))
                        + " functions=" + safeLogValue(String.join(",", summary.functions()));
            }
            case TypeCastNodeDefinition typeCast -> typeCastOperationsSummary(
                    typeCast.configuration().operations());
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
            case DeduplicateNodeDefinition deduplicate -> processorOperationsSummary(
                    deduplicate.configuration().operations());
            case NullHandlingNodeDefinition nullHandling -> processorOperationsSummary(
                    nullHandling.configuration().operations());
            case ValueMappingNodeDefinition valueMapping -> processorOperationsSummary(
                    valueMapping.configuration().operations());
            case MaskFieldsNodeDefinition maskFields -> processorOperationsSummary(
                    maskFields.configuration().operations());
            case JsonExtractNodeDefinition jsonExtract -> processorOperationsSummary(
                    jsonExtract.configuration().operations());
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
            case TopNNodeDefinition topN -> processorOperationsSummary(
                    topN.configuration().operations());
            case ModelOutputNodeDefinition output -> modelOutputSummary(output, metadataIndex);
            case ModelSnapshotSyncOutputNodeDefinition output -> "sourceTable="
                    + safeLogValue(output.configuration().sourceTableName())
                    + " " + modelSummary(
                    "targetModelId=" + safeLogValue(output.configuration().targetModelId()),
                    metadataModel(metadataIndex, output.configuration().targetModelId()))
                    + " keyColumns=" + safeLogValue(String.join(",", output.configuration().keyColumns()))
                    + " mappingCount=" + output.configuration().columnMappings().size()
                    + " deleteAction=" + output.configuration().deletePolicy().action()
                    + " deleteRowsLimit=" + output.configuration().deletePolicy().maxDeleteRows()
                    + " deleteRatioLimit=" + output.configuration().deletePolicy().maxDeleteRatio()
                    + " stages=TARGET_SCHEMA,MATERIALIZE,LOCK,COMPARE,WRITE";
            case JdbcOutputNodeDefinition output -> jdbcOutputSummary(output);
            case JdbcSnapshotSyncOutputNodeDefinition output -> "sourceTable="
                    + safeLogValue(output.configuration().sourceTableName())
                    + " dataSourceId=" + safeLogValue(output.configuration().dataSourceId())
                    + " targetTable=" + safeLogValue(output.configuration().targetTableName())
                    + " keyColumns=" + safeLogValue(String.join(",", output.configuration().keyColumns()))
                    + " mappingCount=" + output.configuration().columnMappings().size()
                    + " deleteAction=" + output.configuration().deletePolicy().action()
                    + " deleteRowsLimit=" + output.configuration().deletePolicy().maxDeleteRows()
                    + " deleteRatioLimit=" + output.configuration().deletePolicy().maxDeleteRatio()
                    + " stages=TARGET_SCHEMA,MATERIALIZE,LOCK,COMPARE,WRITE";
            case cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition output ->
                    kafkaOutputSummary(output);
            case FileOutputNodeDefinition output -> fileOutputSummary(output, inputTables);
        };
    }

    private static String fileOutputSummary(
            FileOutputNodeDefinition output,
            List<CanvasTableSchema> inputTables
    ) {
        var writes = output.configuration().writes();
        String formats = writes.stream()
                .map(write -> write.formatOptions() == null
                        ? "UNCONFIGURED" : write.formatOptions().getClass().getSimpleName())
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        String policies = writes.stream()
                .map(write -> String.valueOf(write.conflictPolicy()))
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        return "dataSourceId=" + safeLogValue(output.configuration().dataSourceId())
                + " writeCount=" + writes.size()
                + " sources=" + safeLogValue(safePreview(writes.stream()
                .map(cn.superhuang.data.scalpel.contract.task.FileOutputWrite::sourceTableName).toList()))
                + " targets=" + safeLogValue(safePreview(writes.stream()
                .map(cn.superhuang.data.scalpel.contract.task.FileOutputWrite::targetPath).toList()))
                + " formats=" + safeLogValue(formats)
                + " conflictPolicies=" + safeLogValue(policies);
    }

    private static String sqlTransformSummary(
            SqlTransformNodeDefinition sqlTransform,
            List<CanvasTableSchema> inputTables,
            List<CanvasTableSchema> outputTables
    ) {
        String sql = sqlTransform.configuration().sql();
        List<String> referencedTables = inputTables.stream()
                .map(CanvasTableSchema::name)
                .filter(tableName -> referencesSqlTable(sql, tableName))
                .toList();
        int outputFieldCount = outputTables.stream()
                .filter(table -> sqlTransform.configuration().outputTableName().equals(table.name()))
                .findFirst().map(table -> table.columns().size()).orElse(0);
        return "sqlSha256=" + safeLogValue(sha256(sql))
                + " sqlLength=" + sql.length()
                + " referencedTableCount=" + referencedTables.size()
                + " referencedTables=" + safeLogValue(safePreview(referencedTables))
                + " outputTable=" + safeLogValue(sqlTransform.configuration().outputTableName())
                + " outputFieldCount=" + outputFieldCount;
    }

    private static boolean referencesSqlTable(String sql, String tableName) {
        String quoted = "`" + tableName.replace("`", "``") + "`";
        if (sql.contains(quoted)) return true;
        if (!tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) return false;
        return Pattern.compile("(^|[^A-Za-z0-9_$])" + Pattern.quote(tableName)
                        + "(?=$|[^A-Za-z0-9_$])", Pattern.CASE_INSENSITIVE)
                .matcher(sql)
                .find();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String processorOperationsSummary(
            List<? extends cn.superhuang.data.scalpel.contract.task.ProcessorOperation> operations
    ) {
        if (operations == null) return "tableCount=0 ruleCount=0";
        int ruleCount = operations.stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(CanvasTaskExecutor::processorOperationItemCount)
                .sum();
        return "tableCount=" + operations.size()
                + " ruleCount=" + ruleCount
                + " sources=" + safeLogValue(safePreview(operations.stream()
                .filter(java.util.Objects::nonNull)
                .map(cn.superhuang.data.scalpel.contract.task.ProcessorOperation::sourceTableName)
                .toList()))
                + " outputs=" + safeLogValue(operationOutputPreview(operations));
    }

    private static int processorOperationItemCount(
            cn.superhuang.data.scalpel.contract.task.ProcessorOperation operation
    ) {
        return switch (operation) {
            case cn.superhuang.data.scalpel.contract.task.RenameOperation value -> size(value.columnMappings());
            case cn.superhuang.data.scalpel.contract.task.FilterOperation value ->
                    value.mode() == FilterConditionMode.SQL_EXPRESSION
                            ? (value.sqlExpression().isBlank() ? 0 : 1)
                            : value.condition() == null ? 0 : filterSummary(value.condition()).predicates();
            case cn.superhuang.data.scalpel.contract.task.SelectColumnsOperation value -> size(value.columns());
            case cn.superhuang.data.scalpel.contract.task.DeriveColumnsOperation value -> size(value.derivations());
            case cn.superhuang.data.scalpel.contract.task.TypeCastOperation value -> size(value.casts());
            case cn.superhuang.data.scalpel.contract.task.DeduplicateOperation value -> size(value.keyColumns());
            case cn.superhuang.data.scalpel.contract.task.NullHandlingOperation value -> size(value.rules());
            case cn.superhuang.data.scalpel.contract.task.ValueMappingOperation value -> size(value.rules());
            case cn.superhuang.data.scalpel.contract.task.MaskFieldsOperation value -> size(value.fieldRules());
            case cn.superhuang.data.scalpel.contract.task.JsonExtractOperation value -> size(value.extractions());
            case cn.superhuang.data.scalpel.contract.task.TopNOperation value -> size(value.orderBy());
            default -> 0;
        };
    }

    private static int size(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static String kafkaInputSummary(
            cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration configuration
    ) {
        if (configuration == null) return "valueFormat=UNCONFIGURED outputFieldCount=0 metadataFields=";
        int payloadFieldCount = configuration.effectiveValueFormat()
                == cn.superhuang.data.scalpel.contract.task.KafkaInputValueFormat.JSON
                ? configuration.valueSchema() == null || configuration.valueSchema().columns() == null
                ? 0 : configuration.valueSchema().columns().size()
                : 1;
        return "valueFormat=" + configuration.effectiveValueFormat()
                + " outputFieldCount="
                + (payloadFieldCount + configuration.effectiveMetadataFields().size())
                + " metadataFields=" + safeLogValue(configuration.effectiveMetadataFields()
                .stream().map(Enum::name).sorted()
                .collect(java.util.stream.Collectors.joining(",")));
    }

    private static String typeCastOperationsSummary(
            List<cn.superhuang.data.scalpel.contract.task.TypeCastOperation> operations
    ) {
        String base = processorOperationsSummary(operations);
        if (operations == null) return base;
        List<cn.superhuang.data.scalpel.contract.task.ColumnTypeCast> casts = operations.stream()
                .filter(java.util.Objects::nonNull)
                .flatMap(operation -> operation.casts() == null
                        ? java.util.stream.Stream.<cn.superhuang.data.scalpel.contract.task.ColumnTypeCast>empty()
                        : operation.casts().stream())
                .filter(java.util.Objects::nonNull)
                .toList();
        String epochTimestampUnits = casts.stream()
                .map(cast -> cast.epochTimestampUnit() == null
                        ? "LEGACY_SECONDS" : cast.epochTimestampUnit().name())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        List<cn.superhuang.data.scalpel.contract.task.ColumnTypeCast> temporalCasts = casts.stream()
                .filter(cast -> cast.stringTemporalParseOptions() != null)
                .toList();
        String temporalTargetTypes = temporalCasts.stream()
                .map(cast -> cast.targetType() == null || cast.targetType().type() == null
                        ? "UNCONFIGURED" : cast.targetType().type().name())
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        String temporalZoneModes = temporalCasts.stream()
                .map(cast -> cast.stringTemporalParseOptions().zoneMode() == null
                        ? "UNCONFIGURED" : cast.stringTemporalParseOptions().zoneMode().name())
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        String sourceTimeZones = safePreview(temporalCasts.stream()
                .map(cast -> cast.stringTemporalParseOptions().sourceTimeZone())
                .filter(zone -> zone != null && !zone.isBlank()).toList());
        String patternMetadata = temporalCasts.stream()
                .map(cast -> cast.stringTemporalParseOptions().pattern())
                .map(pattern -> pattern == null ? "UNCONFIGURED" : pattern.length() + ":" + sha256(pattern))
                .collect(java.util.stream.Collectors.joining(","));
        List<cn.superhuang.data.scalpel.contract.task.ColumnTypeCast> temporalStringCasts = casts.stream()
                .filter(cast -> cast.temporalStringFormatOptions() != null)
                .toList();
        String temporalStringTargetTimeZones = safePreview(temporalStringCasts.stream()
                .map(cast -> cast.temporalStringFormatOptions().targetTimeZone())
                .filter(zone -> zone != null && !zone.isBlank()).toList());
        String temporalStringPatternMetadata = temporalStringCasts.stream()
                .map(cast -> cast.temporalStringFormatOptions().pattern())
                .map(pattern -> pattern == null ? "UNCONFIGURED" : pattern.length() + ":" + sha256(pattern))
                .collect(java.util.stream.Collectors.joining(","));
        return base
                + " epochTimestampUnits=" + safeLogValue(epochTimestampUnits)
                + " stringTemporalParseCount=" + temporalCasts.size()
                + " stringTemporalTargetTypes=" + safeLogValue(temporalTargetTypes)
                + " stringTemporalZoneModes=" + safeLogValue(temporalZoneModes)
                + " stringTemporalSourceTimeZones=" + safeLogValue(sourceTimeZones)
                + " stringTemporalPatternMetadata=" + safeLogValue(patternMetadata)
                + " temporalStringFormatCount=" + temporalStringCasts.size()
                + " temporalStringTargetTimeZones=" + safeLogValue(temporalStringTargetTimeZones)
                + " temporalStringPatternMetadata=" + safeLogValue(temporalStringPatternMetadata);
    }

    private static String filterOperationsSummary(List<FilterOperation> operations) {
        String base = processorOperationsSummary(operations);
        if (operations == null) return base;
        long sqlCount = operations.stream()
                .filter(java.util.Objects::nonNull)
                .filter(operation -> operation.mode() == FilterConditionMode.SQL_EXPRESSION)
                .count();
        String sqlExpressionLengths = operations.stream()
                .filter(java.util.Objects::nonNull)
                .filter(operation -> operation.mode() == FilterConditionMode.SQL_EXPRESSION)
                .map(operation -> Integer.toString(operation.sqlExpression().length()))
                .collect(java.util.stream.Collectors.joining(","));
        return base
                + " structuredCount=" + (operations.size() - sqlCount)
                + " sqlExpressionCount=" + sqlCount
                + " sqlExpressionLengths=" + safeLogValue(sqlExpressionLengths);
    }

    private static String modelOutputSummary(
            ModelOutputNodeDefinition output,
            MetadataIndex metadataIndex
    ) {
        var writes = output.configuration().writes();
        int mappings = writes.stream().mapToInt(write -> size(write.columnMappings())).sum();
        return "writeCount=" + writes.size()
                + " sources=" + safeLogValue(safePreview(writes.stream()
                .map(cn.superhuang.data.scalpel.contract.task.ModelOutputWrite::sourceTableName).toList()))
                + " targets=" + safeLogValue(safePreview(writes.stream()
                .map(write -> displayModelName(metadataIndex, write.targetModelId())).toList()))
                + " writeModes=" + safeLogValue(writeModeSummary(writes.stream()
                .map(cn.superhuang.data.scalpel.contract.task.ModelOutputWrite::writeMode).toList()))
                + " mappingCount=" + mappings
                + " stages=TARGET_SCHEMA,MATERIALIZE,TRUNCATE_IF_REQUIRED,WRITE";
    }

    private static String jdbcOutputSummary(JdbcOutputNodeDefinition output) {
        var writes = output.configuration().writes();
        int mappings = writes.stream().mapToInt(write -> size(write.columnMappings())).sum();
        return "dataSourceId=" + safeLogValue(output.configuration().dataSourceId())
                + " writeCount=" + writes.size()
                + " sources=" + safeLogValue(safePreview(writes.stream()
                .map(cn.superhuang.data.scalpel.contract.task.JdbcOutputWrite::sourceTableName).toList()))
                + " targets=" + safeLogValue(safePreview(writes.stream()
                .map(cn.superhuang.data.scalpel.contract.task.JdbcOutputWrite::targetTableName).toList()))
                + " writeModes=" + safeLogValue(writeModeSummary(writes.stream()
                .map(cn.superhuang.data.scalpel.contract.task.JdbcOutputWrite::writeMode).toList()))
                + " mappingCount=" + mappings
                + " stages=TARGET_SCHEMA,MATERIALIZE,TRUNCATE_IF_REQUIRED,WRITE";
    }

    private static String kafkaOutputSummary(
            cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition output
    ) {
        var writes = output.configuration().writes();
        int fields = writes.stream().mapToInt(write -> write.legacyMappingMode()
                ? write.valueSchema() == null ? 0 : size(write.valueSchema().columns())
                : size(write.valueColumnNames())).sum();
        String formats = writes.stream()
                .map(write -> write.legacyMappingMode()
                        ? "LEGACY_JSON_MAPPING" : write.valueFormat().name())
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
        List<String> valueColumnNames = writes.stream()
                .flatMap(write -> write.legacyMappingMode()
                        ? write.columnMappings().stream().map(mapping -> mapping.targetColumnName())
                        : write.valueColumnNames().stream())
                .toList();
        return "dataSourceId=" + safeLogValue(output.configuration().dataSourceId())
                + " writeCount=" + writes.size()
                + " sources=" + safeLogValue(safePreview(writes.stream()
                .map(cn.superhuang.data.scalpel.contract.task.KafkaOutputWrite::sourceTableName).toList()))
                + " topics=" + safeLogValue(safePreview(writes.stream()
                .map(cn.superhuang.data.scalpel.contract.task.KafkaOutputWrite::topic).toList()))
                + " valueFormats=" + safeLogValue(formats)
                + " valueFieldCount=" + fields
                + " valueFields=" + safeLogValue(safePreview(valueColumnNames));
    }

    private static String writeModeSummary(List<JdbcWriteMode> modes) {
        if (modes == null) return "";
        Map<JdbcWriteMode, Long> counts = modes.stream()
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(
                        java.util.function.Function.identity(),
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static String modelSummary(String prefix, MetadataIndex.ModelEntry model) {
        if (model == null) return prefix;
        return prefix
                + " modelCode=" + safeLogValue(model.metadata().code())
                + " modelSchemaVersion=" + model.metadata().schemaVersion()
                + " dataSourceId=" + model.metadata().dataSourceId()
                + " physicalTable=" + safeLogValue(model.metadata().physicalTableName());
    }

    private static List<String> jdbcInputTableNames(JdbcInputNodeDefinition input) {
        return input.configuration() == null || input.configuration().tables() == null
                ? List.of()
                : input.configuration().tables().stream()
                .map(JdbcInputTableSelection::tableName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    private static int jdbcInputConfiguredTableCount(JdbcInputNodeDefinition input) {
        return input.configuration() == null || input.configuration().tables() == null
                ? 0
                : (int) input.configuration().tables().stream()
                .filter(java.util.Objects::nonNull)
                .filter(table -> table.readOptions() != null && !table.readOptions().isEmpty())
                .count();
    }

    private static int jdbcInputReadOptionCount(JdbcInputNodeDefinition input) {
        return input.configuration() == null || input.configuration().tables() == null
                ? 0
                : input.configuration().tables().stream()
                .filter(java.util.Objects::nonNull)
                .mapToInt(table -> table.readOptions() == null ? 0 : table.readOptions().size())
                .sum();
    }

    static String jdbcReadOptionRedactionRegex(TaskExecutionManifest manifest) {
        String base = "(?i)secret|password|passwd|token|credential|api[-_.]?key|access[-_.]?key|tdengine.*pass";
        if (manifest == null || manifest.task() == null || manifest.task().definition() == null
                || manifest.task().definition().nodes() == null) {
            return base;
        }
        List<String> names = manifest.task().definition().nodes().stream()
                .filter(JdbcInputNodeDefinition.class::isInstance)
                .map(JdbcInputNodeDefinition.class::cast)
                .filter(input -> input.configuration() != null && input.configuration().tables() != null)
                .flatMap(input -> input.configuration().tables().stream())
                .filter(java.util.Objects::nonNull)
                .filter(table -> table.readOptions() != null)
                .flatMap(table -> table.readOptions().stream())
                .filter(java.util.Objects::nonNull)
                .map(option -> option.name())
                .filter(name -> name != null && !name.isBlank())
                .map(Pattern::quote)
                .distinct()
                .toList();
        return names.isEmpty() ? base : base + "|(?:" + String.join("|", names) + ")";
    }

    private static List<String> jdbcReadOptionValues(CanvasNodeDefinition node) {
        if (!(node instanceof JdbcInputNodeDefinition input)
                || input.configuration() == null || input.configuration().tables() == null) {
            return List.of();
        }
        return input.configuration().tables().stream()
                .filter(java.util.Objects::nonNull)
                .filter(table -> table.readOptions() != null)
                .flatMap(table -> table.readOptions().stream())
                .filter(java.util.Objects::nonNull)
                .map(option -> option.value())
                .filter(value -> value != null && !value.isEmpty())
                .distinct()
                .toList();
    }

    private static List<String> jdbcReadOptionValues(TaskExecutionManifest manifest) {
        if (manifest == null || manifest.task() == null || manifest.task().definition() == null
                || manifest.task().definition().nodes() == null) {
            return List.of();
        }
        return manifest.task().definition().nodes().stream()
                .flatMap(node -> jdbcReadOptionValues(node).stream())
                .distinct()
                .toList();
    }

    private static String jdbcInputTablePreview(JdbcInputNodeDefinition input) {
        List<String> names = jdbcInputTableNames(input);
        String preview = names.stream().limit(5).collect(java.util.stream.Collectors.joining(","));
        return names.size() <= 5 ? preview : preview + ",...";
    }

    private static String displayJdbcInputTables(JdbcInputNodeDefinition input) {
        List<String> names = jdbcInputTableNames(input);
        if (names.isEmpty()) return "未选择物理表";
        String preview = names.stream().limit(2).collect(java.util.stream.Collectors.joining("、"));
        return names.size() <= 2 ? preview : preview + " 等 " + names.size() + " 张表";
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
            case JDBC_INCREMENTAL_INPUT -> "JDBC 增量输入已准备";
            case JDBC_QUERY_INPUT -> "JDBC 查询输入已准备";
            case FILE_DATASET_INPUT -> "文件数据集输入已准备";
            case HTTP_API_INPUT -> "HTTP API 输入已读取";
            case SPATIAL_SERVICE_INPUT -> "空间服务输入已读取";
            case KAFKA_INPUT -> "Kafka 输入已准备";
            case TDENGINE_TMQ_INPUT -> "TDengine TMQ 输入已准备";
            case JOIN -> "Join 已准备";
            case GEOMETRY_CONSTRUCT -> "Geometry 构造已准备";
            case SPATIAL_TRANSFORM -> "空间转换已准备";
            case GEOMETRY_VALIDATE -> "Geometry 校验已准备";
            case GEOMETRY_REPAIR -> "Geometry 修复已准备";
            case GEOMETRY_DERIVE -> "Geometry 派生已准备";
            case GEOMETRY_SIMPLIFY -> "Geometry 简化已准备";
            case SPATIAL_NEAREST -> "空间最近邻已准备";
            case SPATIAL_SUMMARIZE_WITHIN -> "区域内汇总已准备";
            case SPATIAL_OVERLAY -> "空间叠加已准备";
            case TRACK_RECONSTRUCT -> "轨迹重建已准备";
            case TRACK_MOTION_STATISTICS -> "运动统计已准备";
            case TRACK_FIND_DWELL -> "驻留识别已准备";
            case TRACK_DETECT_INCIDENTS -> "轨迹事件识别已准备";
            case SPATIAL_BIN_AGGREGATE -> "空间格网聚合已准备";
            case SPATIAL_POINT_CLUSTER -> "空间点聚类已准备";
            case SPATIAL_CENTER_DISPERSION -> "空间中心与离散统计已准备";
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
            case SQL_TRANSFORM -> "SQL 处理已准备";
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
            case MODEL_OUTPUT, MODEL_SNAPSHOT_SYNC_OUTPUT,
                    JDBC_OUTPUT, JDBC_SNAPSHOT_SYNC_OUTPUT,
                    KAFKA_OUTPUT, FILE_OUTPUT ->
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
        List<cn.superhuang.data.scalpel.contract.task.ColumnDerivation> globalDerivations =
                node.configuration() == null || node.configuration().globalDerivations() == null
                        ? List.of() : node.configuration().globalDerivations();
        List<cn.superhuang.data.scalpel.contract.task.DeriveColumnsOperation> operations =
                node.configuration() == null || node.configuration().operations() == null
                        ? List.of() : node.configuration().operations();
        int localDerivationCount = 0;
        java.util.Set<String> targets = new java.util.TreeSet<>();
        java.util.Set<String> expressionKinds = new java.util.TreeSet<>();
        java.util.Set<String> runtimeValues = new java.util.TreeSet<>();
        java.util.Set<String> functions = new java.util.TreeSet<>();
        for (cn.superhuang.data.scalpel.contract.task.ColumnDerivation derivation
                : globalDerivations) {
            if (derivation == null) continue;
            if (derivation.targetColumnName() != null) targets.add(derivation.targetColumnName());
            collectExpressionSummary(
                    derivation.expression(),
                    expressionKinds,
                    runtimeValues,
                    functions
            );
        }
        for (cn.superhuang.data.scalpel.contract.task.DeriveColumnsOperation operation : operations) {
            if (operation == null || operation.derivations() == null) continue;
            localDerivationCount += operation.derivations().size();
            for (cn.superhuang.data.scalpel.contract.task.ColumnDerivation derivation
                    : operation.derivations()) {
                if (derivation == null) continue;
                if (derivation.targetColumnName() != null) targets.add(derivation.targetColumnName());
                collectExpressionSummary(
                        derivation.expression(),
                        expressionKinds,
                        runtimeValues,
                        functions
                );
            }
        }
        long effectiveDerivationCount = (long) globalDerivations.size() * operations.size()
                + localDerivationCount;
        return new DeriveSummary(
                operations.size(),
                globalDerivations.size(),
                localDerivationCount,
                effectiveDerivationCount,
                targets,
                expressionKinds,
                runtimeValues,
                functions
        );
    }

    private static void collectExpressionSummary(
            CanvasExpression expression,
            java.util.Set<String> kinds,
            java.util.Set<String> runtimeValues,
            java.util.Set<String> functions
    ) {
        if (expression == null) return;
        switch (expression) {
            case ColumnExpression ignored -> kinds.add("COLUMN");
            case LiteralExpression ignored -> kinds.add("LITERAL");
            case RuntimeValueExpression runtime -> {
                kinds.add("RUNTIME_VALUE");
                if (runtime.value() != null) runtimeValues.add(runtime.value().name());
            }
            case BinaryExpression binary -> {
                kinds.add("BINARY");
                collectExpressionSummary(binary.left(), kinds, runtimeValues, functions);
                collectExpressionSummary(binary.right(), kinds, runtimeValues, functions);
            }
            case FunctionExpression function -> {
                kinds.add("FUNCTION");
                functions.add(function.function().name());
                function.arguments().forEach(
                        argument -> collectExpressionSummary(argument, kinds, runtimeValues, functions)
                );
            }
            case CaseWhenExpression caseWhen -> {
                kinds.add("CASE_WHEN");
                caseWhen.branches().forEach(
                        branch -> collectExpressionSummary(branch.result(), kinds, runtimeValues, functions)
                );
                if (caseWhen.elseExpression() != null) {
                    collectExpressionSummary(caseWhen.elseExpression(), kinds, runtimeValues, functions);
                }
            }
        }
    }

    private record DeriveSummary(
            int tableCount,
            int globalDerivationCount,
            int localDerivationCount,
            long effectiveDerivationCount,
            java.util.Set<String> targets,
            java.util.Set<String> expressionKinds,
            java.util.Set<String> runtimeValues,
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

    private static void logOutputWriteStart(
            TaskExecutionManifest manifest,
            PreparedOutput output
    ) {
        LOGGER.info(
                "event=OUTPUT_WRITE_START executionId={} runId={} attempt={} nodeId={} nodeType={} writeId={} sourceTable={} target={}",
                manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                safeLogValue(output.node().id()), output.node().nodeType(), safeLogValue(output.writeId()),
                safeLogValue(output.sourceTableName()), safeLogValue(output.displayTarget()));
    }

    private static void logOutputWriteSuccess(
            TaskExecutionManifest manifest,
            PreparedOutput output,
            Long rowsWritten
    ) {
        LOGGER.info(
                "event=OUTPUT_WRITE_SUCCESS executionId={} runId={} attempt={} nodeId={} nodeType={} writeId={} target={} rowsWritten={}",
                manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                safeLogValue(output.node().id()), output.node().nodeType(), safeLogValue(output.writeId()),
                safeLogValue(output.displayTarget()), rowsWritten);
    }

    private static void logOutputWriteFailure(
            TaskExecutionManifest manifest,
            PreparedOutput output,
            TaskExecutionError error
    ) {
        LOGGER.error(
                "event=OUTPUT_WRITE_FAILED executionId={} runId={} attempt={} nodeId={} nodeType={} writeId={} target={} code={} diagnosticId={}",
                manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                safeLogValue(output.node().id()), output.node().nodeType(), safeLogValue(output.writeId()),
                safeLogValue(output.displayTarget()), error.code(), error.diagnosticId());
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
                RunnerLogSanitizer.jdbcReadOptionSafeStackTrace(
                        throwable, jdbcReadOptionValues(node)));
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
        LOGGER.error(
                "{}\n{}",
                summary,
                RunnerLogSanitizer.jdbcReadOptionSafeStackTrace(
                        throwable, jdbcReadOptionValues(manifest))
        );
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
            String writeId,
            String sourceTableName,
            RuntimeDataSource runtimeDataSource,
            String qualifiedTableName,
            String displayTarget,
            JdbcWriteMode writeMode,
            Dataset<Row> dataset,
            CanvasPreparedOutput jdbcOutput,
            CanvasPreparedFileOutput fileOutput,
            CanvasPreparedSnapshotSyncOutput snapshotSyncOutput,
            Instant startedAt
    ) {
    }
}
