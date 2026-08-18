package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.SparkJarTaskResourceBinding;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskResourceBindingRepository;
import cn.superhuang.data.scalpel.contract.execution.*;
import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SparkJarTaskRunPreparationService {
    private final SparkJarTaskResourceBindingRepository bindingRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DialectRegistry dialectRegistry;
    private final ObjectMapper objectMapper;

    public SparkJarTaskRunPreparationService(
            SparkJarTaskResourceBindingRepository bindingRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            DialectRegistry dialectRegistry,
            ObjectMapper objectMapper
    ) {
        this.bindingRepository = bindingRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dialectRegistry = dialectRegistry;
        this.objectMapper = objectMapper;
    }

    public Preparation prepare(SparkJarTaskDefinition definition, SparkJarExecutionPayload.TriggerType triggerType,
                               UUID scheduleId, java.time.Instant scheduledFireAt) {
        List<SparkJarTaskResourceBinding> bindings = bindingRepository
                .findAllByTaskIdOrderByCreatedAtAsc(definition.getTaskId());
        Set<UUID> modelIds = bindings.stream().filter(binding -> binding.getResourceType() == SparkJarResourceType.MODEL)
                .map(SparkJarTaskResourceBinding::getResourceId).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, DataModel> models = modelRepository.findAllById(modelIds).stream()
                .collect(Collectors.toMap(DataModel::getId, model -> model));
        if (models.size() != modelIds.size()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 绑定模型已不存在");

        Set<UUID> sourceIds = bindings.stream().filter(binding -> binding.getResourceType() == SparkJarResourceType.JDBC_DATA_SOURCE)
                .map(SparkJarTaskResourceBinding::getResourceId).collect(Collectors.toCollection(LinkedHashSet::new));
        models.values().forEach(model -> sourceIds.add(model.getStorageDataSourceId()));
        Map<UUID, DataSource> sources = dataSourceRepository.findAllById(sourceIds).stream()
                .collect(Collectors.toMap(DataSource::getId, source -> source));
        if (sources.size() != sourceIds.size()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 绑定数据源已不存在");

        List<MetadataModel> metadataModels = models.values().stream().sorted(Comparator.comparing(DataModel::getId))
                .map(this::metadataModel).toList();
        List<CanvasTaskRunManifest.RuntimeDataSource> runtimeSources = sources.values().stream()
                .sorted(Comparator.comparing(DataSource::getId)).map(this::runtimeDataSource).toList();
        List<MetadataDataSource> metadataSources = sources.values().stream().sorted(Comparator.comparing(DataSource::getId))
                .map(source -> new MetadataDataSource(
                        source.getId(), source.isEnabled(), ConnectionKind.JDBC,
                        source.getPurposes().stream().map(purpose -> cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.valueOf(purpose.name()))
                                .collect(Collectors.toUnmodifiableSet()), List.of())).toList();
        SparkJarExecutionPayload payload = new SparkJarExecutionPayload(
                definition.getJobApiVersion(), definition.getJobClass(),
                parseEntries(definition.getParametersJson()).stream()
                        .map(entry -> new SparkJarExecutionPayload.Parameter(entry.name(), entry.value())).toList(),
                parseEntries(definition.getSparkConfJson()).stream()
                        .map(entry -> new SparkConfigurationEntry(entry.name(), entry.value())).toList(),
                bindings.stream().map(binding -> new SparkJarExecutionPayload.ResourceBinding(
                        binding.getBindingName(), binding.getResourceType(), binding.getResourceId(), binding.getAccessMode())).toList(),
                triggerType, scheduleId, scheduledFireAt);
        return new Preparation(new MetadataSnapshot(metadataSources, metadataModels), runtimeSources, payload,
                sources.values().stream().collect(Collectors.toUnmodifiableMap(DataSource::getId, DataSource::getUpdatedAt)),
                models.values().stream().collect(Collectors.toUnmodifiableMap(DataModel::getId, DataModel::getUpdatedAt)));
    }

    public StreamingPreparation prepareStreaming(
            SparkJarTaskDefinition definition,
            cn.superhuang.data.scalpel.business.task.domain.TaskStreamingDeployment deployment
    ) {
        List<SparkJarTaskResourceBinding> bindings = bindingRepository
                .findAllByTaskIdOrderByCreatedAtAsc(definition.getTaskId());
        Set<UUID> modelIds = bindings.stream()
                .filter(binding -> binding.getResourceType() == SparkJarResourceType.MODEL)
                .map(SparkJarTaskResourceBinding::getResourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, DataModel> models = modelRepository.findAllById(modelIds).stream()
                .collect(Collectors.toMap(DataModel::getId, model -> model));
        if (models.size() != modelIds.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark Streaming JAR 绑定模型已不存在");
        }

        Set<UUID> sourceIds = bindings.stream()
                .filter(binding -> binding.getResourceType() != SparkJarResourceType.MODEL)
                .map(SparkJarTaskResourceBinding::getResourceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        models.values().forEach(model -> sourceIds.add(model.getStorageDataSourceId()));
        Map<UUID, DataSource> sources = dataSourceRepository.findAllById(sourceIds).stream()
                .collect(Collectors.toMap(DataSource::getId, source -> source));
        if (sources.size() != sourceIds.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark Streaming JAR 绑定数据源已不存在");
        }

        List<MetadataModel> metadataModels = models.values().stream()
                .sorted(Comparator.comparing(DataModel::getId)).map(this::metadataModel).toList();
        List<CanvasTaskRunManifest.RuntimeDataSource> runtimeSources = sources.values().stream()
                .sorted(Comparator.comparing(DataSource::getId))
                .map(source -> source.getType() == cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType.KAFKA
                        ? kafkaRuntimeDataSource(source, bindings) : runtimeDataSource(source))
                .toList();
        List<MetadataDataSource> metadataSources = sources.values().stream()
                .sorted(Comparator.comparing(DataSource::getId))
                .map(source -> new MetadataDataSource(
                        source.getId(), source.isEnabled(),
                        source.getType() == cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType.KAFKA
                                ? ConnectionKind.KAFKA : ConnectionKind.JDBC,
                        source.getPurposes().stream().map(purpose ->
                                cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.valueOf(purpose.name()))
                                .collect(Collectors.toUnmodifiableSet()),
                        List.of()))
                .toList();
        SparkStreamingJarExecutionPayload payload = new SparkStreamingJarExecutionPayload(
                definition.getJobApiVersion(), definition.getJobClass(),
                parseEntries(definition.getParametersJson()).stream()
                        .map(entry -> new SparkJarExecutionPayload.Parameter(entry.name(), entry.value())).toList(),
                parseEntries(definition.getSparkConfJson()).stream()
                        .map(entry -> new SparkConfigurationEntry(entry.name(), entry.value())).toList(),
                bindings.stream().map(binding -> new SparkJarExecutionPayload.ResourceBinding(
                        binding.getBindingName(), binding.getResourceType(), binding.getResourceId(),
                        binding.getTopicName(), binding.getAccessMode())).toList(),
                definition.getTimeoutSeconds(), deployment.getId(), deployment.getCheckpointKeyPrefix(),
                deployment.getCheckpointStartMode(), deployment.getCheckpointSourceDeploymentId());
        return new StreamingPreparation(
                new MetadataSnapshot(metadataSources, metadataModels), runtimeSources, payload,
                sources.values().stream().collect(Collectors.toUnmodifiableMap(
                        DataSource::getId, DataSource::getUpdatedAt)),
                models.values().stream().collect(Collectors.toUnmodifiableMap(
                        DataModel::getId, DataModel::getUpdatedAt)));
    }

    public void assertUnchanged(Preparation preparation) {
        assertUnchanged(preparation.dataSourceVersions(), preparation.modelVersions());
    }

    public void assertUnchanged(StreamingPreparation preparation) {
        assertUnchanged(preparation.dataSourceVersions(), preparation.modelVersions());
    }

    private void assertUnchanged(Map<UUID, java.time.Instant> dataSourceVersions,
                                 Map<UUID, java.time.Instant> modelVersions) {
        dataSourceVersions.forEach((id, version) -> {
            if (!dataSourceRepository.findById(id).map(DataSource::getUpdatedAt).filter(version::equals).isPresent())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 绑定数据源已变化，请重新运行");
        });
        modelVersions.forEach((id, version) -> {
            if (!modelRepository.findById(id).map(DataModel::getUpdatedAt).filter(version::equals).isPresent())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 绑定模型已变化，请重新运行");
        });
    }

    private MetadataModel metadataModel(DataModel model) {
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
        List<CanvasColumnSchema> columns = fields.stream().map(field -> new CanvasColumnSchema(
                field.getCode(), field.getFieldType(), field.getLength(), field.getPrecision(), field.getScale(),
                field.isNullable(), null, false, false, field.getDescription(), field.getGeometry())).toList();
        List<String> keys = fields.stream().filter(DataModelField::isPrimaryKey)
                .sorted(Comparator.comparingInt(DataModelField::getSortOrder)).map(DataModelField::getCode).toList();
        List<MetadataUniqueKey> uniqueKeys = keys.isEmpty() ? List.of()
                : List.of(new MetadataUniqueKey("MODEL_PRIMARY_KEY", MetadataUniqueKeyType.PRIMARY_KEY, keys));
        return new MetadataModel(model.getId(), model.getCode(), model.getName(), model.getSchemaVersion(),
                MetadataModelStatus.valueOf(model.getStatus().name()),
                MetadataModelPhysicalTableMode.valueOf(model.getPhysicalTableMode().name()),
                model.getStorageDataSourceId(), model.getCatalogName(), model.getSchemaName(),
                model.getPhysicalTableName(), columns, uniqueKeys,
                fields.stream().sorted(Comparator.comparingInt(DataModelField::getSortOrder))
                        .map(field -> new MetadataModelField(field.getId(), field.getCode(), field.getName(), field.getSortOrder())).toList());
    }

    private CanvasTaskRunManifest.RuntimeDataSource runtimeDataSource(DataSource source) {
        if (!source.getType().isJdbc() || !source.isEnabled())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark JAR 绑定数据源当前不可用");
        DatabaseDialect dialect = dialectRegistry.require(source.getType().name());
        JdbcConnectionConfig config = source.getConnection().toJdbcConnectionConfig();
        JdbcConnectionSpec spec = dialect.createConnectionSpec(config);
        Map<String, String> properties = new LinkedHashMap<>();
        Properties jdbcProperties = spec.properties();
        jdbcProperties.stringPropertyNames().stream().sorted().forEach(key -> {
            if (!"user".equalsIgnoreCase(key) && !"password".equalsIgnoreCase(key))
                properties.put(key, jdbcProperties.getProperty(key));
        });
        CanvasTaskRunManifest.RuntimeDatabaseType databaseType;
        try { databaseType = CanvasTaskRunManifest.RuntimeDatabaseType.valueOf(source.getType().name()); }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "当前 Task Engine 尚不支持该 JDBC 类型：" + source.getType().displayName());
        }
        return new CanvasTaskRunManifest.RuntimeDataSource(source.getId(), ConnectionKind.JDBC, databaseType,
                source.getPurposes().stream().map(purpose -> cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.valueOf(purpose.name()))
                        .collect(Collectors.toUnmodifiableSet()),
                new CanvasTaskRunManifest.RuntimeJdbcConnection(spec.driverClassName(), spec.jdbcUrl(),
                        dialect.resolveCatalog(config, null), dialect.resolveSchema(config, null),
                config.username(), config.password(), properties), null, List.of(), null, null, List.of());
    }

    private static CanvasTaskRunManifest.RuntimeDataSource kafkaRuntimeDataSource(
            DataSource source,
            List<SparkJarTaskResourceBinding> bindings
    ) {
        if (!source.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Spark Streaming JAR 绑定 Kafka 数据源当前不可用");
        }
        Set<cn.superhuang.data.scalpel.contract.task.DataSourcePurpose> purposes = bindings.stream()
                .filter(binding -> binding.getResourceType() == SparkJarResourceType.KAFKA_TOPIC
                        && binding.getResourceId().equals(source.getId()))
                .flatMap(binding -> {
                    var values = java.util.EnumSet.noneOf(cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.class);
                    if (binding.getAccessMode().canRead()) values.add(cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.SOURCE);
                    if (binding.getAccessMode().canWrite()) values.add(cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.DISTRIBUTION);
                    return values.stream();
                }).collect(Collectors.toUnmodifiableSet());
        String mechanism = source.getConnection().getOptions().get("saslMechanism");
        if (mechanism != null) mechanism = mechanism.replace('-', '_');
        return new CanvasTaskRunManifest.RuntimeDataSource(
                source.getId(), ConnectionKind.KAFKA, null, purposes,
                null, null, List.of(),
                new CanvasTaskRunManifest.RuntimeKafkaConnection(
                        source.getConnection().getEndpoint(),
                        source.getConnection().getOptions().getOrDefault("securityProtocol", "PLAINTEXT"),
                        mechanism, source.getConnection().getPrincipal(), source.getConnection().secretValue()),
                null, List.of());
    }

    private List<Entry> parseEntries(String json) {
        return objectMapper.readValue(json, new TypeReference<List<Entry>>() {});
    }

    public record Entry(String name, String value) {}
    public record Preparation(MetadataSnapshot metadataSnapshot,
                              List<CanvasTaskRunManifest.RuntimeDataSource> runtimeDataSources,
                              SparkJarExecutionPayload payload,
                              Map<UUID, java.time.Instant> dataSourceVersions,
                              Map<UUID, java.time.Instant> modelVersions) {
        public Preparation {
            runtimeDataSources = List.copyOf(runtimeDataSources);
            dataSourceVersions = Map.copyOf(dataSourceVersions);
            modelVersions = Map.copyOf(modelVersions);
        }
    }

    public record StreamingPreparation(
            MetadataSnapshot metadataSnapshot,
            List<CanvasTaskRunManifest.RuntimeDataSource> runtimeDataSources,
            SparkStreamingJarExecutionPayload payload,
            Map<UUID, java.time.Instant> dataSourceVersions,
            Map<UUID, java.time.Instant> modelVersions
    ) {
        public StreamingPreparation {
            runtimeDataSources = List.copyOf(runtimeDataSources);
            dataSourceVersions = Map.copyOf(dataSourceVersions);
            modelVersions = Map.copyOf(modelVersions);
        }
    }
}
