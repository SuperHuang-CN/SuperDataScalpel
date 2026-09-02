package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.*;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.streaming.DataStreamWriter;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;

public final class SparkStreamingJobTestKit {
    private SparkStreamingJobTestKit() { }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final TestContextConfiguration.Builder delegate = new TestContextConfiguration.Builder();
        private final LinkedHashMap<String, TestKafkaTopic> kafkaInputs = new LinkedHashMap<>();
        private final LinkedHashMap<String, TestKafkaTopic> kafkaOutputs = new LinkedHashMap<>();
        private Path checkpointRoot;
        private Duration startupTimeout = Duration.ofSeconds(30);
        private Duration processingTimeout = Duration.ofSeconds(30);

        private Builder() { }

        public Builder sparkSession(SparkSession value) { delegate.sparkSession(value); return this; }
        public Builder identity(SparkJobIdentity value) { delegate.identity(value); return this; }
        public Builder parameter(String name, String value) { delegate.parameter(name, value); return this; }
        public Builder captureRowLimit(int value) { delegate.captureRowLimit(value); return this; }
        public Builder modelInput(String name, StructType schema, List<Row> rows) { delegate.modelInput(name, schema, rows); return this; }
        public Builder modelInput(String name, Dataset<Row> dataset) { delegate.modelInput(name, dataset); return this; }
        public Builder modelInputParquet(String name, Path path, StructType expectedSchema) {
            delegate.modelInputParquet(name, path, expectedSchema); return this;
        }
        public Builder modelOutput(String name, TestModelTarget target) { delegate.modelOutput(name, target); return this; }
        public Builder jdbcTable(String name, JdbcTableIdentifier table, StructType schema, List<Row> rows) {
            delegate.jdbcTable(name, table, schema, rows); return this;
        }
        public Builder jdbcTable(String name, JdbcTableIdentifier table, Dataset<Row> dataset) {
            delegate.jdbcTable(name, table, dataset); return this;
        }
        public Builder jdbcTableParquet(String name, JdbcTableIdentifier table, Path path, StructType expectedSchema) {
            delegate.jdbcTableParquet(name, table, path, expectedSchema); return this;
        }
        public Builder jdbcQuery(String name, String sql, StructType schema, List<Row> rows) {
            delegate.jdbcQuery(name, sql, schema, rows); return this;
        }
        public Builder jdbcQuery(String name, String sql, Dataset<Row> dataset) {
            delegate.jdbcQuery(name, sql, dataset); return this;
        }
        public Builder jdbcOutput(String name, JdbcTableIdentifier table, StructType schema) {
            delegate.jdbcOutput(name, table, schema); return this;
        }

        public Builder kafkaInput(String bindingName, TestKafkaTopic topic) {
            bindingName = required(bindingName);
            if (kafkaInputs.putIfAbsent(bindingName, Objects.requireNonNull(topic)) != null) {
                throw new IllegalArgumentException("Duplicate Kafka input binding: " + bindingName);
            }
            return this;
        }

        public Builder kafkaOutput(String bindingName, TestKafkaTopic topic) {
            bindingName = required(bindingName);
            if (kafkaOutputs.putIfAbsent(bindingName, Objects.requireNonNull(topic)) != null) {
                throw new IllegalArgumentException("Duplicate Kafka output binding: " + bindingName);
            }
            return this;
        }

        public Builder checkpointRoot(Path value) {
            checkpointRoot = Objects.requireNonNull(value).toAbsolutePath().normalize();
            return this;
        }

        public Builder startupTimeout(Duration value) { startupTimeout = positive(value, "startupTimeout"); return this; }
        public Builder processingTimeout(Duration value) { processingTimeout = positive(value, "processingTimeout"); return this; }

