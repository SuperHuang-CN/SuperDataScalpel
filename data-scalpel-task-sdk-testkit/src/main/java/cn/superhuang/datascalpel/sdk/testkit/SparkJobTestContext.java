package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.*;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.spark.sql.functions.col;

public class SparkJobTestContext implements SparkJobContext, AutoCloseable {
    private final SparkSession spark;
    private final boolean ownsSpark;
    private final Path workspaceRoot;
    private final SparkJobIdentity identity;
    private final SparkJobParameters parameters;
    private final boolean streaming;
    private final int captureRowLimit;
    private final Map<String, RuntimeModelBinding> models;
    private final Map<String, RuntimeJdbcBinding> jdbc;
    private final Map<String, CopyOnWriteArrayList<CapturedModelWrite>> modelWrites = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<CapturedJdbcWrite>> jdbcWrites = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<String>> jdbcQueryCalls = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<TestJdbcQueryReadCall>> jdbcQueryReadCalls = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<TestModelReadCall>> modelReadCalls = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<TestJdbcTableReadCall>> jdbcTableReadCalls = new ConcurrentHashMap<>();
    private final AtomicLong affectedRows = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final TestJobObservability observability;

    protected SparkJobTestContext(TestContextConfiguration configuration, boolean streaming) {
        this.streaming = streaming;
        this.captureRowLimit = configuration.captureRowLimit;
        try {
            workspaceRoot = Files.createTempDirectory("datascalpel-sdk-testkit-");
        } catch (IOException exception) {
            throw new SparkJobTestException("TESTKIT_WORKSPACE_CREATE_FAILED",
                    "Unable to create TestKit workspace", exception);
        }
        if (configuration.sparkSession == null) {
            ownsSpark = true;
            try {
                spark = createSpark(workspaceRoot);
            } catch (RuntimeException failure) {
                try { deleteRecursively(workspaceRoot); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        } else {
            ownsSpark = false;
            spark = configuration.sparkSession;
        }
        observability = new TestJobObservability(spark);
        identity = configuration.identity == null
                ? (streaming ? TestSparkJobIdentity.streaming().build() : TestSparkJobIdentity.batch().build())
                : configuration.identity;
        if (streaming != identity.deploymentId().isPresent()) {
            closeAfterConstructionFailure();
            throw new IllegalArgumentException(streaming
                    ? "Streaming TestKit identity requires deploymentId"
                    : "Batch TestKit identity must not define deploymentId");
        }
        parameters = new Parameters(configuration.parameters);
        try {
            models = materializeModels(configuration.models);
            jdbc = materializeJdbc(configuration.jdbc);
        } catch (RuntimeException exception) {
            closeAfterConstructionFailure();
            throw exception;
        }
    }

    public static Builder builder() { return new Builder(); }

    @Override public SparkSession spark() { requireOpen(); return spark; }
    @Override public SparkJobIdentity identity() { return identity; }
    @Override public SparkJobParameters parameters() { return parameters; }
    @Override public ModelResources models() { requireOpen(); return new Models(); }
    @Override public JdbcResources jdbc() { requireOpen(); return new Jdbc(); }
    @Override public JobObservability observability() { requireOpen(); return observability; }

    public List<TestObservabilityEvent> observabilityEvents() { return observability.events(); }
    public Optional<TestJobStatus> latestStatus() { return observability.latestStatus(); }
    public Map<String, TestMetricSnapshot> metricSnapshot() { return observability.snapshot(); }
    public void assertCounter(String name, long expected) {
        TestMetricSnapshot metric = requireMetric(name, TestMetricKind.COUNTER);
        if (!Objects.equals(metric.counterValue(), expected)) {
            throw new AssertionError("Expected counter " + name + "=" + expected + " but was " + metric.counterValue());
        }
    }
    public void assertGauge(String name, double expected) {
        TestMetricSnapshot metric = requireMetric(name, TestMetricKind.GAUGE);
        if (metric.gaugeValue() == null || Double.compare(metric.gaugeValue(), expected) != 0) {
            throw new AssertionError("Expected gauge " + name + "=" + expected + " but was " + metric.gaugeValue());
        }
    }
    public void assertTimerRecorded(String name) {
        TestMetricSnapshot metric = requireMetric(name, TestMetricKind.TIMER);
        if (metric.count() == null || metric.count() < 1 || metric.lastDurationMillis() == null
                || metric.lastDurationMillis() < 0 || metric.totalDurationMillis() == null
                || metric.totalDurationMillis() < 0 || metric.maxDurationMillis() == null
                || metric.maxDurationMillis() < 0) {
            throw new AssertionError("Timer was not recorded: " + name);
        }
    }

    public List<CapturedModelWrite> modelWrites(String bindingName) {
        return List.copyOf(modelWrites.getOrDefault(required(bindingName), new CopyOnWriteArrayList<>()));
    }

    public List<CapturedJdbcWrite> jdbcWrites(String bindingName) {
        return List.copyOf(jdbcWrites.getOrDefault(required(bindingName), new CopyOnWriteArrayList<>()));
    }

    public List<String> jdbcQueryCalls(String bindingName) {
        return List.copyOf(jdbcQueryCalls.getOrDefault(required(bindingName), new CopyOnWriteArrayList<>()));
    }

    public List<TestJdbcQueryReadCall> jdbcQueryReadCalls(String bindingName) {
        return List.copyOf(jdbcQueryReadCalls.getOrDefault(required(bindingName), new CopyOnWriteArrayList<>()));
    }

    public List<TestModelReadCall> modelReadCalls(String bindingName) {
        return List.copyOf(modelReadCalls.getOrDefault(required(bindingName), new CopyOnWriteArrayList<>()));
    }

    public List<TestJdbcTableReadCall> jdbcTableReadCalls(String bindingName) {
        return List.copyOf(jdbcTableReadCalls.getOrDefault(required(bindingName), new CopyOnWriteArrayList<>()));
    }

    public long affectedRows() { return affectedRows.get(); }

    protected final Path workspaceRoot() { return workspaceRoot; }
    protected final boolean streaming() { return streaming; }
    protected final TestJobObservability testObservability() { return observability; }
    protected final void requireOpen() {
        if (closed.get()) throw new SparkJobTestException("TESTKIT_CONTEXT_CLOSED", "TestKit context is closed");
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        RuntimeException failure = null;
        if (ownsSpark) {
            try { spark.stop(); }
            catch (RuntimeException exception) { failure = exception; }
        }
        try { deleteRecursively(workspaceRoot); }
        catch (RuntimeException exception) {
            if (failure == null) failure = exception; else failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    private final class Models implements ModelResources {
        @Override
        public Dataset<Row> read(String bindingName, JdbcReadOptions options) {
            String name = required(bindingName);
            options = Objects.requireNonNull(options, "options");
            modelReadCalls.computeIfAbsent(name, ignored -> new CopyOnWriteArrayList<>())
                    .add(new TestModelReadCall(name, options));
            RuntimeModelBinding binding = models.get(name);
            if (binding == null || binding.input() == null) {
                throw error("TESTKIT_MODEL_READ_NOT_BOUND", "Model read binding is not configured: " + name);
            }
            return binding.input();
        }

        @Override
        public ModelWriteOperation write(String bindingName, Dataset<Row> source) {
            String name = required(bindingName);
            RuntimeModelBinding binding = models.get(name);
            if (binding == null || binding.target() == null) {
                throw error("TESTKIT_MODEL_WRITE_NOT_BOUND", "Model write binding is not configured: " + name);
            }
            return new ModelWriter(name, binding.target(), Objects.requireNonNull(source));
        }
    }

    private final class Jdbc implements JdbcResources {
        @Override
        public Dataset<Row> readTable(String bindingName, JdbcTableIdentifier table, JdbcReadOptions options) {
            String name = required(bindingName);
            table = Objects.requireNonNull(table, "table"); options = Objects.requireNonNull(options, "options");
            jdbcTableReadCalls.computeIfAbsent(name, ignored -> new CopyOnWriteArrayList<>())
                    .add(new TestJdbcTableReadCall(name, table, options));
            RuntimeJdbcBinding binding = jdbc.get(name);
            Dataset<Row> result = binding == null ? null : binding.tables().get(table);
            if (result == null) {
                throw error("TESTKIT_JDBC_TABLE_NOT_BOUND", "JDBC table is not configured: " + name + " / " + table);
            }
            return result;
        }

        @Override
        public Dataset<Row> readQuery(String bindingName, String sql, JdbcReadOptions options) {
            String name = required(bindingName);
            options = Objects.requireNonNull(options, "options");
            RuntimeJdbcBinding binding = jdbc.get(name);
            String normalized = TestContextConfiguration.normalizedSql(sql);
            jdbcQueryCalls.computeIfAbsent(name, ignored -> new CopyOnWriteArrayList<>()).add(normalized);
            jdbcQueryReadCalls.computeIfAbsent(name, ignored -> new CopyOnWriteArrayList<>())
                    .add(new TestJdbcQueryReadCall(name, normalized, options));
            Dataset<Row> result = binding == null ? null : binding.queries().get(normalized);
            if (result == null) {
                throw error("TESTKIT_JDBC_QUERY_NOT_BOUND", "JDBC query is not configured: " + name + " / " + normalized);
            }
            return result;
        }

        @Override
        public JdbcWriteOperation write(String bindingName, Dataset<Row> source) {
            String name = required(bindingName);
            RuntimeJdbcBinding binding = jdbc.get(name);
            if (binding == null || binding.targets().isEmpty()) {
                throw error("TESTKIT_JDBC_WRITE_NOT_BOUND", "JDBC write binding is not configured: " + name);
            }
            return new JdbcWriter(name, binding.targets(), Objects.requireNonNull(source));
        }
    }

    private final class ModelWriter implements ModelWriteOperation {
        private final String bindingName;
        private final TestModelTarget target;
        private final Dataset<Row> source;
        private final LinkedHashMap<String, String> mappings = new LinkedHashMap<>();
        private ModelWriteMode mode = ModelWriteMode.APPEND;

        private ModelWriter(String bindingName, TestModelTarget target, Dataset<Row> source) {
            this.bindingName = bindingName; this.target = target; this.source = source;
        }

        @Override public ModelWriteOperation mode(ModelWriteMode value) { mode = Objects.requireNonNull(value); return this; }
        @Override public ModelWriteOperation map(String targetColumn, String sourceColumn) {
            putMapping(mappings, targetColumn, sourceColumn); return this;
        }
        @Override public ModelWriteOperation mapSameName() {
            requireNoMappings(mappings);
            for (String column : source.columns()) putMapping(mappings, column, column);
            return this;
        }
        @Override public ModelWriteOperation checkSchema() {
            validateModelSchema(source.schema(), target.schema());
            return this;
        }

        @Override
        public WriteResult execute() {
            return observedWrite("model", bindingName, mode.name(), bindingName, this::executeInternal);
        }

        private WriteResult executeInternal() {
            if (streaming && mode == ModelWriteMode.OVERWRITE) {
                throw error("TESTKIT_STREAMING_OVERWRITE_NOT_ALLOWED", "Streaming model write does not allow OVERWRITE");
            }
            if (target.external() && mode == ModelWriteMode.OVERWRITE) {
                throw error("TESTKIT_EXTERNAL_MODEL_OVERWRITE_NOT_ALLOWED", "EXTERNAL model does not allow OVERWRITE");
            }
            validateMappings(mappings, targetColumns(target.schema()), source);
            if (!mappings.keySet().containsAll(target.requiredColumns())) {
                throw error("TESTKIT_REQUIRED_TARGET_COLUMN_NOT_MAPPED", "Required model columns are not fully mapped");
            }
            if (streaming && hasGeometry(target.schema())) {
                throw error("TESTKIT_STREAMING_GEOMETRY_NOT_ALLOWED", "Streaming model write does not support Geometry");
            }
            Dataset<Row> projected = source.select(mappings.entrySet().stream()
                    .map(entry -> col(entry.getValue()).cast(target.schema().apply(entry.getKey()).dataType()).as(entry.getKey()))
                    .toArray(Column[]::new));
            if (mode == ModelWriteMode.UPSERT) {
                validateKeys(projected, target.primaryKeyColumns(), mappings.keySet());
            }
            List<Row> rows = capture(projected);
            CapturedModelWrite write = new CapturedModelWrite(
                    bindingName, mode, mappings, projected.schema(), rows, rows.size());
            modelWrites.computeIfAbsent(bindingName, ignored -> new CopyOnWriteArrayList<>()).add(write);
            if (!streaming) affectedRows.addAndGet(rows.size());
            return WriteResult.known(rows.size());
        }
    }

    private final class JdbcWriter implements JdbcWriteOperation {
        private final String bindingName;
        private final Map<JdbcTableIdentifier, StructType> targets;
        private final Dataset<Row> source;
        private final LinkedHashMap<String, String> mappings = new LinkedHashMap<>();
        private JdbcWriteMode mode = JdbcWriteMode.APPEND;
        private JdbcTableIdentifier table;
        private List<String> keys = List.of();

        private JdbcWriter(
                String bindingName,
                Map<JdbcTableIdentifier, StructType> targets,
                Dataset<Row> source
        ) {
            this.bindingName = bindingName; this.targets = targets; this.source = source;
        }

        @Override public JdbcWriteOperation table(JdbcTableIdentifier value) { table = Objects.requireNonNull(value); return this; }
        @Override public JdbcWriteOperation mode(JdbcWriteMode value) { mode = Objects.requireNonNull(value); return this; }
        @Override public JdbcWriteOperation upsertKeyColumns(String... values) {
            keys = normalizedNames(values, "UPSERT key");
            return this;
        }
        @Override public JdbcWriteOperation map(String targetColumn, String sourceColumn) {
            putMapping(mappings, targetColumn, sourceColumn); return this;
        }

        @Override
        public WriteResult execute() {
            return observedWrite("jdbc", bindingName, mode.name(),
                    table == null ? "unconfigured" : table.toString(), this::executeInternal);
        }

        private WriteResult executeInternal() {
            if (table == null) {
                throw error("TESTKIT_JDBC_TARGET_REQUIRED", "JDBC target table is required");
            }
            StructType targetSchema = targets.get(table);
            if (targetSchema == null) {
                throw error("TESTKIT_JDBC_TARGET_NOT_BOUND",
                        "JDBC target table is not configured: " + bindingName + " / " + table);
            }
            if (streaming && mode == JdbcWriteMode.OVERWRITE) {
                throw error("TESTKIT_STREAMING_OVERWRITE_NOT_ALLOWED", "Streaming JDBC write does not allow OVERWRITE");
            }
            validateMappings(mappings, targetColumns(targetSchema), source);
            Dataset<Row> projected = source.select(mappings.entrySet().stream()
                    .map(entry -> col(entry.getValue()).cast(targetSchema.apply(entry.getKey()).dataType()).as(entry.getKey()))
                    .toArray(Column[]::new));
            if (streaming && hasGeometry(projected.schema())) {
                throw error("TESTKIT_STREAMING_GEOMETRY_NOT_ALLOWED", "Streaming JDBC write does not support Geometry");
            }
            if (mode == JdbcWriteMode.UPSERT) validateKeys(projected, keys, mappings.keySet());
            List<Row> rows = capture(projected);
            CapturedJdbcWrite write = new CapturedJdbcWrite(
                    bindingName, table, mode, keys, mappings, projected.schema(), rows, rows.size());
            jdbcWrites.computeIfAbsent(bindingName, ignored -> new CopyOnWriteArrayList<>()).add(write);
            if (!streaming) affectedRows.addAndGet(rows.size());
            return WriteResult.known(rows.size());
        }
    }

    private WriteResult observedWrite(
            String kind,
            String bindingName,
            String mode,
            String target,
            java.util.function.Supplier<WriteResult> action
    ) {
        String prefix = "datascalpel." + kind + ".write.";
        Map<String, String> attributes = Map.of("binding", bindingName, "mode", mode, "target", target);
        observability.addPlatformCounter(prefix + "attempts", 1);
        observability.platformInfo(kind + ".write.started", "SDK write started", attributes);
        long started = System.nanoTime();
        try {
            WriteResult result = action.get();
            observability.addPlatformCounter(prefix + "successes", 1);
            if (result.affectedRows() != null) {
                observability.addPlatformCounter(prefix + "rows", result.affectedRows());
            }
            observability.platformInfo(kind + ".write.succeeded", "SDK write succeeded", attributes);
            return result;
        } catch (RuntimeException | Error failure) {
            observability.platformError(kind + ".write.failed", "SDK write failed", attributes);
            throw failure;
        } finally {
            observability.recordPlatformTimer(prefix + "duration", TimeUnit.NANOSECONDS.toMillis(
                    Math.max(0, System.nanoTime() - started)));
        }
    }

    private TestMetricSnapshot requireMetric(String name, TestMetricKind kind) {
        TestMetricSnapshot metric = metricSnapshot().get(name);
        if (metric == null) throw new AssertionError("Metric was not recorded: " + name);
        if (metric.kind() != kind) {
            throw new AssertionError("Expected metric kind " + kind + " but was " + metric.kind());
        }
        return metric;
    }

    private List<Row> capture(Dataset<Row> dataset) {
        List<Row> rows = dataset.limit(captureRowLimit + 1).collectAsList();
        if (rows.size() > captureRowLimit) {
            throw error("TESTKIT_CAPTURE_LIMIT_EXCEEDED",
                    "Captured write exceeds row limit " + captureRowLimit);
        }
        return rows;
    }

    private static void validateMappings(
            LinkedHashMap<String, String> mappings,
            Set<String> targetColumns,
            Dataset<Row> source
    ) {
        if (mappings.isEmpty()) throw error("TESTKIT_COLUMN_MAPPING_REQUIRED", "At least one column mapping is required");
        Set<String> sourceColumns = new HashSet<>(Arrays.asList(source.columns()));
        mappings.forEach((target, sourceName) -> {
            if (targetColumns != null && !targetColumns.contains(target)) {
                throw error("TESTKIT_TARGET_COLUMN_NOT_FOUND", "Target column does not exist: " + target);
            }
            if (!sourceColumns.contains(sourceName)) {
                throw error("TESTKIT_SOURCE_COLUMN_NOT_FOUND", "Source column does not exist: " + sourceName);
            }
        });
    }

    private static void requireNoMappings(LinkedHashMap<String, String> mappings) {
        if (!mappings.isEmpty()) {
            throw error("TESTKIT_SAME_NAME_MAPPING_AFTER_EXPLICIT_MAPPING",
                    "mapSameName cannot be combined with explicit column mappings");
        }
    }

    private static void validateModelSchema(StructType source, StructType target) {
        Map<String, DataType> sourceColumns = schemaTypes(source);
        Map<String, DataType> targetColumns = schemaTypes(target);
        List<String> missing = targetColumns.keySet().stream().filter(name -> !sourceColumns.containsKey(name)).toList();
        List<String> unexpected = sourceColumns.keySet().stream().filter(name -> !targetColumns.containsKey(name)).toList();
        List<String> incompatible = targetColumns.keySet().stream()
                .filter(sourceColumns::containsKey)
                .filter(name -> !targetColumns.get(name).equals(sourceColumns.get(name)))
                .map(name -> name + " (expected " + targetColumns.get(name).catalogString()
                        + ", actual " + sourceColumns.get(name).catalogString() + ")")
                .toList();
        if (missing.isEmpty() && unexpected.isEmpty() && incompatible.isEmpty()) return;
        List<String> details = new ArrayList<>();
        if (!missing.isEmpty()) details.add("missing target columns: " + missing);
        if (!unexpected.isEmpty()) details.add("unexpected source columns: " + unexpected);
        if (!incompatible.isEmpty()) details.add("incompatible columns: " + incompatible);
        throw error("TESTKIT_MODEL_SCHEMA_MISMATCH", String.join("; ", details));
    }

    private static Map<String, DataType> schemaTypes(StructType schema) {
        LinkedHashMap<String, DataType> result = new LinkedHashMap<>();
        for (StructField field : schema.fields()) {
            if (result.putIfAbsent(field.name(), field.dataType()) != null) {
                throw error("TESTKIT_MODEL_SCHEMA_MISMATCH", "duplicate schema column: " + field.name());
            }
        }
        return result;
    }

    private static void validateKeys(Dataset<Row> dataset, List<String> keys, Set<String> mappedTargets) {
        if (keys.isEmpty()) throw error("TESTKIT_UPSERT_KEY_REQUIRED", "UPSERT key is required");
        if (!mappedTargets.containsAll(keys)) {
            throw error("TESTKIT_UPSERT_KEY_NOT_MAPPED", "UPSERT keys must be fully mapped");
        }
        Column nullCondition = keys.stream().map(key -> col(key).isNull()).reduce(Column::or).orElseThrow();
        if (dataset.filter(nullCondition).limit(1).count() > 0) {
            throw error("TESTKIT_UPSERT_KEY_NULL", "UPSERT key must not contain NULL");
        }
        Column[] keyColumns = keys.stream().map(org.apache.spark.sql.functions::col).toArray(Column[]::new);
        if (dataset.groupBy(keyColumns).count().filter(col("count").gt(1)).limit(1).count() > 0) {
            throw error("TESTKIT_UPSERT_DUPLICATE_KEY", "Captured write contains duplicate UPSERT keys");
        }
    }

    private Map<String, RuntimeModelBinding> materializeModels(Map<String, TestContextConfiguration.ModelBinding> values) {
        LinkedHashMap<String, RuntimeModelBinding> result = new LinkedHashMap<>();
        values.forEach((name, value) -> result.put(name, new RuntimeModelBinding(
                value.input() == null ? null : value.input().materialize(spark), value.target())));
        return Collections.unmodifiableMap(result);
    }

    private Map<String, RuntimeJdbcBinding> materializeJdbc(Map<String, TestContextConfiguration.JdbcBinding> values) {
        LinkedHashMap<String, RuntimeJdbcBinding> result = new LinkedHashMap<>();
        values.forEach((name, value) -> {
            LinkedHashMap<JdbcTableIdentifier, Dataset<Row>> tables = new LinkedHashMap<>();
            value.tables().forEach((table, input) -> tables.put(table, input.materialize(spark)));
            LinkedHashMap<String, Dataset<Row>> queries = new LinkedHashMap<>();
            value.queries().forEach((sql, input) -> queries.put(sql, input.materialize(spark)));
            result.put(name, new RuntimeJdbcBinding(
                    Collections.unmodifiableMap(tables), Collections.unmodifiableMap(queries), value.targets()));
        });
        return Collections.unmodifiableMap(result);
    }

    private static SparkSession createSpark(Path workspace) {
        try {
            return SparkSession.builder()
                    .master("local[2]")
                    .appName("DataScalpel SDK TestKit")
                    .config("spark.ui.enabled", "false")
                    .config("spark.sql.session.timeZone", "UTC")
                    .config("spark.sql.caseSensitive", "true")
                    .config("spark.sql.ansi.enabled", "true")
                    .config("spark.sql.shuffle.partitions", "2")
                    .config("spark.sql.catalogImplementation", "in-memory")
                    .config("spark.sql.warehouse.dir", workspace.resolve("warehouse").toUri().toString())
                    .config("spark.driver.host", "127.0.0.1")
                    .config("spark.driver.bindAddress", "127.0.0.1")
                    .getOrCreate();
        } catch (RuntimeException exception) {
            throw new SparkJobTestException("TESTKIT_SPARK_SESSION_CREATE_FAILED",
                    "Unable to create local SparkSession", exception);
        }
    }

    private void closeAfterConstructionFailure() {
        try { if (ownsSpark) spark.stop(); } catch (RuntimeException ignored) { }
        try { deleteRecursively(workspaceRoot); } catch (RuntimeException ignored) { }
    }

    static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        } catch (IOException exception) {
            throw new SparkJobTestException("TESTKIT_WORKSPACE_CLEANUP_FAILED",
                    "Unable to clean TestKit workspace", exception);
        }
    }

    private static boolean hasGeometry(StructType schema) {
        return Arrays.stream(schema.fields()).map(StructField::dataType).anyMatch(type -> {
            String name = type.typeName().toLowerCase(Locale.ROOT);
            return name.contains("geometry") || type.getClass().getName().toLowerCase(Locale.ROOT).contains("geometry");
        });
    }

    private static Set<String> targetColumns(StructType schema) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Arrays.stream(schema.fields()).map(StructField::name).forEach(result::add);
        return result;
    }

    private static void putMapping(Map<String, String> mappings, String target, String source) {
        target = required(target); source = required(source);
        if (mappings.putIfAbsent(target, source) != null) {
            throw error("TESTKIT_DUPLICATE_TARGET_MAPPING", "Target column cannot be mapped twice: " + target);
        }
    }

    private static List<String> normalizedNames(String[] values, String label) {
        if (values == null) return List.of();
        List<String> result = Arrays.stream(values).map(SparkJobTestContext::required).toList();
        if (new HashSet<>(result).size() != result.size()) {
            throw new IllegalArgumentException(label + " must not contain duplicates");
        }
        return result;
    }

    private static String required(String value) {
        return TestContextConfiguration.required(value, "name");
    }

    private static SparkJobTestException error(String code, String message) {
        return new SparkJobTestException(code, message);
    }

    private record RuntimeModelBinding(Dataset<Row> input, TestModelTarget target) { }
    private record RuntimeJdbcBinding(
            Map<JdbcTableIdentifier, Dataset<Row>> tables,
            Map<String, Dataset<Row>> queries,
            Map<JdbcTableIdentifier, StructType> targets
    ) { }

    private static final class Parameters implements SparkJobParameters {
        private final Map<String, String> values;
        private Parameters(Map<String, String> values) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
        @Override public Optional<String> find(String name) { return Optional.ofNullable(values.get(required(name))); }
        @Override public String require(String name) { return find(name).orElseThrow(() ->
                error("TESTKIT_PARAMETER_REQUIRED", "Required parameter is missing: " + name)); }
        @Override public Map<String, String> asMap() { return values; }
    }

    public static final class Builder {
        private final TestContextConfiguration.Builder delegate = new TestContextConfiguration.Builder();
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
        public SparkJobTestContext build() { return new SparkJobTestContext(delegate.build(), false); }
    }
}
