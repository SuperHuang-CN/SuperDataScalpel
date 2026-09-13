package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeDataAccess;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedOutput;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedSnapshotSyncOutput;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedKafkaOutput;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedFileOutput;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputResourceSelection;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceInputResourceSelection;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceResourceDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputTableSelection;
import cn.superhuang.data.scalpel.contract.task.JdbcIncrementalInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcIncrementalSourceSignature;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcSnapshotSyncOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputMetadataField;
import cn.superhuang.data.scalpel.contract.task.KafkaInputValueFormat;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputTableSelection;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeFileInput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeFileStorage;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeKafkaConnection;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeS3Connection;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeTdEngineTmqConnection;
import cn.superhuang.datascalpel.taskengine.tdengine.tmq.TdEngineTmqTableProvider;
import cn.superhuang.datascalpel.taskengine.jdbc.incremental.JdbcIncrementalTableProvider;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelInputSelection;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelSnapshotSyncOutputNodeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlyQueryFingerprint;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.httpapi.HttpApiPullConnectorRegistry;
import cn.superhuang.datascalpel.taskengine.httpapi.HttpApiPullException;
import cn.superhuang.datascalpel.taskengine.spark.HttpApiBatchDatasetStager;
import cn.superhuang.datascalpel.taskengine.spark.HttpApiBatchStagingException;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.streaming.DataStreamReader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RuntimeCanvasNodeDataAccess implements CanvasNodeDataAccess {
    private final SparkSession spark;
    private final Map<UUID, RuntimeDataSource> runtimeSources;
    private final RuntimeFileStorage runtimeFileStorage;
    private final Map<UUID, RuntimeFileInput> runtimeFileInputs;
    private final String executionId;
    private final String taskId;
    private final int attempt;
    private final String streamingSourceNodeId;
    private final String streamingSourceSignature;
    private final String initialSourceOffset;
    private final FileDatasetBatchReaderRegistry fileReaders = new FileDatasetBatchReaderRegistry();
    private final List<HttpApiBatchDatasetStager> httpApiStagers = new ArrayList<>();
    private final SpatialServiceFeatureReader spatialServiceFeatureReader = new SpatialServiceFeatureReader();

    RuntimeCanvasNodeDataAccess(
            SparkSession spark,
            Map<UUID, RuntimeDataSource> runtimeSources
    ) {
        this(spark, runtimeSources, null, List.of(), null, null, 0, null, null, null);
    }

    RuntimeCanvasNodeDataAccess(
            SparkSession spark,
            Map<UUID, RuntimeDataSource> runtimeSources,
            RuntimeFileStorage runtimeFileStorage,
            List<RuntimeFileInput> runtimeFileInputs
    ) {
        this(spark, runtimeSources, runtimeFileStorage, runtimeFileInputs, null, null, 0,
                null, null, null);
    }

    RuntimeCanvasNodeDataAccess(
            SparkSession spark,
            Map<UUID, RuntimeDataSource> runtimeSources,
            RuntimeFileStorage runtimeFileStorage,
            List<RuntimeFileInput> runtimeFileInputs,
            String executionId,
            String taskId,
            int attempt,
            String streamingSourceNodeId,
            String streamingSourceSignature,
            String initialSourceOffset
    ) {
        this.spark = spark;
        this.runtimeSources = runtimeSources;
        this.runtimeFileStorage = runtimeFileStorage;
        this.executionId = executionId;
        this.taskId = taskId;
        this.attempt = attempt;
        this.streamingSourceNodeId = streamingSourceNodeId;
        this.streamingSourceSignature = streamingSourceSignature;
        this.initialSourceOffset = initialSourceOffset;
        this.runtimeFileInputs = runtimeFileInputs.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                RuntimeFileInput::fileDatasetTableId,
                java.util.function.Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException(
                            "Duplicate runtime file input: " + left.fileDatasetTableId());
                }
        ));
    }

    @Override
    public Dataset<Row> readJdbcInput(
            JdbcInputNodeDefinition node,
            JdbcInputTableSelection table,
            CanvasTableSchema logicalSchema
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "输入数据源 ID 无效", node.id());
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, dataSourceId, DataSourcePurpose.SOURCE, node.id());
        if (runtime.connectionKind() != ConnectionKind.JDBC) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "输入节点需要 JDBC 数据源", node.id());
        }
        return SpatialJdbcRuntimeSupport.readTable(
                spark,
                runtime,
                SpatialJdbcRuntimeSupport.tableIdentifier(runtime, table.tableName()),
                logicalSchema,
                table.readOptions(),
                node.id()
        );
    }

    @Override
    public Dataset<Row> readJdbcIncrementalInput(
            JdbcIncrementalInputNodeDefinition node,
            CanvasTableSchema logicalSchema
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "增量输入数据源 ID 无效", node.id());
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, dataSourceId, DataSourcePurpose.SOURCE, node.id());
        if (runtime.connectionKind() != ConnectionKind.JDBC
                || runtime.databaseType() == null || runtime.connection() == null) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "增量输入节点需要 JDBC 数据源", node.id());
        }
        var cursor = logicalSchema.columns().stream()
                .filter(column -> column.name().equals(node.configuration().incrementalTimeColumn()))
                .findFirst().orElseThrow(() -> new RunnerExecutionException(
                        "JDBC_INCREMENTAL_SCHEMA_CHANGED", "增量时间字段不存在", node.id()));
        var connection = runtime.connection();
        String sourceSignature = JdbcIncrementalSourceSignature.sha256(
                node, connection.catalogName(), connection.schemaName(), logicalSchema.columns());
        if (streamingSourceSignature != null
                && (!node.id().equals(streamingSourceNodeId)
                || !sourceSignature.equals(streamingSourceSignature))) {
            throw new RunnerExecutionException(
                    "JDBC_INCREMENTAL_SOURCE_CHANGED",
                    "运行 Manifest 的 JDBC 增量来源签名与当前定义不一致",
                    node.id());
        }
        var reader = spark.readStream()
                .format(JdbcIncrementalTableProvider.class.getName())
                .schema(SparkTypeMapper.toStructType(logicalSchema.columns()))
                .option("dataSourceId", dataSourceId.toString())
                .option("taskId", taskId == null ? "unknown" : taskId)
                .option("nodeId", node.id())
                .option("sourceSignature", sourceSignature)
                .option("databaseType", runtime.databaseType().name())
                .option("driverClassName", connection.driverClassName())
                .option("jdbcUrl", connection.jdbcUrl())
                .option("username", connection.username())
                .option("tableName", node.configuration().tableName())
                .option("incrementalTimeColumn", node.configuration().incrementalTimeColumn())
                .option("temporalType", cursor.fieldType().name())
                .option("cursorTimeZone", node.configuration().cursorTimeZone())
                .option("visibilityDelaySeconds", node.configuration().visibilityDelaySeconds())
                .option("startPosition", node.configuration().startPosition().name());
        if (connection.password() != null) reader = reader.option("password", connection.password());
        if (connection.catalogName() != null) reader = reader.option("catalogName", connection.catalogName());
        if (connection.schemaName() != null) reader = reader.option("schemaName", connection.schemaName());
        if (node.configuration().startTime() != null) {
            reader = reader.option("startTime", node.configuration().startTime().toString());
        }
        if (initialSourceOffset != null && node.id().equals(streamingSourceNodeId)) {
            reader = reader.option("resumeOffset", initialSourceOffset);
        }
        for (Map.Entry<String, String> property : connection.properties().entrySet()) {
            reader = reader.option("jdbcProperty." + property.getKey(), property.getValue());
        }
        return reader.load();
    }

    @Override
    public Dataset<Row> readJdbcQueryInput(
            JdbcQueryInputNodeDefinition node,
            CanvasTableSchema logicalSchema
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "查询输入数据源 ID 无效", node.id());
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, dataSourceId, DataSourcePurpose.SOURCE, node.id());
        if (runtime.connectionKind() != ConnectionKind.JDBC || runtime.databaseType() == null) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "查询输入节点需要 JDBC 数据源", node.id());
        }
        InsertSelectQuery query;
        try {
            query = ReadOnlySelectQueryParser.parse(node.configuration().sql());
        } catch (IllegalArgumentException exception) {
            throw new RunnerExecutionException(
                    "JDBC_QUERY_NOT_READ_ONLY", "SQL 必须是单条只读查询", node.id());
        }
        if (!ReadOnlyQueryFingerprint.sha256(query).equals(node.configuration().analyzedSqlSha256())) {
            throw new RunnerExecutionException(
                    "JDBC_QUERY_SCHEMA_STALE", "SQL 已修改，请重新分析 SQL", node.id());
        }
        try {
            var reader = CanvasTaskExecutor.reader(spark, runtime)
                    .option("query", query.sql());
            String sessionInitialization = SpatialJdbcRuntimeSupport.dialect(runtime)
                    .readOnlySessionInitializationSql();
            if (sessionInitialization != null && !sessionInitialization.isBlank()) {
                reader = reader.option("sessionInitStatement", sessionInitialization);
            }
            return reader.load();
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // SQL text and literals are intentionally omitted from the runtime failure chain.
            throw new RunnerExecutionException(
                    "JDBC_QUERY_INPUT_FAILED", "JDBC 查询输入读取失败", node.id());
        }
    }

    @Override
    public Dataset<Row> readFileDatasetInput(
            FileDatasetInputNodeDefinition node,
            FileDatasetInputTableSelection selection,
            MetadataIndex.FileDatasetTableEntry table,
            CanvasTableSchema logicalSchema
    ) {
        UUID tableId = CanvasTaskExecutor.uuid(
                selection.fileDatasetTableId(),
                "文件数据集表 ID 无效",
                node.id()
        );
        RuntimeFileInput input = runtimeFileInputs.get(tableId);
        if (runtimeFileStorage == null || input == null) {
            throw new RunnerExecutionException(
                    "FILE_DATASET_INPUT_FAILED",
                    "运行 Manifest 中缺少文件数据集输入",
                    node.id()
            );
        }
        return fileReaders.read(spark, runtimeFileStorage, input, logicalSchema, node.id(), node.name());
    }

    @Override
    public Dataset<Row> readHttpApiInput(
            HttpApiInputNodeDefinition node,
            HttpApiInputResourceSelection selection,
            CanvasTableSchema logicalSchema
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "API 数据源 ID 无效", node.id());
        UUID resourceId = CanvasTaskExecutor.uuid(
                selection.resourceId(), "API 资源 ID 无效", node.id());
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, dataSourceId, DataSourcePurpose.SOURCE, node.id());
        if (runtime.connectionKind() != ConnectionKind.HTTP_API || runtime.httpApiConnection() == null) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "输入节点需要 HTTP API 数据源", node.id());
        }
        HttpApiContracts.ResourceDefinition resource = runtime.apiResources().stream()
                .filter(candidate -> resourceId.equals(candidate.id()))
                .findFirst()
                .orElseThrow(() -> new RunnerExecutionException(
                        "API_RESOURCE_NOT_FOUND", "运行 Manifest 中不存在 API 资源", node.id()));
        if (!resource.enabled()) {
            throw new RunnerExecutionException("API_RESOURCE_DISABLED", "API 资源已停用", node.id());
        }
        HttpApiBatchDatasetStager stager = new HttpApiBatchDatasetStager(
                spark,
                SparkTypeMapper.toStructType(logicalSchema.columns())
        );
        httpApiStagers.add(stager);
        try {
            HttpApiPullConnectorRegistry.require(resource.connectorType()).pullBatches(
                    new HttpApiContracts.PullRequest(
                            runtime.httpApiConnection(),
                            resource,
                            selection.runtimeParameters()
                    ),
                    stager
            );
            return stager.dataset();
        } catch (HttpApiPullException exception) {
            throw new RunnerExecutionException(exception.code(), exception.getMessage(), node.id(), exception);
        } catch (HttpApiBatchStagingException exception) {
            throw new RunnerExecutionException(
                    "API_BATCH_STAGING_FAILED", "HTTP API 分批暂存失败", node.id(), exception);
        }
    }

    @Override
    public Dataset<Row> readSpatialServiceInput(
            SpatialServiceInputNodeDefinition node,
            SpatialServiceInputResourceSelection selection,
            CanvasTableSchema logicalSchema
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "空间服务数据源 ID 无效", node.id());
        UUID resourceId = CanvasTaskExecutor.uuid(
                selection.resourceId(), "空间要素资源 ID 无效", node.id());
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, dataSourceId, DataSourcePurpose.SOURCE, node.id());
        if (runtime.connectionKind() != ConnectionKind.HTTP_API || runtime.httpApiConnection() == null) {
            throw new RunnerExecutionException("RUNTIME_DATA_SOURCE_UNAVAILABLE", "输入节点需要空间服务数据源", node.id());
        }
        SpatialServiceResourceDefinition resource = runtime.spatialResources().stream()
                .filter(candidate -> resourceId.equals(candidate.id()))
                .findFirst().orElseThrow(() -> new RunnerExecutionException(
                        "SPATIAL_RESOURCE_NOT_FOUND", "运行 Manifest 中不存在空间要素资源", node.id()));
        if (!resource.enabled()) {
            throw new RunnerExecutionException("SPATIAL_RESOURCE_DISABLED", "空间要素资源已停用", node.id());
        }
        return spatialServiceFeatureReader.read(spark, runtime.httpApiConnection(), resource, logicalSchema, node.id());
    }

    @Override
    public Dataset<Row> readKafkaInput(
            KafkaInputNodeDefinition node,
            CanvasTableSchema logicalSchema
    ) {
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources,
                CanvasTaskExecutor.uuid(
                        node.configuration().dataSourceId(),
                        "Kafka 输入数据源 ID 无效",
                        node.id()
                ),
                DataSourcePurpose.SOURCE,
                node.id()
        );
        RuntimeKafkaConnection connection = runtime.kafkaConnection();
        if (runtime.connectionKind() != ConnectionKind.KAFKA || connection == null) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "输入节点需要 Kafka 数据源", node.id());
        }
        DataStreamReader reader = spark.readStream().format("kafka")
                .option("kafka.bootstrap.servers", connection.bootstrapServers())
                .option("kafka.request.timeout.ms", "60000")
                .option("kafka.default.api.timeout.ms", "120000")
                .option("kafka.socket.connection.setup.timeout.ms", "30000")
                .option("kafka.socket.connection.setup.timeout.max.ms", "120000")
                .option("subscribe", node.configuration().topic())
                .option("startingOffsets", node.configuration().startingOffsets().name().toLowerCase())
                .option("failOnDataLoss", "true")
                .option("kafka.security.protocol", connection.securityProtocol().name());
        applyKafkaAuthentication(reader, connection);
        Dataset<Row> raw = reader.load();
        return projectKafkaInput(raw, node, logicalSchema);
    }

    static Dataset<Row> projectKafkaInput(
            Dataset<Row> raw,
            KafkaInputNodeDefinition node,
            CanvasTableSchema logicalSchema
    ) {
        KafkaInputValueFormat valueFormat = node.configuration().effectiveValueFormat();
        List<Column> projections = new ArrayList<>();
        Dataset<Row> decoded = raw;
        if (valueFormat == KafkaInputValueFormat.JSON) {
            String decodedColumnName = "__data_scalpel_kafka_value";
            int valueColumnCount = node.configuration().valueSchema().columns().size();
            decoded = raw.withColumn(decodedColumnName, functions.from_json(
                    functions.col("value").cast("string"),
                    SparkTypeMapper.toStructType(logicalSchema.columns().subList(0, valueColumnCount)),
                    Map.of("mode", "FAILFAST")
            ));
            node.configuration().valueSchema().columns().forEach(column -> projections.add(
                    functions.col(decodedColumnName).getField(column.name()).alias(column.name())
            ));
        } else if (valueFormat == KafkaInputValueFormat.TEXT) {
            projections.add(functions.col("value").cast("string").alias("value"));
        } else {
            projections.add(functions.col("value").alias("value"));
        }

        LinkedHashSet<KafkaInputMetadataField> metadataFields = new LinkedHashSet<>(
                node.configuration().effectiveMetadataFields()
        );
        for (KafkaInputMetadataField field : KafkaInputMetadataField.values()) {
            if (!metadataFields.contains(field)) continue;
            projections.add(switch (field) {
                case KEY -> functions.col("key").alias("_kafka_key");
                case TOPIC -> functions.col("topic").alias("_kafka_topic");
                case PARTITION -> functions.col("partition").alias("_kafka_partition");
                case OFFSET -> functions.col("offset").alias("_kafka_offset");
                case TIMESTAMP -> functions.col("timestamp").alias("_kafka_timestamp");
            });
        }
        return decoded.select(projections.toArray(Column[]::new));
    }

    @Override
    public Dataset<Row> readTdEngineTmqInput(
            TdEngineTmqInputNodeDefinition node,
            CanvasTableSchema logicalSchema
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "TMQ 输入数据源 ID 无效", node.id());
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, dataSourceId, DataSourcePurpose.SOURCE, node.id());
        RuntimeTdEngineTmqConnection connection = runtime.tdEngineTmqConnection();
        if (runtime.connectionKind() != ConnectionKind.JDBC || connection == null) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE",
                    "TMQ 输入需要 TDengine WebSocket 运行连接",
                    node.id()
            );
        }
        DataStreamReader reader = spark.readStream()
                .format(TdEngineTmqTableProvider.class.getName())
                .schema(SparkTypeMapper.toStructType(logicalSchema.columns()))
                .option("dataSourceId", dataSourceId.toString())
                .option("taskId", taskId == null ? "unknown" : taskId)
                .option("nodeId", node.id())
                .option("executionId", executionId == null ? "unknown" : executionId)
                .option("attempt", attempt)
                .option("bootstrapServers", connection.bootstrapServers())
                .option("username", connection.username())
                .option("password", connection.password())
                .option("useSsl", connection.useSsl())
                .option("topic", node.configuration().topicName())
                .option("startingOffsets", node.configuration().startingOffsets().name().toLowerCase())
                .option(
                        "maxOffsetsPerVGroupPerTrigger",
                        node.configuration().maxOffsetsPerVGroupPerTrigger()
                );
        if (node.id().equals(streamingSourceNodeId)
                && initialSourceOffset != null && !initialSourceOffset.isBlank()) {
            reader.option("initialSourceOffset", initialSourceOffset);
        }
        return reader.load();
    }

    @Override
    public Dataset<Row> readModelInput(
            ModelInputNodeDefinition node,
            ModelInputSelection selection,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema logicalSchema
    ) {
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSourceForModelRead(
                runtimeSources, model.metadata().dataSourceId(), node.id());
        return SpatialJdbcRuntimeSupport.readTable(
                spark,
                runtime,
                new TableIdentifier(
                        model.metadata().catalogName(),
                        model.metadata().schemaName(),
                        model.metadata().physicalTableName()
                ),
                logicalSchema,
                List.of(),
                node.id()
        );
    }

    @Override
    public CanvasPreparedOutput prepareJdbcOutput(
            JdbcOutputNodeDefinition node,
            cn.superhuang.data.scalpel.contract.task.JdbcOutputWrite write,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "输出数据源 ID 无效", node.id());
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, dataSourceId, DataSourcePurpose.DISTRIBUTION, node.id());
        if (runtime.connectionKind() != ConnectionKind.JDBC) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "输出节点需要 JDBC 数据源", node.id());
        }
        TableIdentifier targetTable =
                SpatialJdbcRuntimeSupport.tableIdentifier(runtime, write.targetTableName());
        String qualifiedTableName = SpatialJdbcRuntimeSupport.qualifiedTable(runtime, targetTable);
        Map<String, Integer> geometryLocalSrids = SpatialJdbcRuntimeSupport.resolveGeometryLocalSrids(
                runtime, targetTable, targetSchema, dataset.columns(), node.id());
        return new CanvasPreparedOutput(
                node,
                write.writeId(),
                write.sourceTableName(),
                runtime,
                targetTable,
                qualifiedTableName,
                write.targetTableName(),
                write.writeMode(),
                dataset,
                targetSchema,
                geometryLocalSrids,
                write.upsertKeyColumns()
        );
    }

    @Override
    public CanvasPreparedOutput prepareModelOutput(
            ModelOutputNodeDefinition node,
            cn.superhuang.data.scalpel.contract.task.ModelOutputWrite write,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset,
            List<String> upsertKeyColumns
    ) {
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources,
                model.metadata().dataSourceId(),
                DataSourcePurpose.STORAGE,
                node.id()
        );
        if (runtime.connectionKind() != ConnectionKind.JDBC) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "模型输出节点需要 JDBC 数据源", node.id());
        }
        TableIdentifier targetTable = new TableIdentifier(
                model.metadata().catalogName(),
                model.metadata().schemaName(),
                model.metadata().physicalTableName()
        );
        String qualifiedTableName = SpatialJdbcRuntimeSupport.qualifiedTable(runtime, targetTable);
        Map<String, Integer> geometryLocalSrids = SpatialJdbcRuntimeSupport.resolveGeometryLocalSrids(
                runtime, targetTable, targetSchema, dataset.columns(), node.id());
        return new CanvasPreparedOutput(
                node,
                write.writeId(),
                write.sourceTableName(),
                runtime,
                targetTable,
                qualifiedTableName,
                model.metadata().name() + " · " + model.metadata().code(),
                write.writeMode(),
                dataset,
                targetSchema,
                geometryLocalSrids,
                upsertKeyColumns
        );
    }

    @Override
    public CanvasPreparedSnapshotSyncOutput prepareJdbcSnapshotSyncOutput(
            JdbcSnapshotSyncOutputNodeDefinition node,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "快照同步数据源 ID 无效", node.id());
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, dataSourceId, DataSourcePurpose.DISTRIBUTION, node.id());
        requireSnapshotSyncDatabase(runtime, node.id());
        TableIdentifier targetTable = SpatialJdbcRuntimeSupport.tableIdentifier(
                runtime, node.configuration().targetTableName());
        Map<String, Integer> localSrids = SpatialJdbcRuntimeSupport.resolveGeometryLocalSrids(
                runtime, targetTable, targetSchema, dataset.columns(), node.id());
        return new CanvasPreparedSnapshotSyncOutput(
                node,
                runtime,
                targetTable,
                CanvasTaskExecutor.displayTable(runtime, node.configuration().targetTableName()),
                dataset,
                targetSchema,
                node.configuration().keyColumns(),
                node.configuration().deletePolicy(),
                localSrids
        );
    }

    @Override
    public CanvasPreparedSnapshotSyncOutput prepareModelSnapshotSyncOutput(
            ModelSnapshotSyncOutputNodeDefinition node,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
    ) {
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, model.metadata().dataSourceId(), DataSourcePurpose.STORAGE, node.id());
        requireSnapshotSyncDatabase(runtime, node.id());
        TableIdentifier targetTable = new TableIdentifier(
                model.metadata().catalogName(),
                model.metadata().schemaName(),
                model.metadata().physicalTableName()
        );
        Map<String, Integer> localSrids = SpatialJdbcRuntimeSupport.resolveGeometryLocalSrids(
                runtime, targetTable, targetSchema, dataset.columns(), node.id());
        return new CanvasPreparedSnapshotSyncOutput(
                node,
                runtime,
                targetTable,
                CanvasTaskExecutor.displayModelTable(runtime, model),
                dataset,
                targetSchema,
                node.configuration().keyColumns(),
                node.configuration().deletePolicy(),
                localSrids
        );
    }

    private static void requireSnapshotSyncDatabase(RuntimeDataSource runtime, String nodeId) {
        if (runtime.connectionKind() != ConnectionKind.JDBC
                || runtime.connection() == null
                || runtime.databaseType() != cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType.POSTGRESQL
                && runtime.databaseType() != cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType.HIGHGO
                && runtime.databaseType() != cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType.MYSQL
                && runtime.databaseType() != cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType.OPENGAUSS
                && runtime.databaseType() != cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType.KINGBASE) {
            throw new RunnerExecutionException(
                    "SNAPSHOT_SYNC_DATABASE_NOT_SUPPORTED",
                    "快照同步只支持 PostgreSQL、HighGo、MySQL、openGauss 和人大金仓 JDBC 数据源",
                    nodeId
            );
        }
    }

    @Override
    public CanvasPreparedKafkaOutput prepareKafkaOutput(
            KafkaOutputNodeDefinition node,
            cn.superhuang.data.scalpel.contract.task.KafkaOutputWrite write,
            String keyColumnAlias,
            Dataset<Row> dataset
    ) {
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources,
                CanvasTaskExecutor.uuid(
                        node.configuration().dataSourceId(),
                        "Kafka 输出数据源 ID 无效",
                        node.id()
                ),
                DataSourcePurpose.DISTRIBUTION,
                node.id()
        );
        if (runtime.connectionKind() != ConnectionKind.KAFKA || runtime.kafkaConnection() == null) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "输出节点需要 Kafka 数据源", node.id());
        }
        return new CanvasPreparedKafkaOutput(
                node,
                write.writeId(),
                runtime,
                write.topic(),
                write.valueFormat(),
                keyColumnAlias,
                dataset
        );
    }

    @Override
    public CanvasPreparedFileOutput prepareFileOutput(
            FileOutputNodeDefinition node,
            cn.superhuang.data.scalpel.contract.task.FileOutputWrite write,
            CanvasTableSchema sourceSchema,
            Dataset<Row> dataset
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "文件输出数据源 ID 无效", node.id());
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, dataSourceId, DataSourcePurpose.DISTRIBUTION, node.id());
        RuntimeS3Connection connection = runtime.s3Connection();
        if (runtime.connectionKind() != ConnectionKind.S3 || connection == null) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "文件输出节点需要 S3 数据源", node.id());
        }
        String targetKey = joinKey(connection.rootPrefix(), write.targetPath());
        return new CanvasPreparedFileOutput(
                node,
                write.writeId(),
                write.sourceTableName(),
                runtime,
                write.targetPath(),
                "s3a://" + connection.bucket() + "/" + targetKey,
                write.conflictPolicy(),
                write.formatOptions(),
                sourceSchema,
                dataset
        );
    }

    @Override
    public void close() {
        httpApiStagers.forEach(HttpApiBatchDatasetStager::close);
    }

    private static void applyKafkaAuthentication(DataStreamReader reader, RuntimeKafkaConnection connection) {
        if (connection.saslMechanism() == null) return;
        String mechanism = switch (connection.saslMechanism()) {
            case PLAIN -> "PLAIN";
            case SCRAM_SHA_256 -> "SCRAM-SHA-256";
            case SCRAM_SHA_512 -> "SCRAM-SHA-512";
        };
        String loginModule = connection.saslMechanism() == cn.superhuang.datascalpel.taskengine.contract.KafkaSaslMechanism.PLAIN
                ? "org.apache.kafka.common.security.plain.PlainLoginModule"
                : "org.apache.kafka.common.security.scram.ScramLoginModule";
        reader.option("kafka.sasl.mechanism", mechanism)
                .option("kafka.sasl.jaas.config", loginModule + " required username=\""
                        + jaas(connection.username()) + "\" password=\"" + jaas(connection.password()) + "\";");
    }

    private static String jaas(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String joinKey(String prefix, String path) {
        return prefix == null || prefix.isBlank() ? path : prefix + "/" + path;
    }
}