        public SparkStreamingJobTestRun start(SparkStreamingJob job) {
            Objects.requireNonNull(job, "job");
            TestStreamingContext context = new TestStreamingContext(
                    delegate.build(), kafkaInputs, kafkaOutputs, checkpointRoot);
            Throwable primary = null;
            try {
                invokeStart(job, context, startupTimeout);
                context.managedQueries().validateRegisteredSet();
                return new SparkStreamingJobTestRun(job, context, processingTimeout);
            } catch (Throwable throwable) {
                primary = unwrap(throwable);
                context.managedQueries().stopAllNewQueries(primary);
                try { job.onStop(context); }
                catch (Throwable cleanup) { primary.addSuppressed(cleanup); }
                try { context.close(); }
                catch (Throwable cleanup) { primary.addSuppressed(cleanup); }
                if (primary instanceof RuntimeException runtime) throw runtime;
                throw new SparkJobTestException("TESTKIT_STREAMING_JOB_START_FAILED",
                        "Streaming job failed during TestKit startup", primary);
            }
        }
    }

    private static void invokeStart(SparkStreamingJob job, TestStreamingContext context, Duration timeout) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "datascalpel-sdk-testkit-start");
            thread.setDaemon(true);
            return thread;
        });
        Future<?> future = executor.submit(() -> {
            try { job.start(context); }
            catch (Throwable throwable) { throw new CompletionException(throwable); }
        });
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new SparkJobTestException("TESTKIT_STREAMING_START_TIMEOUT",
                    "Streaming job did not finish query registration within " + timeout, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new SparkJobTestException("TESTKIT_STREAMING_START_INTERRUPTED",
                    "Streaming job startup was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable actual = unwrap(exception);
            if (actual instanceof RuntimeException runtime) throw runtime;
            throw new SparkJobTestException("TESTKIT_STREAMING_JOB_START_FAILED",
                    "Streaming job failed during TestKit startup", actual);
        } finally {
            executor.shutdownNow();
        }
    }

    static final class TestStreamingContext extends SparkJobTestContext implements SparkStreamingJobContext {
        private static final StructType KAFKA_SCHEMA = new StructType()
                .add("key", DataTypes.BinaryType, true)
                .add("value", DataTypes.BinaryType, true)
                .add("topic", DataTypes.StringType, false)
                .add("partition", DataTypes.IntegerType, false)
                .add("offset", DataTypes.LongType, false)
                .add("timestamp", DataTypes.TimestampType, false)
                .add("timestampType", DataTypes.IntegerType, false);

        private final Map<String, TestKafkaTopic> kafkaInputs;
        private final Map<String, TestKafkaTopic> kafkaOutputs;
        private final Path checkpointRoot;
        private final ManagedQueries managedQueries;
        private final KafkaResources kafka = new Kafka();

        private TestStreamingContext(
                TestContextConfiguration configuration,
                Map<String, TestKafkaTopic> kafkaInputs,
                Map<String, TestKafkaTopic> kafkaOutputs,
                Path explicitCheckpointRoot
        ) {
            super(configuration, true);
            this.kafkaInputs = Collections.unmodifiableMap(new LinkedHashMap<>(kafkaInputs));
            this.kafkaOutputs = Collections.unmodifiableMap(new LinkedHashMap<>(kafkaOutputs));
            checkpointRoot = explicitCheckpointRoot == null
                    ? workspaceRoot().resolve("checkpoints") : explicitCheckpointRoot;
            try { Files.createDirectories(checkpointRoot); }
            catch (java.io.IOException exception) {
                close();
                throw new SparkJobTestException("TESTKIT_CHECKPOINT_CREATE_FAILED",
                        "Unable to create TestKit checkpoint directory", exception);
            }
            managedQueries = new ManagedQueries(spark(), identity(), checkpointRoot, testObservability());
        }

        @Override public KafkaResources kafka() { requireOpen(); return kafka; }
        @Override public StreamingQueries queries() { requireOpen(); return managedQueries; }
        ManagedQueries managedQueries() { return managedQueries; }

        List<TestKafkaRecord> kafkaOutputRecords(String bindingName) {
            TestKafkaTopic topic = kafkaOutputs.get(required(bindingName));
            if (topic == null) {
                throw new SparkJobTestException("TESTKIT_KAFKA_OUTPUT_NOT_BOUND",
                        "Kafka output binding is not configured: " + bindingName);
            }
            List<TestKafkaRecord> result = new ArrayList<>();
            long offset = 0;
            for (Path path : topic.outputPaths()) {
                if (!containsDataFile(path)) continue;
                for (Row row : spark().read().parquet(path.toString()).collectAsList()) {
                    result.add(new TestKafkaRecord(
                            bytes(row.getAs("key")), bytes(row.getAs("value")),
                            topic.name(), 0, offset++, Instant.now(), 0));
                }
            }
            return List.copyOf(result);
        }

        private final class Kafka implements KafkaResources {
            @Override
            public Dataset<Row> readStream(String bindingName, KafkaStartingOffsets startingOffsets) {
                String name = required(bindingName);
                TestKafkaTopic topic = kafkaInputs.get(name);
                if (topic == null) {
                    throw new SparkJobTestException("TESTKIT_KAFKA_INPUT_NOT_BOUND",
                            "Kafka input binding is not configured: " + name);
                }
                String subscription = checkpointRoot.toAbsolutePath().normalize() + ":" + name;
                Path input = topic.subscribe(subscription, Objects.requireNonNull(startingOffsets));
                return spark().readStream().schema(KAFKA_SCHEMA).json(input.toString());
            }

            @Override
            public DataStreamWriter<Row> writeStream(String bindingName, Dataset<Row> source) {
                String name = required(bindingName);
                TestKafkaTopic topic = kafkaOutputs.get(name);
                if (topic == null) {
                    throw new SparkJobTestException("TESTKIT_KAFKA_OUTPUT_NOT_BOUND",
                            "Kafka output binding is not configured: " + name);
                }
                Objects.requireNonNull(source, "source");
                if (!source.isStreaming()) {
                    throw new SparkJobTestException("TESTKIT_KAFKA_OUTPUT_NOT_STREAMING",
                            "Kafka output source must be a streaming Dataset");
                }
                StructField value = field(source.schema(), "value");
                if (value == null || !kafkaType(value.dataType())) {
                    throw new SparkJobTestException("TESTKIT_KAFKA_VALUE_REQUIRED",
                            "Kafka output requires a String or Binary value column");
                }
                StructField key = field(source.schema(), "key");
                if (key != null && !kafkaType(key.dataType())) {
                    throw new SparkJobTestException("TESTKIT_KAFKA_KEY_INVALID",
                            "Kafka output key must be String or Binary when present");
                }
                Column keyColumn = key == null
                        ? lit(null).cast(DataTypes.BinaryType).as("key") : col("key");
                Dataset<Row> projected = source.select(keyColumn, col("value"));
                Path output = topic.registerOutput();
                return projected.writeStream().format("parquet").option("path", output.toString());
            }
        }

        private static StructField field(StructType schema, String name) {
            return Arrays.stream(schema.fields()).filter(value -> value.name().equals(name)).findFirst().orElse(null);
        }

        private static boolean kafkaType(DataType value) {
            return value.equals(DataTypes.StringType) || value.equals(DataTypes.BinaryType);
        }

        private static boolean containsDataFile(Path path) {
            try (var files = Files.walk(path)) {
                return files.anyMatch(file -> Files.isRegularFile(file)
                        && file.getFileName().toString().startsWith("part-"));
            } catch (java.io.IOException exception) {
                throw new SparkJobTestException("TESTKIT_KAFKA_OUTPUT_READ_FAILED",
                        "Unable to inspect test Kafka output", exception);
            }
        }

        private static byte[] bytes(Object value) {
            if (value == null) return null;
            if (value instanceof byte[] bytes) return bytes;
            if (value instanceof String text) return text.getBytes(StandardCharsets.UTF_8);
            throw new SparkJobTestException("TESTKIT_KAFKA_OUTPUT_TYPE_INVALID",
                    "Captured Kafka key/value has an unsupported type: " + value.getClass().getName());
        }
    }

    static final class ManagedQueries implements StreamingQueries {
        private static final Pattern LOGICAL_NAME = Pattern.compile("[A-Za-z0-9._-]{1,100}");
        private final SparkSession spark;
        private final UUID deploymentId;
        private final Path checkpointRoot;
        private final Set<UUID> baselineQueries;
        private final TestJobObservability observability;
        private final LinkedHashMap<String, RegisteredQuery> registered = new LinkedHashMap<>();

        private ManagedQueries(
                SparkSession spark,
                SparkJobIdentity identity,
                Path checkpointRoot,
                TestJobObservability observability
        ) {
            this.spark = spark;
            deploymentId = identity.deploymentId().orElseThrow();
            this.checkpointRoot = checkpointRoot;
            this.observability = observability;
            baselineQueries = Arrays.stream(spark.streams().active())
                    .map(StreamingQuery::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        @Override
        public synchronized StreamingQuery start(
                String logicalName,
                StreamingSinkType sinkType,
                StreamingQueryStarter starter
        ) throws Exception {
            if (logicalName == null || !LOGICAL_NAME.matcher(logicalName).matches()) {
                throw new SparkJobTestException("TESTKIT_STREAMING_QUERY_NAME_INVALID",
                        "Streaming query name must match [A-Za-z0-9._-]{1,100}");
            }
            if (registered.containsKey(logicalName)) {
                throw new SparkJobTestException("TESTKIT_STREAMING_QUERY_NAME_DUPLICATE",
                        "Streaming query name is duplicated: " + logicalName);
            }
            Objects.requireNonNull(sinkType, "sinkType");
            Objects.requireNonNull(starter, "starter");
            UUID queryId = UUID.nameUUIDFromBytes(
                    (deploymentId + ":" + logicalName).getBytes(StandardCharsets.UTF_8));
            String queryName = "datascalpel-" + deploymentId + "-" + queryId;
            Path checkpoint = checkpointRoot.resolve("queries").resolve(logicalName);
            StreamingQuery query = starter.start(new StreamingQuerySpec(queryName, checkpoint.toString()));
            if (query == null || !query.isActive() || !queryName.equals(query.name())) {
                if (query != null && query.isActive()) query.stop();
                throw new SparkJobTestException("TESTKIT_STREAMING_QUERY_REGISTRATION_INVALID",
                        "Streaming query must be active and use the provided query name: " + logicalName);
            }
            if (!Files.isRegularFile(checkpoint.resolve("metadata"))) {
                query.stop();
                throw new SparkJobTestException("TESTKIT_STREAMING_CHECKPOINT_NOT_USED",
                        "Streaming query did not use the provided checkpoint: " + logicalName);
            }
            registered.put(logicalName, new RegisteredQuery(queryId, logicalName, sinkType, checkpoint, query));
            observability.setPlatformGauge("datascalpel.streaming.registered_queries", registered.size());
            observability.platformInfo("streaming.query.registered", "StreamingQuery registered", Map.of(
                    "logicalName", logicalName,
                    "sinkType", sinkType.name(),
                    "queryName", queryName));
            return query;
        }

        synchronized void validateRegisteredSet() {
            if (registered.isEmpty()) {
                throw new SparkJobTestException("TESTKIT_STREAMING_QUERY_REQUIRED",
                        "Streaming job must register at least one query");
            }
            validateHealthy();
        }

        synchronized void validateHealthy() {
            for (RegisteredQuery value : registered.values()) {
                if (value.query().exception().isDefined()) {
                    Throwable failure = value.query().exception().get();
                    stopAllNewQueries(failure);
                    throw new SparkJobTestException("TESTKIT_STREAMING_QUERY_FAILED",
                            "Streaming query failed: " + value.logicalName(), failure);
                }
                if (!value.query().isActive()) {
                    SparkJobTestException failure = new SparkJobTestException(
                            "TESTKIT_STREAMING_QUERY_STOPPED_UNEXPECTEDLY",
                            "Streaming query stopped unexpectedly: " + value.logicalName());
                    stopAllNewQueries(failure);
                    throw failure;
                }
            }
            Set<UUID> expected = registered.values().stream().map(value -> value.query().id())
                    .collect(java.util.stream.Collectors.toSet());
            Set<UUID> actual = Arrays.stream(spark.streams().active()).map(StreamingQuery::id)
                    .filter(value -> !baselineQueries.contains(value))
                    .collect(java.util.stream.Collectors.toSet());
            if (!actual.equals(expected)) {
                SparkJobTestException failure = new SparkJobTestException(
                        "TESTKIT_UNREGISTERED_STREAMING_QUERY",
                        "SparkSession contains a query that was not registered through the TestKit context");
                stopAllNewQueries(failure);
                throw failure;
            }
        }

        synchronized void processAllAvailable(Duration timeout) {
            ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "datascalpel-sdk-testkit-progress");
                thread.setDaemon(true);
                return thread;
            });
            Future<?> future = executor.submit(() -> {
                for (RegisteredQuery value : registered.values()) {
                    try {
                        value.query().processAllAvailable();
                    } catch (Throwable failure) {
                        throw new QueryProcessingFailure(value.logicalName(), failure);
                    }
                }
            });
            try {
                future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException exception) {
                future.cancel(true);
                stopAllNewQueries(exception);
                throw new SparkJobTestException("TESTKIT_STREAMING_PROCESS_TIMEOUT",
                        "Streaming queries did not process available input within " + timeout, exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                stopAllNewQueries(exception);
                throw new SparkJobTestException("TESTKIT_STREAMING_PROCESS_INTERRUPTED",
                        "Streaming query processing was interrupted", exception);
            } catch (ExecutionException exception) {
                Throwable actual = unwrap(exception);
                stopAllNewQueries(actual);
                String logicalName = actual instanceof QueryProcessingFailure failure
                        ? failure.logicalName() : "unknown";
                Throwable cause = actual.getCause() == null ? actual : actual.getCause();
                throw new SparkJobTestException("TESTKIT_STREAMING_QUERY_FAILED",
                        "Streaming query failed while processing available input: " + logicalName, cause);
            } finally {
                executor.shutdownNow();
            }
            validateHealthy();
        }

        synchronized void stopAllNewQueries(Throwable primary) {
            SparkJobTestException stopFailure = null;
            for (StreamingQuery query : spark.streams().active()) {
                if (baselineQueries.contains(query.id())) continue;
                try { query.stop(); }
                catch (Exception exception) {
                    if (primary != null) {
                        primary.addSuppressed(exception);
                    } else if (stopFailure == null) {
                        stopFailure = new SparkJobTestException("TESTKIT_STREAMING_STOP_FAILED",
                                "Unable to stop streaming query " + query.name(), exception);
                    } else {
                        stopFailure.addSuppressed(exception);
                    }
                }
            }
            if (stopFailure != null) throw stopFailure;
        }

        synchronized List<String> queryNames() { return List.copyOf(registered.keySet()); }

        private record RegisteredQuery(
                UUID queryId,
                String logicalName,
                StreamingSinkType sinkType,
                Path checkpoint,
                StreamingQuery query
        ) { }

        private static final class QueryProcessingFailure extends RuntimeException {
            private final String logicalName;

            private QueryProcessingFailure(String logicalName, Throwable cause) {
                super(cause);
                this.logicalName = logicalName;
            }

            private String logicalName() { return logicalName; }
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private static String required(String value) {
        return TestContextConfiguration.required(value, "binding name");
    }

    private static Duration positive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }
}
