package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.*;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskResourceBindingRepository;
import cn.superhuang.data.scalpel.business.task.web.request.CreateSparkJarDevelopmentKitRequest;
import cn.superhuang.data.scalpel.contract.execution.SparkJarJobMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.model.*;
import cn.superhuang.data.scalpel.dialect.query.*;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseStandardQueryExecutor;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class SparkJarDevelopmentKitGenerator {
    static final long MAX_ROWS = 1_000_000;
    static final long MAX_ZIP_BYTES = 512L * 1024 * 1024;
    private static final int PAGE_SIZE = 1_000;
    private static final Duration QUERY_TIMEOUT = Duration.ofHours(1);

    private final SparkJarTaskDefinitionRepository definitionRepository;
    private final SparkJarTaskResourceBindingRepository bindingRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DatabaseStandardQueryExecutor queryExecutor;
    private final DialectRegistry dialectRegistry;
    private final DatabaseInspector databaseInspector;
    private final ObjectMapper objectMapper;

    public SparkJarDevelopmentKitGenerator(SparkJarTaskDefinitionRepository definitionRepository,
            SparkJarTaskResourceBindingRepository bindingRepository, DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository, DataSourceRepository dataSourceRepository,
            DatabaseStandardQueryExecutor queryExecutor, DialectRegistry dialectRegistry,
            DatabaseInspector databaseInspector, ObjectMapper objectMapper) {
        this.definitionRepository = definitionRepository; this.bindingRepository = bindingRepository;
        this.modelRepository = modelRepository; this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository; this.queryExecutor = queryExecutor;
        this.dialectRegistry = dialectRegistry; this.databaseInspector = databaseInspector; this.objectMapper = objectMapper;
    }

    public GeneratedKit generate(SparkJarDevelopmentKitJob job, Progress progress) throws IOException {
        SparkJarTaskDefinition definition = definitionRepository.findByTaskId(job.getTaskId())
                .orElseThrow(() -> failure("DEFINITION_NOT_FOUND", "Spark JAR 任务定义不存在"));
        if (definition.getJobMode() != SparkJarJobMode.BATCH)
            throw failure("DEFINITION_NOT_BATCH", "Spark JAR 任务不再是批处理任务");
        List<SparkJarTaskResourceBinding> bindings = bindingRepository.findAllByTaskIdOrderByCreatedAtAsc(job.getTaskId());
        Request request = readRequest(job.getRequestJson());
        Map<String, Sample> samples = request.samples().stream().collect(java.util.stream.Collectors.toMap(
                Sample::bindingName, value -> value, (left, right) -> { throw failure("DUPLICATE_SAMPLE", "输入绑定抽样配置重复"); }, LinkedHashMap::new));

        Path work = Files.createTempDirectory("datascalpel-spark-kit-");
        Path root = work.resolve("datascalpel-spark-job");
        try {
            Files.createDirectories(root.resolve("src/main/java/com/example/datascalpel"));
            Files.createDirectories(root.resolve("src/test/java/com/example/datascalpel"));
            Files.createDirectories(root.resolve("src/test/resources/samples"));
            List<ModelBundle> models = resolveModels(bindings.stream()
                    .filter(binding -> binding.getResourceType() == SparkJarResourceType.MODEL).toList());
            validateSampleNames(samples, models);
            List<JdbcTableBundle> jdbcTables = resolveJdbcTables(bindings, request.jdbcTables());
            List<Map<String, Object>> sampleMetadata = new ArrayList<>();
            List<Map<String, Object>> jdbcTableMetadata = new ArrayList<>();
            int inputIndex = 0;
            int inputCount = (int) models.stream().filter(ModelBundle::readable).count() + jdbcTables.size();
            for (ModelBundle model : models) {
                if (!model.readable()) continue;
                inputIndex++;
                progress.update(SparkJarDevelopmentKitStage.COUNTING, 5 + inputIndex * 10 / Math.max(1, inputCount), model.bindingName());
                Sample sample = samples.getOrDefault(model.bindingName(), new Sample(model.bindingName(), SparkJarDevelopmentKitSampleMode.NONE, null, null));
                long total = sample.mode() == SparkJarDevelopmentKitSampleMode.PERCENTAGE
                        || sample.mode() == SparkJarDevelopmentKitSampleMode.ALL ? count(model) : 0;
                long target = targetRows(sample, total);
                progress.update(SparkJarDevelopmentKitStage.EXPORTING, 15 + inputIndex * 55 / Math.max(1, inputCount), model.bindingName());
                Path parquet = root.resolve("src/test/resources/samples").resolve(safeFile(model.bindingName()) + ".parquet");
                long actual = writeParquet(model, target, parquet);
                sampleMetadata.add(new LinkedHashMap<>(Map.of(
                        "bindingName", model.bindingName(), "modelId", model.model().getId(),
                        "schemaVersion", model.model().getSchemaVersion(), "sampleMode", sample.mode(),
                        "requestedValue", sample.value(), "actualRows", actual,
                        "stableOrder", !model.primaryKeys().isEmpty(), "file", "src/test/resources/samples/" + parquet.getFileName(),
                        "sha256", sha256(parquet))));
            }
            for (JdbcTableBundle jdbcTable : jdbcTables) {
                inputIndex++;
                progress.update(SparkJarDevelopmentKitStage.COUNTING,
                        5 + inputIndex * 10 / Math.max(1, inputCount), jdbcTable.displayName());
                Sample sample = jdbcTable.sample().asSample();
                long total = sample.mode() == SparkJarDevelopmentKitSampleMode.PERCENTAGE
                        || sample.mode() == SparkJarDevelopmentKitSampleMode.ALL ? count(jdbcTable) : 0;
                long target = targetRows(sample, total, "JDBC 表");
                progress.update(SparkJarDevelopmentKitStage.EXPORTING,
                        15 + inputIndex * 55 / Math.max(1, inputCount), jdbcTable.displayName());
                Path parquet = root.resolve("src/test/resources/samples").resolve(jdbcFileName(jdbcTable));
                long actual = writeParquet(jdbcTable, target, parquet);
                Map<String, Object> sampleValue = new LinkedHashMap<>();
                sampleValue.put("inputType", "JDBC_TABLE"); sampleValue.put("bindingName", jdbcTable.bindingName());
                sampleValue.put("dataSourceCode", jdbcTable.source().getCode());
                sampleValue.put("table", tableMetadata(jdbcTable.table())); sampleValue.put("sampleMode", sample.mode());
                sampleValue.put("requestedValue", sample.value()); sampleValue.put("actualRows", actual);
                sampleValue.put("stableOrder", !jdbcTable.primaryKeys().isEmpty());
                sampleValue.put("file", "src/test/resources/samples/" + parquet.getFileName());
                sampleValue.put("sha256", sha256(parquet)); sampleMetadata.add(sampleValue);
                Map<String, Object> tableValue = jdbcTableMetadata(jdbcTable);
                tableValue.put("sampleMode", sample.mode()); tableValue.put("requestedValue", sample.value());
                tableValue.put("actualRows", actual); tableValue.put("stableOrder", !jdbcTable.primaryKeys().isEmpty());
                tableValue.put("file", "src/test/resources/samples/" + parquet.getFileName());
                tableValue.put("sha256", sampleValue.get("sha256")); jdbcTableMetadata.add(tableValue);
            }
            progress.update(SparkJarDevelopmentKitStage.PACKAGING, 75, null);
            writeText(root.resolve("pom.xml"), SparkJarTaskDefinitionService.templatePom(SparkJarJobMode.BATCH));
            writeText(root.resolve("README.md"), readme(models, jdbcTables));
            writeText(root.resolve("src/main/java/com/example/datascalpel/ExampleSparkJob.java"), jobSource(models, jdbcTables));
            writeText(root.resolve("src/test/java/com/example/datascalpel/ExampleSparkJobTest.java"), testSource(models, jdbcTables));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("taskId", job.getTaskId()); metadata.put("taskName", job.getTaskNameSnapshot());
            metadata.put("definitionVersion", job.getDefinitionVersion()); metadata.put("format", "PARQUET_SNAPPY");
            metadata.put("generatedAt", Instant.now()); metadata.put("samples", sampleMetadata);
            metadata.put("models", models.stream().map(this::modelMetadata).toList());
            metadata.put("jdbcTables", jdbcTableMetadata);
            writeText(root.resolve("datascalpel-development-kit.json"), objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata));
            verifyVersions(models, jdbcTables);
            Path zip = work.resolve("datascalpel-spark-job-development-kit.zip");
            zip(root, zip);
            long size = Files.size(zip);
            if (size > MAX_ZIP_BYTES) throw failure("ARTIFACT_SIZE_EXCEEDED", "开发包超过 512 MiB 安全上限");
            return new GeneratedKit(work, zip, size, sha256(zip));
        } catch (RuntimeException | IOException exception) {
            deleteRecursively(work); throw exception;
        }
    }

    private List<ModelBundle> resolveModels(List<SparkJarTaskResourceBinding> bindings) {
        List<ModelBundle> result = new ArrayList<>();
        for (SparkJarTaskResourceBinding binding : bindings) {
            DataModel model = modelRepository.findById(binding.getResourceId())
                    .orElseThrow(() -> failure("MODEL_NOT_FOUND", "绑定模型不存在：" + binding.getBindingName()));
            if (model.getStatus() != DataModelStatus.PUBLISHED)
                throw failure("MODEL_NOT_PUBLISHED", "绑定模型未发布：" + binding.getBindingName());
            List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
            if (fields.isEmpty()) throw failure("MODEL_SCHEMA_EMPTY", "绑定模型没有字段：" + binding.getBindingName());
            if (fields.stream().anyMatch(field -> field.getFieldType() == PlatformDataType.GEOMETRY))
                throw failure("GEOMETRY_NOT_SUPPORTED", "包含 Geometry 字段的模型暂不支持生成开发包：" + model.getName());
            if (fields.stream().anyMatch(field -> field.getFieldType() == PlatformDataType.DECIMAL
                    && (field.getPrecision() == null || field.getScale() == null)))
                throw failure("MODEL_SCHEMA_INVALID", "Decimal 字段缺少精度或小数位：" + model.getName());
            DataSource source = dataSourceRepository.findById(model.getStorageDataSourceId())
                    .orElseThrow(() -> failure("DATA_SOURCE_NOT_FOUND", "模型存储数据源不存在：" + model.getName()));
            if (!source.isEnabled() || !source.getType().isJdbc())
                throw failure("DATA_SOURCE_UNAVAILABLE", "模型存储数据源不可用：" + model.getName());
            result.add(new ModelBundle(binding, model, sampleFields(fields), source, model.getUpdatedAt(), source.getUpdatedAt()));
        }
        return result;
    }

    private List<JdbcTableBundle> resolveJdbcTables(List<SparkJarTaskResourceBinding> bindings,
            List<JdbcTableSample> requestedTables) {
        Map<String, SparkJarTaskResourceBinding> readableBindings = bindings.stream()
                .filter(binding -> binding.getResourceType() == SparkJarResourceType.JDBC_DATA_SOURCE)
                .filter(binding -> binding.getAccessMode().canRead())
                .collect(java.util.stream.Collectors.toMap(SparkJarTaskResourceBinding::getBindingName,
                        value -> value, (left, right) -> left, LinkedHashMap::new));
        Set<String> observed = new HashSet<>();
        List<JdbcTableBundle> result = new ArrayList<>();
        for (JdbcTableSample sample : requestedTables) {
            SparkJarTaskResourceBinding binding = readableBindings.get(sample.bindingName());
            if (binding == null) throw failure("INVALID_JDBC_BINDING", "JDBC 表不是可读取的数据源绑定：" + sample.bindingName());
            DataSource source = dataSourceRepository.findById(binding.getResourceId())
                    .orElseThrow(() -> failure("DATA_SOURCE_NOT_FOUND", "JDBC 数据源不存在：" + binding.getBindingName()));
            if (!source.isEnabled() || !source.getType().isJdbc() || !source.getPurposes().contains(DataSourcePurpose.SOURCE))
                throw failure("DATA_SOURCE_UNAVAILABLE", "JDBC 数据源不可用：" + binding.getBindingName());
            DatabaseDialect dialect = dialectRegistry.require(source.getType().name());
            TableIdentifier table = new TableIdentifier(
                    dialect.resolveCatalog(source.getConnection().toJdbcConnectionConfig(), sample.catalog()),
                    dialect.resolveSchema(source.getConnection().toJdbcConnectionConfig(), sample.schema()), sample.table());
            String key = sample.bindingName() + "\u0000" + table.catalog() + "\u0000" + table.schema() + "\u0000" + table.table();
            if (!observed.add(key)) throw failure("DUPLICATE_JDBC_TABLE", "JDBC 表声明重复：" + table.table());
            TableMetadata metadata = databaseInspector.readTable(source.getType().name(),
                    source.getConnection().toJdbcConnectionConfig(), table);
            List<SampleField> fields = jdbcFields(source, metadata, table);
            TableIdentifier resolvedTable = metadata.table() == null || metadata.table().identifier() == null
                    ? table : metadata.table().identifier();
            result.add(new JdbcTableBundle(binding, source, resolvedTable, fields, sample, source.getUpdatedAt()));
        }
        return result;
    }

    private List<SampleField> jdbcFields(DataSource source, TableMetadata metadata, TableIdentifier table) {
        if (metadata.columns().isEmpty()) throw failure("JDBC_TABLE_SCHEMA_EMPTY", "JDBC 表没有字段：" + table.table());
        Set<String> primaryKeys = new HashSet<>(metadata.primaryKey().columns());
        List<SampleField> fields = new ArrayList<>();
        for (ColumnMetadata column : metadata.columns()) {
            if (column.spatial() != null)
                throw failure("GEOMETRY_NOT_SUPPORTED", "JDBC 表包含 Geometry 字段，暂不支持生成开发包：" + table.table());
            TypeMappingResult<PlatformTypeDefinition> mapped = dialectRegistry.require(source.getType().name())
                    .mapToPlatformType(JdbcTypeDescriptor.from(column));
            if (mapped.quality() != TypeMappingQuality.EXACT || mapped.definition() == null)
                throw failure("JDBC_TYPE_NOT_EXACT", "JDBC 表字段类型无法精确映射：" + table.table() + "." + column.name());
            PlatformTypeDefinition definition = mapped.definition();
            if (definition.type() == PlatformDataType.GEOMETRY)
                throw failure("GEOMETRY_NOT_SUPPORTED", "JDBC 表包含 Geometry 字段，暂不支持生成开发包：" + table.table());
            fields.add(new SampleField(column.name(), column.name(), definition.type(), definition.length(),
                    definition.precision(), definition.scale(), column.nullable(), primaryKeys.contains(column.name()), column.ordinal()));
        }
        return fields.stream().sorted(Comparator.comparingInt(SampleField::sortOrder).thenComparing(SampleField::code)).toList();
    }

    private long count(SampledInput input) {
        StandardQuery query = query(input, 0, 1, true);
        return queryExecutor.count(input.source().getType().name(),
                input.source().getConnection().toJdbcConnectionConfig(), query, QUERY_TIMEOUT);
    }

    private long writeParquet(SampledInput input, long target, Path file) throws IOException {
        MessageType schema = parquetSchema(input.fields());
        SimpleGroupFactory groups = new SimpleGroupFactory(schema);
        AtomicLong written = new AtomicLong();
        try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(new org.apache.parquet.io.LocalOutputFile(file))
                .withType(schema).withCompressionCodec(CompressionCodecName.SNAPPY).build()) {
            for (int offset = 0; offset < target; offset += PAGE_SIZE) {
                int page = (int) Math.min(PAGE_SIZE, target - offset);
                queryExecutor.consume(input.source().getType().name(), input.source().getConnection().toJdbcConnectionConfig(),
                        query(input, offset, page, false), PAGE_SIZE, QUERY_TIMEOUT, row -> {
                            try { writer.write(group(groups, input.fields(), row)); written.incrementAndGet(); }
                            catch (IOException exception) { throw new ParquetWriteException(exception); }
                        });
            }
        } catch (ParquetWriteException exception) { throw exception.ioException; }
        return written.get();
    }

    private static StandardQuery query(SampledInput input, int offset, int limit, boolean count) {
        List<QueryProjection> projections = input.fields().stream().map(field -> new QueryProjection(field.code(), field.code())).toList();
        List<QueryOrder> orders = input.primaryKeys().stream().map(field ->
                new QueryOrder(field.code(), QueryOrderTarget.COLUMN, QuerySortDirection.ASC)).toList();
        return new StandardQuery(input.table(),
                projections, ConditionConjunction.AND, List.of(), List.of(), List.of(), orders, offset, limit, count);
    }

    private static MessageType parquetSchema(List<SampleField> fields) {
        return new MessageType("datascalpel_model_sample", fields.stream().map(SparkJarDevelopmentKitGenerator::parquetType).toList());
    }

    private static Type parquetType(SampleField field) {
        // Development-kit Parquet files are local input mocks rather than physical constraint definitions.
        // Spark widens Parquet file-source fields to nullable when reading, so keep the file schema aligned.
        Type.Repetition repetition = Type.Repetition.OPTIONAL;
        Types.PrimitiveBuilder<PrimitiveType> builder = switch (field.type()) {
            case BOOLEAN -> Types.primitive(PrimitiveType.PrimitiveTypeName.BOOLEAN, repetition);
            case BYTE -> Types.primitive(PrimitiveType.PrimitiveTypeName.INT32, repetition).as(LogicalTypeAnnotation.intType(8, true));
            case SHORT -> Types.primitive(PrimitiveType.PrimitiveTypeName.INT32, repetition).as(LogicalTypeAnnotation.intType(16, true));
            case INTEGER -> Types.primitive(PrimitiveType.PrimitiveTypeName.INT32, repetition);
            case LONG -> Types.primitive(PrimitiveType.PrimitiveTypeName.INT64, repetition);
            case FLOAT -> Types.primitive(PrimitiveType.PrimitiveTypeName.FLOAT, repetition);
            case DOUBLE -> Types.primitive(PrimitiveType.PrimitiveTypeName.DOUBLE, repetition);
            case DECIMAL -> Types.primitive(PrimitiveType.PrimitiveTypeName.BINARY, repetition)
                    .as(LogicalTypeAnnotation.decimalType(field.scale(), field.precision()));
            case STRING -> Types.primitive(PrimitiveType.PrimitiveTypeName.BINARY, repetition).as(LogicalTypeAnnotation.stringType());
            case BINARY -> Types.primitive(PrimitiveType.PrimitiveTypeName.BINARY, repetition);
            case DATE -> Types.primitive(PrimitiveType.PrimitiveTypeName.INT32, repetition).as(LogicalTypeAnnotation.dateType());
            case TIMESTAMP -> Types.primitive(PrimitiveType.PrimitiveTypeName.INT64, repetition)
                    .as(LogicalTypeAnnotation.timestampType(true, LogicalTypeAnnotation.TimeUnit.MICROS));
            case TIMESTAMP_NTZ -> Types.primitive(PrimitiveType.PrimitiveTypeName.INT64, repetition)
                    .as(LogicalTypeAnnotation.timestampType(false, LogicalTypeAnnotation.TimeUnit.MICROS));
            case GEOMETRY -> throw failure("GEOMETRY_NOT_SUPPORTED", "Geometry 字段暂不支持");
        };
        return builder.named(field.code());
    }

    private static Group group(SimpleGroupFactory factory, List<SampleField> fields, Map<String, Object> row) {
        Group group = factory.newGroup();
        for (SampleField field : fields) {
            Object value = row.get(field.code()); if (value == null) continue; String name = field.code();
            switch (field.type()) {
                case BOOLEAN -> group.append(name, value instanceof Boolean b ? b
                        : value instanceof Number number ? number.intValue() != 0 : Boolean.parseBoolean(value.toString()));
                case BYTE, SHORT, INTEGER -> group.append(name, ((Number)value).intValue());
                case LONG -> group.append(name, ((Number)value).longValue());
                case FLOAT -> group.append(name, ((Number)value).floatValue());
                case DOUBLE -> group.append(name, ((Number)value).doubleValue());
                case DECIMAL -> group.append(name, Binary.fromConstantByteArray(decimal(value).setScale(field.scale()).unscaledValue().toByteArray()));
                case STRING -> group.append(name, value.toString());
                case BINARY -> group.append(name, Binary.fromConstantByteArray(binary(value)));
                case DATE -> group.append(name, Math.toIntExact(date(value).toEpochDay()));
                case TIMESTAMP -> group.append(name, instant(value).getEpochSecond() * 1_000_000L + instant(value).getNano() / 1_000);
                case TIMESTAMP_NTZ -> group.append(name, localDateTime(value).toEpochSecond(ZoneOffset.UTC) * 1_000_000L + localDateTime(value).getNano() / 1_000);
                case GEOMETRY -> throw failure("GEOMETRY_NOT_SUPPORTED", "Geometry 字段暂不支持");
            }
        }
        return group;
    }

    private static long targetRows(Sample sample, long total) {
        return targetRows(sample, total, "模型");
    }

    private static long targetRows(Sample sample, long total, String subject) {
        long target = switch (sample.mode()) {
            case NONE -> 0;
            case ROW_COUNT -> Objects.requireNonNull(sample.rowCount());
            case PERCENTAGE -> Math.min(total, new BigDecimal(total).multiply(Objects.requireNonNull(sample.percentage()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).longValueExact());
            case ALL -> { if (total > MAX_ROWS) throw failure("MODEL_ROW_LIMIT_EXCEEDED", "全部数据超过单" + subject + " 1,000,000 行安全上限"); yield total; }
        };
        if (target > MAX_ROWS) throw failure("MODEL_ROW_LIMIT_EXCEEDED", "样例数据超过单" + subject + " 1,000,000 行安全上限");
        return target;
    }

    private Request readRequest(String json) {
        try { return objectMapper.readValue(json, Request.class); }
        catch (RuntimeException exception) { throw failure("INVALID_REQUEST_SNAPSHOT", "生成任务参数快照无效"); }
    }

    private static void validateSampleNames(Map<String, Sample> samples, List<ModelBundle> models) {
        Set<String> readable = models.stream().filter(ModelBundle::readable).map(ModelBundle::bindingName).collect(java.util.stream.Collectors.toSet());
        if (!readable.containsAll(samples.keySet())) throw failure("INVALID_SAMPLE_BINDING", "抽样配置包含非输入模型绑定");
    }

    private void verifyVersions(List<ModelBundle> models, List<JdbcTableBundle> jdbcTables) {
        for (ModelBundle item : models) {
            DataModel model = modelRepository.findById(item.model().getId()).orElseThrow();
            DataSource source = dataSourceRepository.findById(item.source().getId()).orElseThrow();
            if (model.getSchemaVersion() != item.model().getSchemaVersion() || !Objects.equals(model.getUpdatedAt(), item.modelUpdatedAt())
                    || !Objects.equals(source.getUpdatedAt(), item.sourceUpdatedAt()))
                throw failure("RESOURCE_CHANGED", "模型或数据源在生成过程中发生变化，请重新生成");
        }
        for (JdbcTableBundle item : jdbcTables) {
            DataSource source = dataSourceRepository.findById(item.source().getId()).orElseThrow();
            if (!Objects.equals(source.getUpdatedAt(), item.sourceUpdatedAt()))
                throw failure("RESOURCE_CHANGED", "JDBC 数据源在生成过程中发生变化，请重新生成");
            TableMetadata current = databaseInspector.readTable(source.getType().name(),
                    source.getConnection().toJdbcConnectionConfig(), item.table());
            if (!jdbcFields(source, current, item.table()).equals(item.fields()))
                throw failure("RESOURCE_CHANGED", "JDBC 表结构在生成过程中发生变化，请重新生成");
        }
    }

    private Map<String,Object> modelMetadata(ModelBundle item) {
        Map<String,Object> value = new LinkedHashMap<>(); value.put("bindingName", item.bindingName()); value.put("modelId", item.model().getId());
        value.put("modelCode", item.model().getCode()); value.put("schemaVersion", item.model().getSchemaVersion());
        value.put("accessMode", item.binding().getAccessMode()); value.put("external", item.model().getPhysicalTableMode() == PhysicalTableMode.EXTERNAL);
        value.put("primaryKeys", item.primaryKeys().stream().map(SampleField::code).toList());
        value.put("fields", item.fields().stream().map(SparkJarDevelopmentKitGenerator::fieldMetadata).toList()); return value;
    }

    private Map<String,Object> jdbcTableMetadata(JdbcTableBundle item) {
        Map<String,Object> value = new LinkedHashMap<>();
        value.put("bindingName", item.bindingName()); value.put("dataSourceCode", item.source().getCode());
        value.put("accessMode", item.binding().getAccessMode()); value.put("table", tableMetadata(item.table()));
        value.put("primaryKeys", item.primaryKeys().stream().map(SampleField::code).toList());
        value.put("fields", item.fields().stream().map(SparkJarDevelopmentKitGenerator::fieldMetadata).toList());
        return value;
    }

    private static String readme(List<ModelBundle> models, List<JdbcTableBundle> jdbcTables) {
        StringBuilder warnings = new StringBuilder(); models.stream().filter(ModelBundle::readable).filter(m -> m.primaryKeys().isEmpty())
                .forEach(m -> warnings.append("- 输入绑定 `").append(m.bindingName()).append("` 的模型没有主键，样例顺序不稳定。\n"));
        jdbcTables.stream().filter(table -> table.primaryKeys().isEmpty()).forEach(table -> warnings
                .append("- JDBC 表 `").append(table.bindingName()).append(":").append(table.displayName())
                .append("` 没有主键，样例顺序不稳定。\n"));
        return SparkJarTaskDefinitionService.templateReadme(SparkJarJobMode.BATCH)
                + "\n## 本开发包\n\n样例固定为 Snappy Parquet。作为本地输入 Mock，Parquet 和输入 StructType 的字段均允许 null；"
                + "模型原始 nullable 仍记录在元数据中，输出 Target 继续按模型约束校验。JDBC 表的 SDK 参数始终是资源绑定名，不是数据源编码。\n\n"
                + "当处理结果与输出模型字段同名时，可以使用 `mapSameName()` 代替逐字段 `map(...)`。随后调用 `checkSchema()`，"
                + "TestKit 会在本地比较字段集合和 Spark 类型（忽略顺序、nullable 与 Metadata）；生产运行不做此前置检查，"
                + "仍由 Spark 和实际目标系统处理类型转换与约束。\n\n"
                + warnings;
    }

    private static String jobSource(List<ModelBundle> models, List<JdbcTableBundle> jdbcTables) {
        StringBuilder body = new StringBuilder("package com.example.datascalpel;\n\nimport cn.superhuang.datascalpel.sdk.JdbcReadOptions;\nimport cn.superhuang.datascalpel.sdk.SparkBatchJob;\nimport cn.superhuang.datascalpel.sdk.SparkJobContext;\nimport org.apache.spark.sql.Dataset;\nimport org.apache.spark.sql.Row;\n\npublic final class ExampleSparkJob implements SparkBatchJob {\n  @Override public void execute(SparkJobContext context) {\n    // Source partitioning is configured in user code, for example:\n    // var options = JdbcReadOptions.builder().partitionBy(\"id\", \"1\", \"10000000\", 16).fetchSize(10_000).build();\n    // Dataset<Row> partitioned = context.models().read(\"input_binding\", options);\n");
        if (!jdbcTables.isEmpty()) body.append("    // SQL pushdown supports the same options; the partition column must be selected by the query:\n")
                .append("    // Dataset<Row> pushedDown = context.jdbc().readQuery(\"")
                .append(javaString(jdbcTables.getFirst().bindingName()))
                .append("\", \"SELECT id, amount FROM source_table\", options);\n");
        models.stream().filter(ModelBundle::readable).forEach(m -> body.append("    Dataset<Row> ").append(javaName("model_" + m.bindingName())).append(" = context.models().read(\"").append(javaString(m.bindingName())).append("\");\n"));
        jdbcTables.forEach(table -> body.append("    Dataset<Row> ").append(javaName("jdbc_" + table.javaKey())).append(" = ")
                .append(jdbcReadCall(table)).append(";\n"));
        models.stream().filter(ModelBundle::writable).forEach(m -> body.append("    // TODO 将处理后的 Dataset 写入输出绑定 ").append(m.bindingName()).append(": context.models().write(\"").append(javaString(m.bindingName())).append("\", dataset).mapSameName().checkSchema().execute();\n"));
        return body.append("  }\n}\n").toString();
    }

    private static String testSource(List<ModelBundle> models, List<JdbcTableBundle> jdbcTables) {
        StringBuilder text = new StringBuilder("package com.example.datascalpel;\n\nimport cn.superhuang.datascalpel.sdk.testkit.*;\nimport org.apache.spark.sql.types.*;\nimport org.junit.jupiter.api.Test;\nimport java.nio.file.Path;\n\nclass ExampleSparkJobTest {\n");
        models.stream().filter(ModelBundle::readable).forEach(model -> text.append("  private static final StructType ")
                .append(inputSchemaName(model)).append(" = new StructType()\n")
                .append(schemaSource(model.fields(), true)).append(";\n"));
        models.stream().filter(ModelBundle::writable).forEach(model -> text.append("  private static final StructType ")
                .append(outputSchemaName(model)).append(" = new StructType()\n")
                .append(schemaSource(model.fields(), false)).append(";\n"));
        for (JdbcTableBundle table : jdbcTables) text.append("  private static final StructType ")
                .append(jdbcInputSchemaName(table)).append(" = new StructType()\n")
                .append(schemaSource(table.fields(), true)).append(";\n");
        text.append("  @Test void runsWithGeneratedModelBindings() throws Exception {\n    var builder = SparkJobTestContext.builder();\n");
        models.stream().filter(ModelBundle::readable).forEach(m -> text.append("    builder.modelInputParquet(\"").append(javaString(m.bindingName())).append("\", Path.of(\"src/test/resources/samples/").append(safeFile(m.bindingName())).append(".parquet\"), ").append(inputSchemaName(m)).append(");\n"));
        models.stream().filter(ModelBundle::writable).forEach(m -> text.append("    builder.modelOutput(\"").append(javaString(m.bindingName())).append("\", TestModelTarget.builder(").append(outputSchemaName(m)).append(")")
                .append(stringArrayCall("primaryKeyColumns", m.primaryKeys().stream().map(SampleField::code).toList()))
                .append(stringArrayCall("omittableColumns", m.fields().stream().filter(SampleField::nullable).map(SampleField::code).toList()))
                .append(".external(").append(m.model().getPhysicalTableMode() == PhysicalTableMode.EXTERNAL).append(").build());\n"));
        jdbcTables.forEach(table -> text.append("    builder.jdbcTableParquet(\"").append(javaString(table.bindingName())).append("\", ")
                .append(jdbcTableIdentifierSource(table.table())).append(", Path.of(\"src/test/resources/samples/")
                .append(jdbcFileName(table)).append("\"), ").append(jdbcInputSchemaName(table)).append(");\n"));
        return text.append("    try (SparkJobTestContext context = builder.build()) { new ExampleSparkJob().execute(context); }\n  }\n}\n").toString();
    }

    private static String schemaSource(List<SampleField> fields, boolean forceNullable) { StringBuilder result = new StringBuilder(); for (SampleField f : fields) result.append("      .add(\"").append(javaString(f.code())).append("\", ").append(sparkType(f)).append(", ").append(forceNullable || f.nullable()).append(")\n"); return result.toString(); }
    private static String inputSchemaName(ModelBundle model) { return constName("model_" + model.bindingName() + "_input"); }
    private static String outputSchemaName(ModelBundle model) { return constName("model_" + model.bindingName() + "_output"); }
    private static String jdbcInputSchemaName(JdbcTableBundle table) { return constName("jdbc_" + table.javaKey() + "_input"); }
    private static String sparkType(SampleField f) { return switch(f.type()) { case BOOLEAN->"DataTypes.BooleanType";case BYTE->"DataTypes.ByteType";case SHORT->"DataTypes.ShortType";case INTEGER->"DataTypes.IntegerType";case LONG->"DataTypes.LongType";case FLOAT->"DataTypes.FloatType";case DOUBLE->"DataTypes.DoubleType";case DECIMAL->"DataTypes.createDecimalType("+f.precision()+", "+f.scale()+")";case STRING->"DataTypes.StringType";case BINARY->"DataTypes.BinaryType";case DATE->"DataTypes.DateType";case TIMESTAMP->"DataTypes.TimestampType";case TIMESTAMP_NTZ->"DataTypes.TimestampNTZType";case GEOMETRY->throw failure("GEOMETRY_NOT_SUPPORTED","Geometry 字段暂不支持");}; }
    private static String stringArrayCall(String method, List<String> values) { return values.isEmpty()?"":"."+method+"("+values.stream().map(v->"\""+javaString(v)+"\"").collect(java.util.stream.Collectors.joining(", "))+")"; }
    private static Map<String,Object> fieldMetadata(SampleField field){Map<String,Object> value=new LinkedHashMap<>();value.put("code",field.code());value.put("name",field.name());value.put("type",field.type());value.put("length",field.length());value.put("precision",field.precision());value.put("scale",field.scale());value.put("nullable",field.nullable());value.put("primaryKey",field.primaryKey());value.put("sortOrder",field.sortOrder());return value;}
    private static List<SampleField> sampleFields(List<DataModelField> fields) { return fields.stream().map(field -> new SampleField(
            field.getCode(), field.getName(), field.getFieldType(), field.getLength(), field.getPrecision(), field.getScale(),
            field.isNullable(), field.isPrimaryKey(), field.getSortOrder())).toList(); }
    private static Map<String,Object> tableMetadata(TableIdentifier table) { Map<String,Object> value = new LinkedHashMap<>(); value.put("catalog", table.catalog()); value.put("schema", table.schema()); value.put("name", table.table()); return value; }
    private static String jdbcFileName(JdbcTableBundle table) { String identity = table.table().catalog() + "." + table.table().schema() + "." + table.table().table(); return safeFile(table.bindingName()) + "-" + safeFile(table.table().table()) + "-" + Integer.toUnsignedString(identity.hashCode(), 36) + ".parquet"; }
    private static String jdbcReadCall(JdbcTableBundle table) { String binding = "\"" + javaString(table.bindingName()) + "\""; TableIdentifier id = table.table(); if (id.catalog() != null) return "context.jdbc().readTable(" + binding + ", " + jdbcTableIdentifierSource(id) + ")"; if (id.schema() != null) return "context.jdbc().readTable(" + binding + ", \"" + javaString(id.schema()) + "\", \"" + javaString(id.table()) + "\")"; return "context.jdbc().readTable(" + binding + ", \"" + javaString(id.table()) + "\")"; }
    private static String jdbcTableIdentifierSource(TableIdentifier id) { return "cn.superhuang.datascalpel.sdk.JdbcTableIdentifier.of(" + javaLiteral(id.catalog()) + ", " + javaLiteral(id.schema()) + ", \"" + javaString(id.table()) + "\")"; }
    private static String javaLiteral(String value) { return value == null ? "null" : "\"" + javaString(value) + "\""; }
    private static String safeFile(String s){String n=s.replaceAll("[^A-Za-z0-9._-]","_");return n.equals(s)?n:n+"-"+Integer.toUnsignedString(s.hashCode(),36);}
    private static String javaName(String s){String n=s.replaceAll("[^A-Za-z0-9_$]","_");if(!Character.isJavaIdentifierStart(n.charAt(0)))n="_"+n;return n.equals(s)?n:n+"_"+Integer.toUnsignedString(s.hashCode(),36);}
    private static String constName(String s){return javaName(s).toUpperCase(Locale.ROOT)+"_SCHEMA";} private static String javaString(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
    private static void writeText(Path path,String value)throws IOException{Files.writeString(path,value,StandardCharsets.UTF_8);}
    private static void zip(Path root,Path target)throws IOException{try(ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(target))){try(var paths=Files.walk(root)){for(Path p:paths.filter(Files::isRegularFile).sorted().toList()){String name=root.getParent().relativize(p).toString().replace('\\','/');out.putNextEntry(new ZipEntry(name));Files.copy(p,out);out.closeEntry();}}}}
    static void deleteRecursively(Path path){if(path==null||!Files.exists(path))return;try(var paths=Files.walk(path)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}catch(IOException ignored){}}
    private static String sha256(Path path)throws IOException{try{MessageDigest d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}catch(java.security.GeneralSecurityException e){throw new IllegalStateException(e);}}
    private static BigDecimal decimal(Object v){return v instanceof BigDecimal b?b:new BigDecimal(v.toString());} private static LocalDate date(Object v){if(v instanceof LocalDate d)return d;if(v instanceof java.sql.Date d)return d.toLocalDate();return LocalDate.parse(v.toString());}
    private static Instant instant(Object v){if(v instanceof Instant i)return i;if(v instanceof Timestamp t)return t.toInstant();if(v instanceof OffsetDateTime d)return d.toInstant();if(v instanceof LocalDateTime d)return d.toInstant(ZoneOffset.UTC);return Instant.parse(v.toString());}
    private static LocalDateTime localDateTime(Object v){if(v instanceof LocalDateTime d)return d;if(v instanceof Timestamp t)return t.toLocalDateTime();return LocalDateTime.parse(v.toString());}
    private static byte[] binary(Object value){if(value instanceof byte[] bytes)return bytes;if(value instanceof java.sql.Blob blob){try{return blob.getBytes(1,Math.toIntExact(blob.length()));}catch(java.sql.SQLException exception){throw failure("BINARY_READ_FAILED","无法读取二进制字段");}}throw failure("BINARY_READ_FAILED","数据库返回了不支持的二进制字段类型");}
    private static KitGenerationException failure(String code,String message){return new KitGenerationException(code,message,false);}

    public interface Progress { void update(SparkJarDevelopmentKitStage stage,int percent,String currentModel); }
    public record GeneratedKit(Path workspace,Path zip,long size,String sha256) implements AutoCloseable { @Override public void close(){deleteRecursively(workspace);} }
    public record Request(List<Sample> samples,List<JdbcTableSample> jdbcTables){public Request{samples=samples==null?List.of():List.copyOf(samples);jdbcTables=jdbcTables==null?List.of():List.copyOf(jdbcTables);}}
    public record Sample(String bindingName,SparkJarDevelopmentKitSampleMode mode,Integer rowCount,BigDecimal percentage){Object value(){return switch(mode){case NONE,ALL->mode.name();case ROW_COUNT->rowCount;case PERCENTAGE->percentage;};}}
    public record JdbcTableSample(String bindingName,String catalog,String schema,String table,SparkJarDevelopmentKitSampleMode mode,Integer rowCount,BigDecimal percentage){Sample asSample(){return new Sample(bindingName,mode,rowCount,percentage);}}
    public static final class KitGenerationException extends RuntimeException { private final String code; private final boolean retryable; KitGenerationException(String code,String message,boolean retryable){super(message);this.code=code;this.retryable=retryable;} public String code(){return code;} public boolean retryable(){return retryable;} }
    private interface SampledInput { DataSource source(); List<SampleField> fields(); TableIdentifier table(); List<SampleField> primaryKeys(); }
    private record SampleField(String code,String name,PlatformDataType type,Integer length,Integer precision,Integer scale,boolean nullable,boolean primaryKey,int sortOrder) { }
    private record ModelBundle(SparkJarTaskResourceBinding binding,DataModel model,List<SampleField> fields,DataSource source,Instant modelUpdatedAt,Instant sourceUpdatedAt) implements SampledInput {boolean readable(){return binding.getAccessMode().canRead();}boolean writable(){return binding.getAccessMode().canWrite();}String bindingName(){return binding.getBindingName();}public TableIdentifier table(){return new TableIdentifier(model.getCatalogName(),model.getSchemaName(),model.getPhysicalTableName());}public List<SampleField> primaryKeys(){return fields.stream().filter(SampleField::primaryKey).toList();}}
    private record JdbcTableBundle(SparkJarTaskResourceBinding binding,DataSource source,TableIdentifier table,List<SampleField> fields,JdbcTableSample sample,Instant sourceUpdatedAt) implements SampledInput {String bindingName(){return binding.getBindingName();}public List<SampleField> primaryKeys(){return fields.stream().filter(SampleField::primaryKey).toList();}String displayName(){return java.util.stream.Stream.of(table.catalog(),table.schema(),table.table()).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining("."));}String javaKey(){return bindingName()+"_"+displayName();}}
    private static final class ParquetWriteException extends RuntimeException { final IOException ioException; ParquetWriteException(IOException e){super(e);ioException=e;} }
}
