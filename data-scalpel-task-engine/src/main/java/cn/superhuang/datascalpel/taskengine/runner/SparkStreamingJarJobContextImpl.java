package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.SparkJarExecutionPayload;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import cn.superhuang.datascalpel.sdk.*;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeKafkaConnection;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import org.apache.spark.api.java.function.VoidFunction2;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.DataStreamWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.superhuang.data.scalpel.contract.execution.UserJobObservabilitySnapshot;

import java.util.Objects;
import java.util.function.Consumer;

final class SparkStreamingJarJobContextImpl implements SparkStreamingJobContext {
    private final SparkJarJobContextImpl delegate;
    private final KafkaResources kafka;
    private final StreamingQueries queries;

    SparkStreamingJarJobContextImpl(
            SparkSession spark,
            TaskExecutionManifest manifest,
            StreamingQueries queries,
            ObjectMapper objectMapper,
            Consumer<UserJobObservabilitySnapshot> observabilityPublisher,
            Consumer<cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreview> trialPreviewPublisher
    ) {
        this.delegate = new SparkJarJobContextImpl(
                spark, manifest, objectMapper, observabilityPublisher, trialPreviewPublisher);
        this.kafka = new Kafka(spark);
        this.queries = Objects.requireNonNull(queries);
    }

    @Override public SparkSession spark() { return delegate.spark(); }
    @Override public SparkJobIdentity identity() { return delegate.identity(); }
    @Override public SparkJobParameters parameters() { return delegate.parameters(); }
    @Override public ModelResources models() { return delegate.models(); }
    @Override public JdbcResources jdbc() { return delegate.jdbc(); }
    @Override public JobObservability observability() { return delegate.observability(); }
    @Override public KafkaResources kafka() { return kafka; }
    @Override public StreamingQueries queries() { return queries; }
    UserJobObservabilityRuntime observabilityRuntime() { return delegate.observabilityRuntime(); }
    cn.superhuang.data.scalpel.contract.execution.SparkJarTrialPreview trialPreview() {
        return delegate.trialPreview();
    }

    private final class Kafka implements KafkaResources {
        private final SparkSession spark;

        private Kafka(SparkSession spark) {
            this.spark = spark;
        }

        @Override
        public Dataset<Row> readStream(String bindingName, KafkaStartingOffsets startingOffsets) {
            SparkJarExecutionPayload.ResourceBinding binding = delegate.requireBinding(
                    bindingName, SparkJarResourceType.KAFKA_TOPIC, true);
            RuntimeKafkaConnection connection = kafkaConnection(binding);
            var reader = spark.readStream().format("kafka")
                    .option("kafka.bootstrap.servers", connection.bootstrapServers())
                    .option("subscribe", binding.topicName())
                    .option("startingOffsets", Objects.requireNonNull(startingOffsets).name().toLowerCase())
                    .option("failOnDataLoss", "true")
                    .option("kafka.security.protocol", connection.securityProtocol().name());
            applyAuthentication(reader, connection);
            return reader.load();
        }

        @Override
        public DataStreamWriter<Row> writeStream(String bindingName, Dataset<Row> source) {
            SparkJarExecutionPayload.ResourceBinding binding = delegate.requireBinding(
                    bindingName, SparkJarResourceType.KAFKA_TOPIC, false);
            if (delegate.trial()) {
                Dataset<Row> value = Objects.requireNonNull(source);
                return value.writeStream().foreachBatch((VoidFunction2<Dataset<Row>, Long>) (batch, batchId) ->
                        delegate.previewKafka(binding.bindingName(), binding.topicName(), batch));
            }
            RuntimeKafkaConnection connection = kafkaConnection(binding);
            DataStreamWriter<Row> writer = Objects.requireNonNull(source).writeStream()
                    .format("kafka")
                    .option("topic", binding.topicName())
                    .option("kafka.bootstrap.servers", connection.bootstrapServers())
                    .option("kafka.security.protocol", connection.securityProtocol().name());
            applyAuthentication(writer, connection);
            return writer;
        }

        private RuntimeKafkaConnection kafkaConnection(SparkJarExecutionPayload.ResourceBinding binding) {
            RuntimeDataSource source = delegate.runtimeSource(binding.resourceId());
            if (source.kafkaConnection() == null) {
                throw new RunnerExecutionException("SDK_KAFKA_BINDING_UNAVAILABLE",
                        "绑定 Kafka 运行连接不存在", null);
            }
            return source.kafkaConnection();
        }
    }

    private static void applyAuthentication(
            org.apache.spark.sql.streaming.DataStreamReader reader,
            RuntimeKafkaConnection connection
    ) {
        if (connection.saslMechanism() == null) return;
        reader.option("kafka.sasl.mechanism", mechanism(connection))
                .option("kafka.sasl.jaas.config", jaasConfig(connection));
    }

    private static void applyAuthentication(DataStreamWriter<Row> writer, RuntimeKafkaConnection connection) {
        if (connection.saslMechanism() == null) return;
        writer.option("kafka.sasl.mechanism", mechanism(connection))
                .option("kafka.sasl.jaas.config", jaasConfig(connection));
    }

    private static String mechanism(RuntimeKafkaConnection connection) {
        return switch (connection.saslMechanism()) {
            case PLAIN -> "PLAIN";
            case SCRAM_SHA_256 -> "SCRAM-SHA-256";
            case SCRAM_SHA_512 -> "SCRAM-SHA-512";
        };
    }

    private static String jaasConfig(RuntimeKafkaConnection connection) {
        String module = connection.saslMechanism()
                == cn.superhuang.datascalpel.taskengine.contract.KafkaSaslMechanism.PLAIN
                ? "org.apache.kafka.common.security.plain.PlainLoginModule"
                : "org.apache.kafka.common.security.scram.ScramLoginModule";
        return module + " required username=\"" + jaas(connection.username())
                + "\" password=\"" + jaas(connection.password()) + "\";";
    }

    private static String jaas(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
