package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeDataAccess;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedOutput;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedKafkaOutput;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedFileOutput;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeFileInput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeFileStorage;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeKafkaConnection;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeS3Connection;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.httpapi.HttpApiPullConnectorRegistry;
import cn.superhuang.datascalpel.taskengine.httpapi.HttpApiPullException;
import cn.superhuang.datascalpel.taskengine.spark.HttpApiBatchDatasetStager;
import cn.superhuang.datascalpel.taskengine.spark.HttpApiBatchStagingException;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.streaming.DataStreamReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RuntimeCanvasNodeDataAccess implements CanvasNodeDataAccess {
    private final SparkSession spark;
    private final Map<UUID, RuntimeDataSource> runtimeSources;
    private final RuntimeFileStorage runtimeFileStorage;
    private final Map<UUID, RuntimeFileInput> runtimeFileInputs;
    private final FileDatasetBatchReaderRegistry fileReaders = new FileDatasetBatchReaderRegistry();
    private final List<HttpApiBatchDatasetStager> httpApiStagers = new ArrayList<>();

    RuntimeCanvasNodeDataAccess(
            SparkSession spark,
            Map<UUID, RuntimeDataSource> runtimeSources
    ) {
        this(spark, runtimeSources, null, List.of());
    }

    RuntimeCanvasNodeDataAccess(
            SparkSession spark,
            Map<UUID, RuntimeDataSource> runtimeSources,
            RuntimeFileStorage runtimeFileStorage,
            List<RuntimeFileInput> runtimeFileInputs
    ) {
        this.spark = spark;
        this.runtimeSources = runtimeSources;
        this.runtimeFileStorage = runtimeFileStorage;
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
            CanvasTableSchema expectedSchema
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "输入数据源 ID 无效", node.id());
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, dataSourceId, DataSourcePurpose.SOURCE, node.id());
        if (runtime.connectionKind() != ConnectionKind.JDBC) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "输入节点需要 JDBC 数据源", node.id());
        }
        Dataset<Row> dataset = CanvasTaskExecutor.reader(spark, runtime)
                .option("dbtable", CanvasTaskExecutor.qualifiedTable(
                        runtime, node.configuration().tableName()))
                .load();
        CanvasTaskExecutor.validateRuntimeSchema(expectedSchema.columns(), dataset, node.id());
        return dataset;
    }

    @Override
    public Dataset<Row> readFileDatasetInput(
            FileDatasetInputNodeDefinition node,
            MetadataIndex.FileDatasetTableEntry table,
            CanvasTableSchema expectedSchema
    ) {
        UUID tableId = CanvasTaskExecutor.uuid(
                node.configuration().fileDatasetTableId(),
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
        if (!input.schemaFingerprint().equals(FileDatasetSchemaFingerprint.calculate(expectedSchema.columns()))) {
            throw new RunnerExecutionException(
                    "RUNTIME_SCHEMA_MISMATCH",
                    "文件数据集 Manifest Schema 与任务定义不一致",
                    node.id()
            );
        }
        return fileReaders.read(spark, runtimeFileStorage, input, expectedSchema, node.id(), node.name());
    }

    @Override
    public Dataset<Row> readHttpApiInput(
            HttpApiInputNodeDefinition node,
            CanvasTableSchema expectedSchema
    ) {
        UUID dataSourceId = CanvasTaskExecutor.uuid(
                node.configuration().dataSourceId(), "API 数据源 ID 无效", node.id());
        UUID resourceId = CanvasTaskExecutor.uuid(
                node.configuration().resourceId(), "API 资源 ID 无效", node.id());
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
                SparkTypeMapper.toStructType(expectedSchema.columns())
        );
        httpApiStagers.add(stager);
        try {
            HttpApiPullConnectorRegistry.require(resource.connectorType()).pullBatches(
                    new HttpApiContracts.PullRequest(
                            runtime.httpApiConnection(),
                            resource,
                            node.configuration().runtimeParameters()
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
    public Dataset<Row> readKafkaInput(
            KafkaInputNodeDefinition node,
            CanvasTableSchema expectedSchema
    ) {
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources, node.configuration().dataSourceId(), DataSourcePurpose.SOURCE, node.id());
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
        return raw.select(functions.from_json(
                        functions.col("value").cast("string"),
                        SparkTypeMapper.toStructType(expectedSchema.columns()),
                        Map.of("mode", "FAILFAST"))
                .alias("data")).select("data.*");
    }

    @Override
    public Dataset<Row> readModelInput(
            ModelInputNodeDefinition node,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema expectedSchema
    ) {
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSourceForModelRead(
                runtimeSources, model.metadata().dataSourceId(), node.id());
        String qualifiedTableName = CanvasTaskExecutor.qualifiedModelTable(runtime, model);
        Dataset<Row> dataset = CanvasTaskExecutor.reader(spark, runtime)
                .option("dbtable", qualifiedTableName)
                .load();
        CanvasTaskExecutor.validateRuntimeSchema(expectedSchema.columns(), dataset, node.id());
        return dataset;
    }

    @Override
    public CanvasPreparedOutput prepareJdbcOutput(
            JdbcOutputNodeDefinition node,
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
        String qualifiedTableName = CanvasTaskExecutor.qualifiedTable(
                runtime, node.configuration().targetTableName());
        Dataset<Row> actualTarget = CanvasTaskExecutor.reader(spark, runtime)
                .option("dbtable", qualifiedTableName)
                .load();
        CanvasTaskExecutor.validateRuntimeSchema(targetSchema.columns(), actualTarget, node.id());
        return new CanvasPreparedOutput(
                node,
                runtime,
                qualifiedTableName,
                CanvasTaskExecutor.displayTable(runtime, node.configuration().targetTableName()),
                node.configuration().writeMode(),
                dataset
        );
    }

    @Override
    public CanvasPreparedOutput prepareModelOutput(
            ModelOutputNodeDefinition node,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
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
        String qualifiedTableName = CanvasTaskExecutor.qualifiedModelTable(runtime, model);
        Dataset<Row> actualTarget = CanvasTaskExecutor.reader(spark, runtime)
                .option("dbtable", qualifiedTableName)
                .load();
        CanvasTaskExecutor.validateRuntimeSchema(targetSchema.columns(), actualTarget, node.id());
        return new CanvasPreparedOutput(
                node,
                runtime,
                qualifiedTableName,
                CanvasTaskExecutor.displayModelTable(runtime, model),
                node.configuration().writeMode(),
                dataset
        );
    }

    @Override
    public CanvasPreparedKafkaOutput prepareKafkaOutput(
            KafkaOutputNodeDefinition node,
            Dataset<Row> dataset
    ) {
        RuntimeDataSource runtime = CanvasTaskExecutor.requireRuntimeSource(
                runtimeSources,
                node.configuration().dataSourceId(),
                DataSourcePurpose.DISTRIBUTION,
                node.id()
        );
        if (runtime.connectionKind() != ConnectionKind.KAFKA || runtime.kafkaConnection() == null) {
            throw new RunnerExecutionException(
                    "RUNTIME_DATA_SOURCE_UNAVAILABLE", "输出节点需要 Kafka 数据源", node.id());
        }
        return new CanvasPreparedKafkaOutput(
                node,
                runtime,
                node.configuration().topic(),
                dataset
        );
    }

    @Override
    public CanvasPreparedFileOutput prepareFileOutput(
            FileOutputNodeDefinition node,
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
        String targetKey = joinKey(connection.rootPrefix(), node.configuration().targetPath());
        return new CanvasPreparedFileOutput(
                node,
                runtime,
                "s3a://" + connection.bucket() + "/" + targetKey,
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
