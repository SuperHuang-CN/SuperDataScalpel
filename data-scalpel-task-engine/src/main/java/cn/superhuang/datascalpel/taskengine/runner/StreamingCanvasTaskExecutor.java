package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageEnvelope;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.RunnerControlChannel;
import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.execution.RunnerStreamingProgressEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerStreamingStartedEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerStreamingStoppedEvent;
import cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.StreamingQueryProgress;
import cn.superhuang.data.scalpel.contract.execution.StreamingSourceProgress;
import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperationContext;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperationResult;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperatorRegistry;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperators;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedKafkaOutput;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedOutput;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasCompilation;
import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasGraphPlan;
import cn.superhuang.datascalpel.taskengine.compiler.canvas.CanvasTaskCompiler;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CompilationSeverity;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeKafkaConnection;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.jdbc.incremental.JdbcIncrementalOffset;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.api.java.function.VoidFunction2;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.streaming.DataStreamWriter;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

final class StreamingCanvasTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamingCanvasTaskExecutor.class);
    private static final Duration CONTROL_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration PROGRESS_INTERVAL = Duration.ofSeconds(10);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final String HIDDEN_KEY_COLUMN = "__datascalpel_kafka_key";

    private final ObjectMapper objectMapper;
    private final CanvasTaskCompiler compiler = new CanvasTaskCompiler();
    private final CanvasNodeOperatorRegistry nodeOperators = CanvasNodeOperators.builtInRegistry();

    StreamingCanvasTaskExecutor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void execute(
            TaskExecutionManifest manifest,
            RunnerSparkMode sparkMode,
            TaskExecutionLaunchDescriptor launch,
            RunnerEventPublisher publisher
    ) {
        validateManifest(manifest, launch);
        SparkSession.Builder builder = SedonaSparkSupport.builder()
                .appName("DataScalpel Streaming Task " + manifest.execution().executionId())
                .config("spark.ui.enabled", "false")
                .config("spark.sql.shuffle.partitions", "4")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .config("spark.speculation", "false")
                .config("spark.sql.streaming.stopTimeout", "60000");
        builder.config(
                        "spark.redaction.regex",
                        "(?i)secret|password|passwd|token|credential|api[-_.]?key|access[-_.]?key|tdengine.*pass")
                .config(
                        "spark.sql.redaction.options.regex",
                        "(?i)secret|password|passwd|token|credential|api[-_.]?key|access[-_.]?key|tdengine.*pass");
        if (sparkMode == RunnerSparkMode.LOCAL) builder.master("local[*]");
        SparkSession spark = SedonaSparkSupport.initialize(builder.getOrCreate());
        List<Dataset<Row>> cachedDimensions = new ArrayList<>();
        List<QueryBinding> queries = new ArrayList<>();
        try (RuntimeCanvasNodeDataAccess dataAccess = new RuntimeCanvasNodeDataAccess(
                spark,
                CanvasTaskExecutor.runtimeSources(manifest.runtimeDataSources()),
                null,
                List.of(),
                manifest.execution().executionId().toString(),
                manifest.execution().attempt(),
                manifest.streaming().sourceNodeId(),
                manifest.streaming().sourceSignature(),
                manifest.streaming().initialSourceOffset());
             StreamingStopController stopController = new StreamingStopController(
                     launch.runnerControl(), objectMapper,
                     launch.engineId(), launch.executionId(), launch.runId(),
                     launch.attempt(), manifest.execution().deploymentId())) {
            MetadataIndex metadata = MetadataIndex.create(manifest.metadataSnapshot());
            CanvasCompilation compilation = compiler.compile(
                    manifest.task().definition(),
                    CanvasExecutionMode.STREAMING,
                    metadata,
                    SedonaSparkSupport.childSession(spark),
                    new AtomicBoolean()
            );
            requireValidCompilation(compilation);
            prepareQueries(
                    manifest, spark, metadata, dataAccess,
                    launch.checkpointUriPrefix(), queries, cachedDimensions);
            publish(publisher, new RunnerStreamingStartedEvent(
                    ExecutionMessageEnvelope.CURRENT_VERSION,
                    UUID.randomUUID(),
                    ExecutionMessageType.RUNNER_STREAMING_STARTED,
                    Instant.now(),
                    launch.engineId(),
                    manifest.execution().executionId(),
                    manifest.execution().runId(),
                    manifest.execution().attempt(),
                    manifest.execution().deploymentId(),
                    spark.sparkContext().applicationId()
            ));
            monitor(manifest, stopController, publisher, queries, launch.engineId());
        } finally {
            stopQueries(queries);
            cachedDimensions.forEach(Dataset::unpersist);
            spark.stop();
        }
    }

    private void prepareQueries(
            TaskExecutionManifest manifest,
            SparkSession spark,
            MetadataIndex metadata,
            RuntimeCanvasNodeDataAccess dataAccess,
            String checkpointUriPrefix,
            List<QueryBinding> queries,
            List<Dataset<Row>> cachedDimensions
    ) {
        CanvasGraphPlan plan = CanvasGraphPlan.create(
                manifest.task().definition(), CanvasExecutionMode.STREAMING);
        List<Map<String, SparkCanvasTable>> propagated = new ArrayList<>();
        for (int ignored = 0; ignored < manifest.task().definition().nodes().size(); ignored++) {
            propagated.add(Map.of());
        }
        for (int nodeIndex : plan.topologicalOrder()) {
            CanvasNodeDefinition node = plan.nodeAt(nodeIndex);
            Instant startedAt = Instant.now();
            LOGGER.info(
                    "event=NODE_START executionId={} runId={} attempt={} nodeId={} nodeType={} nodeName={} summary={}",
                    manifest.execution().executionId(), manifest.execution().runId(),
                    manifest.execution().attempt(), node.id(), node.nodeType(), node.name(),
                    CanvasTaskExecutor.nodeSummary(node, metadata));
            Map<String, SparkCanvasTable> inputs = CanvasTaskExecutor.mergeInputs(
                    plan.predecessorsOf(nodeIndex), propagated, node.id());
            CanvasNodeOperationResult operation = nodeOperators.apply(
                    node,
                    inputs,
                    new CanvasNodeOperationContext(
                            spark,
                            metadata,
                            new RunnerCanvasNodeIssueSink(node.id()),
                            dataAccess,
                            CanvasExecutionMode.STREAMING
                    )
            );
            if (node instanceof JdbcInputNodeDefinition || node instanceof JdbcQueryInputNodeDefinition) {
                operation.propagatedTables().values().forEach(table -> {
                    Dataset<Row> dimension = table.dataset().persist(StorageLevel.MEMORY_AND_DISK());
                    dimension.count();
                    cachedDimensions.add(dimension);
                });
            }
            if (node instanceof JdbcOutputNodeDefinition || node instanceof ModelOutputNodeDefinition) {
                if (operation.preparedOutput() == null) {
                    throw new RunnerExecutionException(
                            "OUTPUT_NOT_PREPARED", "JDBC 或模型输出节点未生成流式写入计划", node.id());
                }
                StreamingQuery query = startJdbcQuery(
                        manifest, operation.preparedOutput(), checkpoint(checkpointUriPrefix, node.id()));
                queries.add(new QueryBinding(node.id(), query));
            } else if (node instanceof KafkaOutputNodeDefinition) {
                if (operation.preparedKafkaOutput() == null) {
                    throw new RunnerExecutionException(
                            "OUTPUT_NOT_PREPARED", "Kafka 输出节点未生成流式写入计划", node.id());
                }
                StreamingQuery query = startKafkaQuery(
                        manifest, operation.preparedKafkaOutput(), checkpoint(checkpointUriPrefix, node.id()));
                queries.add(new QueryBinding(node.id(), query));
            } else {
                propagated.set(nodeIndex, operation.propagatedTables());
            }
            LOGGER.info(
                    "event=NODE_SUCCESS executionId={} runId={} attempt={} nodeId={} nodeType={} nodeName={} durationMs={}",
                    manifest.execution().executionId(), manifest.execution().runId(),
                    manifest.execution().attempt(), node.id(), node.nodeType(), node.name(),
                    Math.max(0, Duration.between(startedAt, Instant.now()).toMillis()));
        }
        if (queries.isEmpty()) {
            throw new RunnerExecutionException("STREAMING_OUTPUT_REQUIRED", "实时任务没有可启动的输出", null);
        }
    }

    private StreamingQuery startJdbcQuery(
            TaskExecutionManifest manifest,
            CanvasPreparedOutput output,
            String checkpoint
    ) {
        if (SpatialJdbcRuntimeSupport.requiresSpatialWriter(output)) {
            throw new RunnerExecutionException(
                    "SPATIAL_JDBC_UNSUPPORTED",
                    "第一阶段不支持实时任务写入 Geometry",
                    output.node().id()
            );
        }
        try {
            return output.dataset().writeStream()
                    .queryName(queryName(manifest, output.node().id()))
                    .outputMode("append")
                    .trigger(Trigger.ProcessingTime(
                            manifest.streaming().triggerIntervalSeconds(), TimeUnit.SECONDS))
                    .option("checkpointLocation", checkpoint)
                    .foreachBatch((VoidFunction2<Dataset<Row>, Long>) (batch, batchId) ->
                            writeJdbcBatch(output, batch))
                    .start();
        } catch (TimeoutException exception) {
            throw new RunnerExecutionException(
                    "STREAMING_QUERY_START_TIMEOUT",
                    "JDBC 实时输出启动超时",
                    output.node().id(),
                    exception
            );
        }
    }

    private static void writeJdbcBatch(CanvasPreparedOutput output, Dataset<Row> batch) {
        if (output.writeMode() != JdbcWriteMode.UPSERT) {
            CanvasTaskExecutor.write(output.runtimeDataSource(), output.qualifiedTableName(), batch);
            return;
        }
        Dataset<Row> cached = batch.persist(StorageLevel.MEMORY_AND_DISK());
        try {
            SpatialJdbcRuntimeSupport.validateUpsertKeys(output, cached);
            SpatialJdbcRuntimeSupport.writeUpsert(output, cached);
        } finally {
            cached.unpersist();
        }
    }

    private StreamingQuery startKafkaQuery(
            TaskExecutionManifest manifest,
            CanvasPreparedKafkaOutput output,
            String checkpoint
    ) {
        List<String> valueColumns = new ArrayList<>(List.of(output.dataset().columns()));
        boolean hasKey = valueColumns.remove(HIDDEN_KEY_COLUMN);
        Column[] structColumns = valueColumns.stream()
                .map(name -> functions.col("`" + name.replace("`", "``") + "`"))
                .toArray(Column[]::new);
        Dataset<Row> kafkaRows = output.dataset().select(
                hasKey
                        ? functions.col(HIDDEN_KEY_COLUMN).cast("string").alias("key")
                        : functions.lit(null).cast("string").alias("key"),
                functions.to_json(functions.struct(structColumns)).alias("value")
        );
        RuntimeKafkaConnection connection = output.runtimeDataSource().kafkaConnection();
        DataStreamWriter<Row> writer = kafkaRows.writeStream()
                .queryName(queryName(manifest, output.node().id()))
                .format("kafka")
                .outputMode("append")
                .trigger(Trigger.ProcessingTime(
                        manifest.streaming().triggerIntervalSeconds(), TimeUnit.SECONDS))
                .option("checkpointLocation", checkpoint)
                .option("kafka.bootstrap.servers", connection.bootstrapServers())
                .option("kafka.request.timeout.ms", "60000")
                .option("kafka.delivery.timeout.ms", "120000")
                .option("kafka.max.block.ms", "120000")
                .option("kafka.socket.connection.setup.timeout.ms", "30000")
                .option("kafka.socket.connection.setup.timeout.max.ms", "120000")
                .option("topic", output.topic())
                .option("kafka.security.protocol", connection.securityProtocol().name());
        applyKafkaAuthentication(writer, connection);
        try {
            return writer.start();
        } catch (TimeoutException exception) {
            throw new RunnerExecutionException(
                    "STREAMING_QUERY_START_TIMEOUT",
                    "Kafka 实时输出启动超时",
                    output.node().id(),
                    exception
            );
        }
    }

    private void monitor(
            TaskExecutionManifest manifest,
            StreamingStopController stopController,
            RunnerEventPublisher publisher,
            List<QueryBinding> queries,
            UUID engineId
    ) {
        Instant lastPublished = Instant.EPOCH;
        long lastProgressSignature = Long.MIN_VALUE;
        while (true) {
            StopStreamingExecutionCommand stop = stopController.poll(CONTROL_POLL_INTERVAL);
            if (stop != null) {
                stopQueries(queries);
                publish(publisher, new RunnerStreamingStoppedEvent(
                        ExecutionMessageEnvelope.CURRENT_VERSION,
                        UUID.randomUUID(),
                        ExecutionMessageType.RUNNER_STREAMING_STOPPED,
                        Instant.now(),
                        engineId,
                        manifest.execution().executionId(),
                        manifest.execution().runId(),
                        manifest.execution().attempt(),
                        manifest.execution().deploymentId(),
                        Instant.now(),
                        "实时任务已正常停止"
                ));
                return;
            }
            for (QueryBinding binding : queries) {
                if (binding.query().exception().isDefined()) {
                    Throwable failure = binding.query().exception().get();
                    stopQueries(queries);
                    throw new RunnerExecutionException(
                            "STREAMING_QUERY_FAILED",
                            "实时输出查询失败：" + binding.outputNodeId(),
                            binding.outputNodeId(),
                            failure
                    );
                }
                if (!binding.query().isActive()) {
                    stopQueries(queries);
                    throw new RunnerExecutionException(
                            "STREAMING_QUERY_STOPPED_UNEXPECTEDLY",
                            "实时输出查询意外停止：" + binding.outputNodeId(),
                            binding.outputNodeId()
                    );
                }
            }
            ProgressSnapshot progress = latestProgress(manifest, queries);
            long signature = progress.queries().stream()
                    .mapToLong(value -> 31L * value.outputNodeId().hashCode() + value.batchId())
                    .sum() + (progress.source() == null ? 0 : progress.source().committedOffset().hashCode());
            Instant now = Instant.now();
            boolean changed = signature != lastProgressSignature;
            if ((changed && !lastPublished.plus(PROGRESS_INTERVAL).isAfter(now))
                    || !lastPublished.plus(HEARTBEAT_INTERVAL).isAfter(now)) {
                publish(publisher, new RunnerStreamingProgressEvent(
                        ExecutionMessageEnvelope.CURRENT_VERSION,
                        UUID.randomUUID(),
                        ExecutionMessageType.RUNNER_STREAMING_PROGRESS,
                        now,
                        engineId,
                        manifest.execution().executionId(),
                        manifest.execution().runId(),
                        manifest.execution().attempt(),
                        manifest.execution().deploymentId(),
                        progress.queries(),
                        progress.source()
                ));
                lastPublished = now;
                lastProgressSignature = signature;
            }
        }
    }

    private static ProgressSnapshot latestProgress(
            TaskExecutionManifest manifest,
            List<QueryBinding> queries
    ) {
        List<StreamingQueryProgress> result = new ArrayList<>();
        StreamingSourceProgress sourceProgress = null;
        for (QueryBinding binding : queries) {
            org.apache.spark.sql.streaming.StreamingQueryProgress progress = binding.query().lastProgress();
            if (progress == null) {
                result.add(new StreamingQueryProgress(
                        binding.outputNodeId(), -1, 0, 0, 0, 0, Instant.now()));
                continue;
            }
            result.add(new StreamingQueryProgress(
                    binding.outputNodeId(),
                    progress.batchId(),
                    progress.numInputRows(),
                    Math.max(0D, progress.inputRowsPerSecond()),
                    Math.max(0D, progress.processedRowsPerSecond()),
                    Math.max(0L, progress.batchDuration()),
                    parseTimestamp(progress.timestamp())
            ));
            if (sourceProgress == null) {
                sourceProgress = jdbcIncrementalSourceProgress(manifest, progress);
            }
        }
        return new ProgressSnapshot(List.copyOf(result), sourceProgress);
    }

    private static StreamingSourceProgress jdbcIncrementalSourceProgress(
            TaskExecutionManifest manifest,
            org.apache.spark.sql.streaming.StreamingQueryProgress progress
    ) {
        if (manifest.streaming().sourceNodeId() == null
                || manifest.streaming().sourceSignature() == null) return null;
        Instant pollTime = parseTimestamp(progress.timestamp());
        for (org.apache.spark.sql.streaming.SourceProgress source : progress.sources()) {
            if (source.endOffset() == null || source.endOffset().isBlank()) continue;
            try {
                JdbcIncrementalOffset end = JdbcIncrementalOffset.parse(source.endOffset());
                if (!manifest.streaming().sourceSignature().equals(end.sourceSignature())
                        || end.lowerUnbounded() || end.endTime() == null) continue;
                Instant windowStart = null;
                if (source.startOffset() != null && !source.startOffset().isBlank()) {
                    JdbcIncrementalOffset start = JdbcIncrementalOffset.parse(source.startOffset());
                    if (!start.lowerUnbounded()) windowStart = start.endTime();
                }
                return new StreamingSourceProgress(
                        manifest.streaming().sourceNodeId(),
                        end.sourceSignature(),
                        end.json(),
                        windowStart,
                        end.endTime(),
                        source.numInputRows(),
                        Math.max(0L, progress.batchDuration()),
                        pollTime,
                        Math.max(0L, Duration.between(end.endTime(), pollTime).toMillis())
                );
            } catch (RuntimeException ignored) {
                // Only the sanitized, versioned JDBC incremental offset is allowed to cross this boundary.
            }
        }
        return null;
    }

    private static Instant parseTimestamp(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (DateTimeParseException exception) {
            return Instant.now();
        }
    }

    private static void stopQueries(List<QueryBinding> queries) {
        for (QueryBinding binding : queries) {
            if (!binding.query().isActive()) continue;
            try {
                binding.query().stop();
            } catch (TimeoutException exception) {
                throw new RunnerExecutionException(
                        "STREAMING_STOP_TIMEOUT",
                        "实时输出查询停止超时：" + binding.outputNodeId(),
                        binding.outputNodeId(),
                        exception
                );
            }
        }
    }

    private static void requireValidCompilation(CanvasCompilation compilation) {
        if (compilation.valid()) return;
        String message = compilation.canvasIssues().stream()
                .filter(issue -> issue.severity() == CompilationSeverity.ERROR)
                .map(issue -> issue.code() + ": " + issue.message())
                .findFirst()
                .orElseGet(() -> compilation.nodeResults().stream()
                        .flatMap(result -> result.issues().stream())
                        .filter(issue -> issue.severity() == CompilationSeverity.ERROR)
                        .map(issue -> issue.code() + ": " + issue.message())
                        .findFirst()
                        .orElse("实时 Canvas 编译失败"));
        throw new RunnerExecutionException("CANVAS_COMPILATION_FAILED", message, null);
    }

    private static void validateManifest(
            TaskExecutionManifest manifest,
            TaskExecutionLaunchDescriptor launch
    ) {
        ManifestVersionSupport.requireSupported(manifest);
        if (manifest.execution() == null
                || manifest.execution().executionId() == null
                || manifest.execution().runId() == null
                || manifest.execution().taskId() == null
                || manifest.execution().attempt() == null
                || manifest.execution().attempt() < 1
                || manifest.execution().definitionVersion() == null
                || manifest.execution().definitionVersion() < 1
                || manifest.execution().createdAt() == null
                || manifest.execution().deadlineAt() != null
                || manifest.execution().deploymentId() == null
                || manifest.task() == null
                || manifest.executionTaskType() != cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType.SPARK_STREAMING_CANVAS
                || manifest.modelQuality() != null
                || manifest.task().executionMode() != CanvasExecutionMode.STREAMING
                || manifest.task().definition() == null
                || manifest.metadataSnapshot() == null
                || manifest.runtimeDataSources() == null
                || manifest.streaming() == null
                || manifest.streaming().triggerIntervalSeconds() < 1
                || manifest.streaming().triggerIntervalSeconds() > 300
                || (manifest.streaming().sourceNodeId() == null)
                        != (manifest.streaming().sourceSignature() == null)
                || manifest.streaming().initialSourceOffset() != null
                        && manifest.streaming().sourceSignature() == null
                || launch == null
                || launch.deadlineAt() != null
                || launch.checkpointUriPrefix() == null
                || launch.checkpointUriPrefix().isBlank()
                || launch.runnerControl() == null) {
            throw new RunnerExecutionException("INVALID_STREAMING_MANIFEST", "实时任务 Manifest 不完整", null);
        }
    }

    private static String checkpoint(String prefix, String outputNodeId) {
        String normalized = prefix.endsWith("/")
                ? prefix.substring(0, prefix.length() - 1)
                : prefix;
        return normalized + "/outputs/" + UUID.fromString(outputNodeId);
    }

    private static String queryName(TaskExecutionManifest manifest, String nodeId) {
        return "datascalpel-" + manifest.execution().deploymentId() + "-" + nodeId;
    }

    private static void publish(
            RunnerEventPublisher publisher,
            cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent event
    ) {
        try {
            publisher.publish(event);
        } catch (Exception exception) {
            throw new RunnerExecutionException(
                    "STREAMING_EVENT_DELIVERY_FAILED", "实时 Runner 事件发送失败", null, exception);
        }
    }

    private static void applyKafkaAuthentication(
            DataStreamWriter<Row> writer,
            RuntimeKafkaConnection connection
    ) {
        if (connection.saslMechanism() == null) return;
        String mechanism = switch (connection.saslMechanism()) {
            case PLAIN -> "PLAIN";
            case SCRAM_SHA_256 -> "SCRAM-SHA-256";
            case SCRAM_SHA_512 -> "SCRAM-SHA-512";
        };
        String loginModule =
                connection.saslMechanism()
                        == cn.superhuang.datascalpel.taskengine.contract.KafkaSaslMechanism.PLAIN
                        ? "org.apache.kafka.common.security.plain.PlainLoginModule"
                        : "org.apache.kafka.common.security.scram.ScramLoginModule";
        writer.option("kafka.sasl.mechanism", mechanism)
                .option("kafka.sasl.jaas.config", loginModule + " required username=\""
                        + jaas(connection.username()) + "\" password=\""
                        + jaas(connection.password()) + "\";");
    }

    private static String jaas(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record QueryBinding(String outputNodeId, StreamingQuery query) {
    }

    private record ProgressSnapshot(
            List<StreamingQueryProgress> queries,
            StreamingSourceProgress source
    ) {
    }
}
