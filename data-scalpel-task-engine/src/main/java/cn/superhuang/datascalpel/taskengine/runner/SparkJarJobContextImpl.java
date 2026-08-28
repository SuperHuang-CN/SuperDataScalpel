package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.SparkJarExecutionPayload;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceAccessMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import cn.superhuang.data.scalpel.contract.execution.SparkStreamingJarExecutionPayload;
import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.JdbcUpsertColumn;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.datascalpel.sdk.*;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeJdbcConnection;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.api.java.function.ForeachPartitionFunction;
import org.apache.spark.sql.*;
import org.apache.spark.sql.Column;
import org.apache.spark.storage.StorageLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.superhuang.data.scalpel.contract.execution.UserJobObservabilitySnapshot;

import java.io.Serial;
import java.io.Serializable;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class SparkJarJobContextImpl implements SparkJobContext {
    private static final DialectRegistry DIALECTS = BuiltInDialects.registry();
    private final SparkSession spark;
    private final SparkJobIdentity identity;
    private final SparkJobParameters parameters;
    private final Map<String, SparkJarExecutionPayload.ResourceBinding> bindings;
    private final Map<UUID, RuntimeDataSource> runtimeSources;
    private final Map<UUID, MetadataModel> models;
    private final boolean streaming;
    private final AffectedRowsAccumulator affectedRows = new AffectedRowsAccumulator();
    private final ModelResources modelResources = new Models();
    private final JdbcResources jdbcResources = new Jdbc();
    private final UserJobObservabilityRuntime observability;
    private final SparkJarLineageRuntime lineage;

    SparkJarJobContextImpl(
            SparkSession spark,
            TaskExecutionManifest manifest,
            ObjectMapper objectMapper,
            Consumer<UserJobObservabilitySnapshot> observabilityPublisher
    ) {
        this.spark = Objects.requireNonNull(spark);
        SparkJarExecutionPayload payload = manifest.sparkJarJob();
        SparkStreamingJarExecutionPayload streamingPayload = manifest.streamingSparkJarJob();
        this.streaming = streamingPayload != null;
        if ((payload == null) == (streamingPayload == null)) {
            throw new IllegalArgumentException("Spark JAR 执行载荷无效");
        }
        this.identity = new Identity(
                manifest,
                streaming ? TaskTriggerType.STREAMING_START
                        : TaskTriggerType.valueOf(payload.triggerType().name()),
                streaming ? null : payload.scheduleId(),
                streaming ? null : payload.scheduledFireAt());
        LinkedHashMap<String, String> parameterMap = new LinkedHashMap<>();
        (streaming ? streamingPayload.parameters() : payload.parameters())
                .forEach(parameter -> parameterMap.put(parameter.name(), parameter.value()));
        this.parameters = new Parameters(parameterMap);
        LinkedHashMap<String, SparkJarExecutionPayload.ResourceBinding> bindingMap = new LinkedHashMap<>();
        (streaming ? streamingPayload.resourceBindings() : payload.resourceBindings()).forEach(binding -> {
            if (bindingMap.putIfAbsent(binding.bindingName(), binding) != null)
                throw new IllegalArgumentException("重复资源绑定名");
        });
        this.bindings = Collections.unmodifiableMap(bindingMap);
        this.runtimeSources = CanvasTaskExecutor.runtimeSources(manifest.runtimeDataSources());
        this.models = manifest.metadataSnapshot().models().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                MetadataModel::id, model -> model));
        this.observability = new UserJobObservabilityRuntime(
                spark, identity, objectMapper, observabilityPublisher);
        this.lineage = new SparkJarLineageRuntime(!streaming);
    }

    @Override public SparkSession spark() { return spark; }
    @Override public SparkJobIdentity identity() { return identity; }
    @Override public SparkJobParameters parameters() { return parameters; }
    @Override public ModelResources models() { return modelResources; }
    @Override public JdbcResources jdbc() { return jdbcResources; }
    @Override public JobObservability observability() { return observability; }
    UserJobObservabilityRuntime observabilityRuntime() { return observability; }
    Long affectedRows() { return affectedRows.value(); }
    cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence lineageEvidence(boolean succeeded) {
        return lineage.evidence(succeeded);
    }
    SparkJarExecutionPayload.ResourceBinding requireBinding(
            String name, SparkJarResourceType type, boolean read) {
        return binding(name, type, read);
    }
    RuntimeDataSource runtimeSource(UUID id) {
        RuntimeDataSource source = runtimeSources.get(id);
        if (source == null) {
            throw new RunnerExecutionException("SDK_RESOURCE_BINDING_UNAVAILABLE",
                    "绑定运行连接不存在", null);
        }
        return source;
    }

    private final class Models implements ModelResources {
        @Override
        public Dataset<Row> read(String bindingName, JdbcReadOptions options) {
            SparkJarExecutionPayload.ResourceBinding binding = binding(bindingName, SparkJarResourceType.MODEL, true);
            MetadataModel model = model(binding.resourceId());
            RuntimeDataSource source = modelRuntime(model.dataSourceId(), true);
            Dataset<Row> loaded = jdbcReader(source, options)
                    .option("dbtable", qualified(source, model.catalogName(), model.schemaName(), model.physicalTableName()))
                    .load();
            return lineage.modelInput(binding.bindingName(), model, loaded);
        }

        @Override
        public ModelWriteOperation write(String bindingName, Dataset<Row> source) {
            SparkJarExecutionPayload.ResourceBinding binding = binding(bindingName, SparkJarResourceType.MODEL, false);
            return new ModelWriter(binding.bindingName(), model(binding.resourceId()), source);
        }
    }

    private final class Jdbc implements JdbcResources {
        @Override
        public Dataset<Row> readTable(String bindingName, JdbcTableIdentifier table, JdbcReadOptions options) {
            SparkJarExecutionPayload.ResourceBinding binding = binding(bindingName, SparkJarResourceType.JDBC_DATA_SOURCE, true);
            RuntimeDataSource source = jdbcRuntime(binding.resourceId(), true);
            Dataset<Row> loaded = jdbcReader(source, options)
                    .option("dbtable", qualified(source, table.catalog(), table.schema(), table.table())).load();
            JdbcTableIdentifier resolved = JdbcTableIdentifier.of(
                    table.catalog() == null ? source.connection().catalogName() : table.catalog(),
                    table.schema() == null ? source.connection().schemaName() : table.schema(),
                    table.table());
            return lineage.jdbcTableInput(binding.bindingName(), binding.resourceId(), resolved, loaded);
        }

        @Override
        public Dataset<Row> readQuery(String bindingName, String sql, JdbcReadOptions options) {
            SparkJarExecutionPayload.ResourceBinding binding = binding(bindingName, SparkJarResourceType.JDBC_DATA_SOURCE, true);
            RuntimeDataSource source = jdbcRuntime(binding.resourceId(), true);
            String normalized;
            try { normalized = ReadOnlySelectQueryParser.parse(sql).sql(); }
            catch (IllegalArgumentException exception) {
                throw new RunnerExecutionException("SDK_JDBC_QUERY_NOT_READ_ONLY",
                        "JDBC Query 只允许单条 SELECT/WITH 只读语句", null);
            }
            Dataset<Row> loaded = jdbcReader(source, options)
                    .option("dbtable", "(" + normalized + ") datascalpel_sdk_query").load();
            return lineage.jdbcQueryInput(
                    binding.bindingName(), binding.resourceId(), normalized, loaded);
        }

        @Override
        public JdbcWriteOperation write(String bindingName, Dataset<Row> source) {
            SparkJarExecutionPayload.ResourceBinding binding = binding(bindingName, SparkJarResourceType.JDBC_DATA_SOURCE, false);
            return new JdbcWriter(binding.bindingName(), jdbcRuntime(binding.resourceId(), false), source);
        }
    }

    private final class ModelWriter implements ModelWriteOperation {
        private final String bindingName;
        private final MetadataModel model;
        private final Dataset<Row> source;
        private final LinkedHashMap<String, String> mappings = new LinkedHashMap<>();
        private ModelWriteMode mode = ModelWriteMode.APPEND;

        private ModelWriter(String bindingName, MetadataModel model, Dataset<Row> source) {
            this.bindingName = bindingName;
            this.model = model; this.source = Objects.requireNonNull(source, "source");
        }
        @Override public ModelWriteOperation mode(ModelWriteMode mode) { this.mode = Objects.requireNonNull(mode); return this; }
        @Override public ModelWriteOperation map(String target, String source) { putMapping(mappings, target, source); return this; }
        @Override public ModelWriteOperation mapSameName() {
            requireNoMappings(mappings);
            for (String column : source.columns()) putMapping(mappings, column, column);
            return this;
        }
        @Override public ModelWriteOperation checkSchema() {
            // Production validates the mapped write through Spark casts and the real target system.
            return this;
        }
        @Override public WriteResult execute() {
            return observedWrite("model", bindingName, mode.name(), model.code(), () -> {
                if (mode == ModelWriteMode.OVERWRITE && model.physicalTableMode() == MetadataModelPhysicalTableMode.EXTERNAL)
                    throw new RunnerExecutionException("SDK_MODEL_OVERWRITE_NOT_ALLOWED", "EXTERNAL 模型不允许 OVERWRITE", null);
                Map<String, CanvasColumnSchema> columns = model.columns().stream().collect(
                        java.util.stream.Collectors.toMap(CanvasColumnSchema::name, column -> column));
                requireMappings(mappings, columns.keySet(), source);
                List<String> required = model.columns().stream()
                        .filter(column -> !column.nullable() && column.defaultValue() == null
                                && !column.autoIncrement() && !column.generated())
                        .map(CanvasColumnSchema::name).toList();
                if (!mappings.keySet().containsAll(required))
                    throw new RunnerExecutionException("SDK_REQUIRED_TARGET_COLUMN_NOT_MAPPED", "模型必填字段未完成映射", null);
                List<Column> selected = mappings.entrySet().stream().map(entry -> org.apache.spark.sql.functions
                        .col(entry.getValue()).cast(SparkTypeMapper.toDataType(columns.get(entry.getKey())))
                        .as(entry.getKey())).toList();
                Dataset<Row> projected = source.select(selected.toArray(Column[]::new));
                requireStreamingWriteAllowed(mode.name(), hasGeometry(projected.schema()));
                RuntimeDataSource runtime = modelRuntime(model.dataSourceId(), false);
                TableIdentifier table = new TableIdentifier(model.catalogName(), model.schemaName(), model.physicalTableName());
                List<String> keys = model.uniqueKeys().stream().filter(key -> key.type() == MetadataUniqueKeyType.PRIMARY_KEY)
                        .findFirst().map(MetadataUniqueKey::columns).orElse(List.of());
                if (mode == ModelWriteMode.UPSERT && (keys.isEmpty() || !mappings.keySet().containsAll(keys)))
                    throw new RunnerExecutionException("UPSERT_KEY_NOT_MAPPED", "模型完整主键必须完成映射", null);
                SparkJarLineageRuntime.PreparedFlow flow = lineage.analyzeModelWrite(
                        bindingName, model, mode.name(), projected);
                WriteResult result = write(runtime, table, projected, mode.name(), keys);
                lineage.confirm(flow);
                return result;
            });
        }
    }

    private final class JdbcWriter implements JdbcWriteOperation {
        private final String bindingName;
        private final RuntimeDataSource runtime;
        private final Dataset<Row> source;
        private final LinkedHashMap<String, String> mappings = new LinkedHashMap<>();
        private cn.superhuang.datascalpel.sdk.JdbcWriteMode mode = cn.superhuang.datascalpel.sdk.JdbcWriteMode.APPEND;
        private JdbcTableIdentifier table;
        private List<String> keys = List.of();
        private JdbcWriter(String bindingName, RuntimeDataSource runtime, Dataset<Row> source) {
            this.bindingName = bindingName;
            this.runtime = runtime;
            this.source = Objects.requireNonNull(source);
        }
        @Override public JdbcWriteOperation table(JdbcTableIdentifier table) { this.table = Objects.requireNonNull(table); return this; }
        @Override public JdbcWriteOperation mode(cn.superhuang.datascalpel.sdk.JdbcWriteMode mode) { this.mode = Objects.requireNonNull(mode); return this; }
        @Override public JdbcWriteOperation upsertKeyColumns(String... values) {
            this.keys = values == null ? List.of() : Arrays.stream(values).map(SparkJarJobContextImpl::required).toList();
            if (new HashSet<>(keys).size() != keys.size()) throw new RunnerExecutionException("UPSERT_KEY_REQUIRED", "UPSERT Key 不能重复", null);
            return this;
        }
        @Override public JdbcWriteOperation map(String target, String source) { putMapping(mappings, target, source); return this; }
        @Override public WriteResult execute() {
            return observedWrite("jdbc", bindingName, mode.name(),
                    table == null ? "unconfigured" : table.toString(), () -> {
                if (table == null) {
                    throw new RunnerExecutionException(
                            "SDK_JDBC_TARGET_REQUIRED", "JDBC 目标表不能为空", null);
                }
                requireMappings(mappings, null, source);
                if (mode == cn.superhuang.datascalpel.sdk.JdbcWriteMode.UPSERT && (keys.isEmpty() || !mappings.keySet().containsAll(keys)))
                    throw new RunnerExecutionException("UPSERT_KEY_NOT_MAPPED", "JDBC UPSERT Key 必须全部完成映射", null);
                Dataset<Row> projected = source.select(mappings.entrySet().stream()
                        .map(entry -> org.apache.spark.sql.functions.col(entry.getValue()).as(entry.getKey()))
                        .toArray(Column[]::new));
                requireStreamingWriteAllowed(mode.name(), hasGeometry(projected.schema()));
                JdbcTableIdentifier resolved = JdbcTableIdentifier.of(
                        table.catalog() == null ? runtime.connection().catalogName() : table.catalog(),
                        table.schema() == null ? runtime.connection().schemaName() : table.schema(),
                        table.table());
                SparkJarLineageRuntime.PreparedFlow flow = lineage.analyzeJdbcWrite(
                        bindingName, runtime.dataSourceId(), resolved, mode.name(), projected);
                WriteResult result = write(runtime,
                        new TableIdentifier(resolved.catalog(), resolved.schema(), resolved.table()),
                        projected, mode.name(), keys);
                lineage.confirm(flow);
                return result;
            });
        }
    }

    private WriteResult observedWrite(
            String resourceKind,
            String bindingName,
            String mode,
            String target,
            java.util.function.Supplier<WriteResult> action
    ) {
        String prefix = "datascalpel." + resourceKind + ".write.";
        Map<String, String> attributes = Map.of(
                "binding", bindingName,
                "mode", mode,
                "target", target);
        observability.addPlatformCounter(prefix + "attempts", 1);
        observability.platformInfo(resourceKind + ".write.started", "SDK 写入开始", attributes);
        long startedNanos = System.nanoTime();
        try {
            WriteResult result = action.get();
            observability.addPlatformCounter(prefix + "successes", 1);
            if (result.affectedRows() != null) {
                observability.addPlatformCounter(prefix + "rows", result.affectedRows());
            }
            observability.platformInfo(resourceKind + ".write.succeeded", "SDK 写入成功", attributes);
            return result;
        } catch (RuntimeException | Error failure) {
            observability.platformError(resourceKind + ".write.failed", "SDK 写入失败", attributes);
            throw failure;
        } finally {
            observability.recordPlatformTimer(prefix + "duration",
                    TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedNanos)));
        }
    }

    private WriteResult write(RuntimeDataSource runtime, TableIdentifier requestedTable, Dataset<Row> dataset,
                              String mode, List<String> keys) {
        TableIdentifier table = new TableIdentifier(
                requestedTable.catalog() == null ? runtime.connection().catalogName() : requestedTable.catalog(),
                requestedTable.schema() == null ? runtime.connection().schemaName() : requestedTable.schema(),
                requestedTable.table());
        requireJdbcWriteSupported(runtime, mode, dataset.schema());
        Dataset<Row> cached = dataset.persist(StorageLevel.MEMORY_AND_DISK());
        try {
            long rows = cached.count();
            String qualified = DIALECTS.require(runtime.databaseType().name()).qualifiedName(table);
            if ("OVERWRITE".equals(mode)) CanvasTaskExecutor.truncate(runtime, qualified);
            if ("UPSERT".equals(mode)) {
                validateUpsertKeys(cached, keys);
                writeUpsert(runtime, table, cached, keys);
            } else {
                CanvasTaskExecutor.write(runtime, qualified, cached);
            }
            affectedRows.add(rows);
            return WriteResult.known(rows);
        } catch (RunnerExecutionException exception) { throw exception; }
        catch (Exception exception) { throw new RuntimeException(exception); }
        finally { cached.unpersist(); }
    }

    private static void requireJdbcWriteSupported(
            RuntimeDataSource runtime,
            String mode,
            org.apache.spark.sql.types.StructType schema
    ) {
        if ("OVERWRITE".equals(mode)) {
            CanvasTaskExecutor.requireOverwriteSupported(runtime);
        }
        if ("UPSERT".equals(mode)
                && runtime.databaseType() != cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType.POSTGRESQL
                && runtime.databaseType() != cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType.MYSQL) {
            throw new RunnerExecutionException(
                    "UPSERT_DATABASE_NOT_SUPPORTED",
                    "UPSERT 只支持 PostgreSQL 和 MySQL",
                    null
            );
        }
        if (hasGeometry(schema)
                && runtime.databaseType() != cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType.POSTGRESQL
                && runtime.databaseType() != cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType.MYSQL) {
            throw new RunnerExecutionException(
                    "SPATIAL_JDBC_UNSUPPORTED",
                    "Geometry JDBC 读写只支持 PostgreSQL/PostGIS 和 MySQL 8",
                    null
            );
        }
    }

    private void requireStreamingWriteAllowed(String mode, boolean geometry) {
        if (!streaming) return;
        if ("OVERWRITE".equals(mode)) {
            throw new RunnerExecutionException("STREAMING_SDK_OVERWRITE_NOT_ALLOWED",
                    "实时 JAR 的模型/JDBC 写入不允许 OVERWRITE", null);
        }
        if (geometry) {
            throw new RunnerExecutionException("STREAMING_SDK_GEOMETRY_NOT_ALLOWED",
                    "实时 JAR 的模型/JDBC 写入暂不支持 Geometry", null);
        }
    }

    private static boolean hasGeometry(org.apache.spark.sql.types.StructType schema) {
        return Arrays.stream(schema.fields()).anyMatch(field ->
                field.dataType().typeName().toLowerCase(Locale.ROOT).contains("geometry")
                        || field.dataType().getClass().getName().toLowerCase(Locale.ROOT).contains("geometry"));
    }

    private static void validateUpsertKeys(Dataset<Row> dataset, List<String> keys) {
        if (keys.stream().anyMatch(key -> Arrays.stream(dataset.columns()).noneMatch(key::equals)))
            throw new RunnerExecutionException("UPSERT_KEY_NOT_MAPPED", "UPSERT Key 未完成映射", null);
        Column nullCondition = keys.stream().map(key -> org.apache.spark.sql.functions.col(key).isNull())
                .reduce(Column::or).orElseThrow();
        if (dataset.filter(nullCondition).limit(1).count() > 0)
            throw new RunnerExecutionException("UPSERT_KEY_NULL", "UPSERT Key 不能包含 NULL", null);
        Column[] keyColumns = keys.stream().map(org.apache.spark.sql.functions::col).toArray(Column[]::new);
        if (dataset.groupBy(keyColumns).count().filter(org.apache.spark.sql.functions.col("count").gt(1)).limit(1).count() > 0)
            throw new RunnerExecutionException("UPSERT_DUPLICATE_KEY", "当前批次存在重复 UPSERT Key", null);
    }

    private static void writeUpsert(RuntimeDataSource runtime, TableIdentifier table,
                                    Dataset<Row> dataset, List<String> keys) {
        if (!"POSTGRESQL".equals(runtime.databaseType().name()) && !"MYSQL".equals(runtime.databaseType().name()))
            throw new RunnerExecutionException("UPSERT_DATABASE_NOT_SUPPORTED", "UPSERT 只支持 PostgreSQL 和 MySQL", null);
        DatabaseDialect dialect = DIALECTS.require(runtime.databaseType().name());
        List<JdbcUpsertColumn> columns = Arrays.stream(dataset.columns()).map(name -> new JdbcUpsertColumn(name, null)).toList();
        String sql = dialect.renderRowUpsert(table, columns, keys);
        RuntimeJdbcConnection connection = runtime.connection();
        dataset.foreachPartition((ForeachPartitionFunction<Row>) new PartitionWriter(
                connection.driverClassName(), connection.jdbcUrl(), jdbcProperties(connection), sql,
                dataset.schema().size()));
    }

    private static Properties jdbcProperties(RuntimeJdbcConnection connection) {
        Properties values = new Properties();
        if (connection.username() != null) values.setProperty("user", connection.username());
        if (connection.password() != null) values.setProperty("password", connection.password());
        connection.properties().forEach(values::setProperty);
        return values;
    }

    private SparkJarExecutionPayload.ResourceBinding binding(String name, SparkJarResourceType type, boolean read) {
        SparkJarExecutionPayload.ResourceBinding binding = bindings.get(required(name));
        if (binding == null) throw new RunnerExecutionException("SDK_RESOURCE_BINDING_NOT_FOUND", "资源绑定不存在：" + name, null);
        if (binding.resourceType() != type) throw new RunnerExecutionException("SDK_RESOURCE_BINDING_TYPE_MISMATCH", "资源绑定类型不匹配：" + name, null);
        SparkJarResourceAccessMode access = binding.accessMode();
        if (read && !access.canRead() || !read && !access.canWrite())
            throw new RunnerExecutionException("SDK_RESOURCE_ACCESS_DENIED", "资源绑定访问模式不允许当前操作：" + name, null);
        return binding;
    }

    private MetadataModel model(UUID id) {
        MetadataModel model = models.get(id);
        if (model == null) throw new RunnerExecutionException("SDK_MODEL_BINDING_UNAVAILABLE", "绑定模型快照不存在", null);
        return model;
    }

    private RuntimeDataSource requiredRuntime(UUID id) {
        RuntimeDataSource source = runtimeSources.get(id);
        if (source == null || source.connection() == null)
            throw new RunnerExecutionException("SDK_JDBC_BINDING_UNAVAILABLE", "绑定 JDBC 运行连接不存在", null);
        return source;
    }

    private RuntimeDataSource modelRuntime(UUID id, boolean read) {
        RuntimeDataSource source = requiredRuntime(id);
        boolean allowed = read
                ? source.purposes().contains(DataSourcePurpose.SOURCE)
                    || source.purposes().contains(DataSourcePurpose.STORAGE)
                : source.purposes().contains(DataSourcePurpose.STORAGE);
        if (!allowed) {
            throw new RunnerExecutionException("SDK_RESOURCE_ACCESS_DENIED",
                    read ? "模型底层数据源不允许读取" : "模型写入要求底层数据源具有 STORAGE 用途", null);
        }
        return source;
    }

    private RuntimeDataSource jdbcRuntime(UUID id, boolean read) {
        RuntimeDataSource source = requiredRuntime(id);
        DataSourcePurpose requiredPurpose = read ? DataSourcePurpose.SOURCE : DataSourcePurpose.DISTRIBUTION;
        if (!source.purposes().contains(requiredPurpose)) {
            throw new RunnerExecutionException("SDK_RESOURCE_ACCESS_DENIED",
                    read ? "JDBC 读取要求 SOURCE 用途" : "JDBC 写入要求 DISTRIBUTION 用途", null);
        }
        return source;
    }

    private static String qualified(RuntimeDataSource source, String catalog, String schema, String table) {
        return DIALECTS.require(source.databaseType().name()).qualifiedName(new TableIdentifier(
                catalog == null ? source.connection().catalogName() : catalog,
                schema == null ? source.connection().schemaName() : schema, table));
    }

    private DataFrameReader jdbcReader(RuntimeDataSource source, JdbcReadOptions options) {
        Objects.requireNonNull(options, "options");
        DataFrameReader reader = CanvasTaskExecutor.reader(spark, source);
        options.partitioning().ifPresent(partitioning -> {
            reader.option("partitionColumn", partitioning.column());
            reader.option("lowerBound", partitioning.lowerBound());
            reader.option("upperBound", partitioning.upperBound());
            reader.option("numPartitions", partitioning.numPartitions());
        });
        options.fetchSize().ifPresent(value -> reader.option("fetchsize", value));
        options.queryTimeoutSeconds().ifPresent(value -> reader.option("queryTimeout", value));
        options.options().forEach(reader::option);
        return reader;
    }

    private static void putMapping(Map<String, String> mappings, String target, String source) {
        target = required(target); source = required(source);
        if (mappings.putIfAbsent(target, source) != null)
            throw new RunnerExecutionException("SDK_DUPLICATE_TARGET_MAPPING", "目标字段不能重复映射：" + target, null);
    }

    private static void requireNoMappings(Map<String, String> mappings) {
        if (!mappings.isEmpty()) {
            throw new RunnerExecutionException("SDK_SAME_NAME_MAPPING_AFTER_EXPLICIT_MAPPING",
                    "mapSameName 不能与显式字段映射混用", null);
        }
    }

    private static void requireMappings(Map<String, String> mappings, Set<String> targets, Dataset<Row> source) {
        if (mappings.isEmpty()) throw new RunnerExecutionException("SDK_COLUMN_MAPPING_REQUIRED", "至少需要配置一个字段映射", null);
        Set<String> sourceColumns = Set.of(source.columns());
        mappings.forEach((target, sourceName) -> {
            if (targets != null && !targets.contains(target))
                throw new RunnerExecutionException("SDK_TARGET_COLUMN_NOT_FOUND", "目标字段不存在：" + target, null);
            if (!sourceColumns.contains(sourceName))
                throw new RunnerExecutionException("SDK_SOURCE_COLUMN_NOT_FOUND", "来源字段不存在：" + sourceName, null);
        });
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("名称不能为空");
        return value.trim();
    }

    private static final class Parameters implements SparkJobParameters {
        private final Map<String, String> values;
        private Parameters(Map<String, String> values) { this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values)); }
        @Override public Optional<String> find(String name) { return Optional.ofNullable(values.get(required(name))); }
        @Override public String require(String name) { return find(name).orElseThrow(() ->
                new RunnerExecutionException("SDK_PARAMETER_REQUIRED", "任务参数不存在：" + name, null)); }
        @Override public Map<String, String> asMap() { return values; }
    }

    private static final class Identity implements SparkJobIdentity {
        private final TaskExecutionManifest manifest;
        private final TaskTriggerType triggerType;
        private final UUID scheduleId;
        private final java.time.Instant scheduledFireAt;
        private Identity(TaskExecutionManifest manifest, TaskTriggerType triggerType,
                         UUID scheduleId, java.time.Instant scheduledFireAt) {
            this.manifest = manifest;
            this.triggerType = triggerType;
            this.scheduleId = scheduleId;
            this.scheduledFireAt = scheduledFireAt;
        }
        @Override public UUID taskId() { return manifest.execution().taskId(); }
        @Override public UUID runId() { return manifest.execution().runId(); }
        @Override public UUID executionId() { return manifest.execution().executionId(); }
        @Override public int attempt() { return manifest.execution().attempt(); }
        @Override public int definitionVersion() { return manifest.execution().definitionVersion(); }
        @Override public TaskTriggerType triggerType() { return triggerType; }
        @Override public Optional<UUID> scheduleId() { return Optional.ofNullable(scheduleId); }
        @Override public Optional<java.time.Instant> scheduledFireAt() { return Optional.ofNullable(scheduledFireAt); }
        @Override public Optional<UUID> deploymentId() {
            return Optional.ofNullable(manifest.execution().deploymentId());
        }
    }

    private static final class AffectedRowsAccumulator {
        private final AtomicLong total = new AtomicLong(); private final AtomicBoolean known = new AtomicBoolean(true);
        void add(long value) { if (known.get()) try { total.set(Math.addExact(total.get(), value)); } catch (ArithmeticException e) { known.set(false); } }
        Long value() { return known.get() ? total.get() : null; }
    }

    private static final class PartitionWriter implements ForeachPartitionFunction<Row>, Serializable {
        @Serial private static final long serialVersionUID = 1L;
        private final String driver; private final String url; private final Properties properties; private final String sql; private final int columns;
        private PartitionWriter(String driver, String url, Properties properties, String sql, int columns) {
            this.driver = driver; this.url = url; this.properties = properties; this.sql = sql; this.columns = columns;
        }
        @Override public void call(Iterator<Row> rows) throws Exception {
            if (!rows.hasNext()) return;
            Class.forName(driver);
            try (Connection connection = DriverManager.getConnection(url, properties);
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                boolean original = connection.getAutoCommit(); connection.setAutoCommit(false);
                try {
                    int batch = 0;
                    do {
                        Row row = rows.next();
                        for (int index = 0; index < columns; index++) statement.setObject(index + 1,
                                row.isNullAt(index) ? null : row.get(index));
                        statement.addBatch();
                        if (++batch == 1000) { statement.executeBatch(); batch = 0; }
                    } while (rows.hasNext());
                    if (batch > 0) statement.executeBatch();
                    connection.commit();
                } catch (Exception exception) { connection.rollback(); throw exception; }
                finally { try { connection.setAutoCommit(original); } catch (SQLException ignored) {} }
            }
        }
    }
}
