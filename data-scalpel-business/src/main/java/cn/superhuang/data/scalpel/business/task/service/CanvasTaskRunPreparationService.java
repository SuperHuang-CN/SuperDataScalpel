package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceRuntimeService;
import cn.superhuang.data.scalpel.business.datasource.service.ApiResourceService;
import cn.superhuang.data.scalpel.business.datasource.web.response.ColumnMetadataResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableMetadataResponse;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTableInspection;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTablePort;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFile;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFileStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTableSource;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFileRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableSourceRepository;
import cn.superhuang.data.scalpel.business.filedataset.web.response.FileDatasetParsingOptionsResponse;
import cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.ColumnMappingMode;
import cn.superhuang.data.scalpel.contract.task.CompilationSeverity;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetType;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.JdbcInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JoinOperator;
import cn.superhuang.data.scalpel.contract.task.JoinType;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaStartingOffsets;
import cn.superhuang.data.scalpel.contract.task.KafkaValueColumn;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.task.MetadataDataSource;
import cn.superhuang.data.scalpel.contract.task.MetadataFileDatasetTable;
import cn.superhuang.data.scalpel.contract.task.MetadataModel;
import cn.superhuang.data.scalpel.contract.task.MetadataModelPhysicalTableMode;
import cn.superhuang.data.scalpel.contract.task.MetadataModelStatus;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.data.scalpel.contract.task.ModelInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.RenameColumnMapping;
import cn.superhuang.data.scalpel.contract.task.RenameConfiguration;
import cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.StreamJoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.StreamJoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.StreamJoinType;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationRequest;
import cn.superhuang.data.scalpel.contract.task.TaskCompilationResponse;
import cn.superhuang.data.scalpel.contract.task.TaskDefinition;
import cn.superhuang.data.scalpel.contract.task.TaskType;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

/** Builds the authoritative, execution-time view of a persisted Canvas definition. */
@Service
public class CanvasTaskRunPreparationService {
    private final DataSourceRepository dataSourceRepository;
    private final DataSourceRuntimeService runtimeService;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository modelFieldRepository;
    private final DialectRegistry dialectRegistry;
    private final TaskCompilationService compilationService;
    private final ApiResourceService apiResourceService;
    private final ModelPhysicalTablePort physicalTablePort;
    private final FileDatasetRepository fileDatasetRepository;
    private final FileDatasetTableRepository fileDatasetTableRepository;
    private final FileDatasetFileRepository fileDatasetFileRepository;
    private final FileDatasetFieldRepository fileDatasetFieldRepository;
    private final FileDatasetTableSourceRepository fileDatasetTableSourceRepository;
    private final ObjectProvider<CanvasFileStorageRuntimeProvider> fileStorageRuntimeProvider;
    private final ObjectMapper objectMapper;

    public CanvasTaskRunPreparationService(
            DataSourceRepository dataSourceRepository,
            DataSourceRuntimeService runtimeService,
            DataModelRepository modelRepository,
            DataModelFieldRepository modelFieldRepository,
            DialectRegistry dialectRegistry,
            TaskCompilationService compilationService,
            ApiResourceService apiResourceService,
            ModelPhysicalTablePort physicalTablePort,
            FileDatasetRepository fileDatasetRepository,
            FileDatasetTableRepository fileDatasetTableRepository,
            FileDatasetFileRepository fileDatasetFileRepository,
            FileDatasetFieldRepository fileDatasetFieldRepository,
            FileDatasetTableSourceRepository fileDatasetTableSourceRepository,
            ObjectProvider<CanvasFileStorageRuntimeProvider> fileStorageRuntimeProvider,
            ObjectMapper objectMapper
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.runtimeService = runtimeService;
        this.modelRepository = modelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.dialectRegistry = dialectRegistry;
        this.compilationService = compilationService;
        this.apiResourceService = apiResourceService;
        this.physicalTablePort = physicalTablePort;
        this.fileDatasetRepository = fileDatasetRepository;
        this.fileDatasetTableRepository = fileDatasetTableRepository;
        this.fileDatasetFileRepository = fileDatasetFileRepository;
        this.fileDatasetFieldRepository = fileDatasetFieldRepository;
        this.fileDatasetTableSourceRepository = fileDatasetTableSourceRepository;
        this.fileStorageRuntimeProvider = fileStorageRuntimeProvider;
        this.objectMapper = objectMapper;
    }

    public Preparation prepare(CanvasDefinition definition) {
        return prepare(definition, CanvasExecutionMode.BATCH);
    }

    public Preparation prepare(
            CanvasDefinition definition,
            CanvasExecutionMode executionMode
    ) {
        Map<UUID, RequestedModel> modelRequests = referencedModels(definition);
        Map<UUID, DataModel> models = loadModels(modelRequests.keySet());
        Map<UUID, List<DataModelField>> modelFields = loadModelFields(models.keySet());
        FileDatasetPreparation fileDatasets = prepareFileDatasets(definition);
        Map<UUID, RequestedDataSourceBuilder> requestBuilders = referencedDataSourceBuilders(definition);
        modelRequests.forEach((modelId, request) -> {
            DataModel model = models.get(modelId);
            if (model == null) return;
            RequestedDataSourceBuilder builder = requestBuilders.computeIfAbsent(
                    model.getStorageDataSourceId(),
                    ignored -> new RequestedDataSourceBuilder()
            );
            if (request.input()) builder.modelRead();
            if (request.output()) builder.modelWrite();
        });
        Map<UUID, RequestedDataSource> requests = buildDataSourceRequests(requestBuilders);
        Map<UUID, DataSource> sources = loadDataSources(requests.keySet());
        List<MetadataDataSource> metadataSources = new ArrayList<>();
        List<CanvasTaskRunManifest.RuntimeDataSource> runtimeSources = new ArrayList<>();

        requests.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            DataSource source = sources.get(entry.getKey());
            RequestedDataSource requested = entry.getValue();
            validateSource(source, requested);
            if (source.getType() == DataSourceType.HTTP_API) {
                List<HttpApiContracts.ResourceDefinition> resources = requested.apiResourceIds().stream()
                        .sorted()
                        .map(resourceId -> apiResourceService.runtimeDefinition(source.getId(), resourceId))
                        .toList();
                if (resources.stream().anyMatch(resource -> !resource.enabled())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 引用的 API 资源已停用");
                }
                metadataSources.add(new MetadataDataSource(
                        source.getId(),
                        source.isEnabled(),
                        ConnectionKind.HTTP_API,
                        source.getPurposes().stream()
                                .map(purpose -> cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.valueOf(
                                        purpose.name()))
                                .collect(Collectors.toUnmodifiableSet()),
                        resources.stream().map(CanvasTaskRunPreparationService::apiMetadataTable).toList()
                ));
                runtimeSources.add(httpApiRuntimeDataSource(source, requested, resources));
                return;
            }
            if (source.getType() == DataSourceType.KAFKA) {
                runtimeService.requireKafkaTopics(source.getId(), requested.kafkaTopics());
                metadataSources.add(new MetadataDataSource(
                        source.getId(),
                        source.isEnabled(),
                        ConnectionKind.KAFKA,
                        source.getPurposes().stream()
                                .map(purpose -> cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.valueOf(
                                        purpose.name()))
                                .collect(Collectors.toUnmodifiableSet()),
                        List.of()
                ));
                runtimeSources.add(kafkaRuntimeDataSource(source, requested));
                return;
            }
            DatabaseDialect dialect = dialectRegistry.require(source.getType().name());
            List<MetadataTable> tables = requested.tableNames().stream()
                    .sorted()
                    .map(table -> metadataTable(source, dialect, table))
                    .toList();
            metadataSources.add(new MetadataDataSource(
                    source.getId(),
                    source.isEnabled(),
                    ConnectionKind.JDBC,
                    source.getPurposes().stream()
                            .map(purpose -> cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.valueOf(
                                    purpose.name()))
                            .collect(Collectors.toUnmodifiableSet()),
                    tables
            ));
            runtimeSources.add(runtimeDataSource(source, dialect, requested));
        });

        List<MetadataModel> metadataModels = models.values().stream()
                .sorted(Comparator.comparing(DataModel::getId))
                .map(model -> metadataModel(
                        model,
                        modelFields.getOrDefault(model.getId(), List.of()),
                        sources.get(model.getStorageDataSourceId()),
                        dialectRegistry.require(sources.get(model.getStorageDataSourceId()).getType().name())
                ))
                .toList();

        MetadataSnapshot metadata = new MetadataSnapshot(
                metadataSources,
                metadataModels,
                fileDatasets.metadata()
        );
        TaskCompilationRequest compilationRequest = new TaskCompilationRequest(
                UUID.randomUUID(),
                new TaskDefinition(
                        TaskType.CANVAS,
                        compilationDefinition(definition),
                        executionMode
                ),
                metadata
        );
        TaskCompilationResponse compilation = compilationService.compile(compilationRequest);
        if (!compilation.valid()) {
            String problems = java.util.stream.Stream.concat(
                            compilation.canvasIssues().stream(),
                            compilation.nodeResults().stream().flatMap(result -> result.issues().stream()))
                    .filter(issue -> issue.severity() == CompilationSeverity.ERROR)
                    .map(issue -> issue.message())
                    .limit(5)
                    .collect(Collectors.joining("；"));
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    problems.isBlank() ? "Canvas 定义未通过 Task Engine 预检" : problems);
        }
        Map<UUID, Instant> sourceVersions = sources.values().stream()
                .collect(Collectors.toUnmodifiableMap(DataSource::getId, DataSource::getUpdatedAt));
        Map<UUID, ModelVersion> modelVersions = models.values().stream()
                .collect(Collectors.toUnmodifiableMap(DataModel::getId, ModelVersion::from));
        return new Preparation(
                metadata,
                List.copyOf(runtimeSources),
                compilation,
                sourceVersions,
                modelVersions,
                fileDatasets.runtimeStorage(),
                fileDatasets.runtimeInputs()
        );
    }

    private MetadataTable metadataTable(
            DataSource source,
            DatabaseDialect dialect,
            String tableName
    ) {
        TableMetadataResponse metadata = runtimeService.readTable(source.getId(), null, null, tableName);
        String actualName = metadata.table().identifier().table();
        if (!tableName.equals(actualName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "物理表名称与 Canvas 引用不一致：" + tableName);
        }
        return new MetadataTable(
                tableName,
                "VIEW".equalsIgnoreCase(metadata.table().type())
                        ? DatabaseObjectType.VIEW
                        : DatabaseObjectType.TABLE,
                metadata.columns().stream().sorted(Comparator.comparingInt(ColumnMetadataResponse::ordinal))
                        .map(column -> columnSchema(dialect, column)).toList()
        );
    }

    private static MetadataTable apiMetadataTable(
            HttpApiContracts.ResourceDefinition resource
    ) {
        return new MetadataTable(
                resource.id().toString(),
                DatabaseObjectType.API_RESOURCE,
                resource.outputFields().stream().map(field -> new CanvasColumnSchema(
                        field.name(),
                        field.type().type(),
                        field.type().length(),
                        field.type().precision(),
                        field.type().scale(),
                        field.nullable(),
                        null,
                        false,
                        false,
                        field.comment()
                )).toList()
        );
    }

    private MetadataModel metadataModel(
            DataModel model,
            List<DataModelField> fields,
            DataSource source,
            DatabaseDialect dialect
    ) {
        if (fields.stream().anyMatch(field -> field.getFieldType() == PlatformDataType.GEOMETRY)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "SPATIAL_FIELD_UNSUPPORTED：Canvas 第一版不支持包含空间字段的模型：" + model.getName()
            );
        }
        TableMetadata physical;
        try {
            physical = physicalTablePort.readExternalTable(source, model);
        } catch (DatabaseAccessException exception) {
            physicalSchemaMismatch(model, exception.getMessage());
            throw exception;
        }
        ModelPhysicalTableInspection inspection = physicalTablePort.inspect(source, model, fields, physical);
        if (!inspection.compatible()) {
            physicalSchemaMismatch(model, inspection.message());
        }
        if (!model.getPhysicalTableName().equals(physical.table().identifier().table())
                || !"TABLE".equalsIgnoreCase(physical.table().type())) {
            physicalSchemaMismatch(model, "目标不是同名物理表");
        }
        Map<String, CanvasColumnSchema> physicalColumns = new LinkedHashMap<>();
        physical.columns().stream()
                .sorted(Comparator.comparingInt(ColumnMetadata::ordinal))
                .forEach(column -> {
                    CanvasColumnSchema schema = columnSchema(dialect, column);
                    if (physicalColumns.putIfAbsent(schema.name(), schema) != null) {
                        physicalSchemaMismatch(model, "物理表存在重复字段 " + schema.name());
                    }
                });
        if (fields.isEmpty() || physicalColumns.size() != fields.size()) {
            physicalSchemaMismatch(model, "字段数量不一致");
        }
        List<CanvasColumnSchema> columns = fields.stream()
                .map(field -> modelColumn(model, field, physicalColumns.get(field.getCode())))
                .toList();
        return new MetadataModel(
                model.getId(),
                model.getCode(),
                model.getName(),
                model.getSchemaVersion(),
                MetadataModelStatus.valueOf(model.getStatus().name()),
                MetadataModelPhysicalTableMode.valueOf(model.getPhysicalTableMode().name()),
                model.getStorageDataSourceId(),
                model.getCatalogName(),
                model.getSchemaName(),
                model.getPhysicalTableName(),
                columns
        );
    }

    private static CanvasColumnSchema modelColumn(
            DataModel model,
            DataModelField field,
            CanvasColumnSchema physical
    ) {
        if (physical == null) {
            physicalSchemaMismatch(model, "缺少字段 " + field.getCode());
        }
        PlatformDataType expectedType = field.getFieldType();
        return new CanvasColumnSchema(
                field.getCode(),
                expectedType,
                field.getLength(),
                field.getPrecision(),
                field.getScale(),
                field.isNullable(),
                physical.defaultValue(),
                physical.autoIncrement(),
                physical.generated(),
                field.getDescription()
        );
    }

    private static void physicalSchemaMismatch(DataModel model, String detail) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "MODEL_PHYSICAL_SCHEMA_MISMATCH：模型 %s 的物理结构不一致（%s）".formatted(model.getName(), detail)
        );
    }

    private static CanvasColumnSchema columnSchema(
            DatabaseDialect dialect,
            ColumnMetadataResponse column
    ) {
        TypeMappingResult<PlatformTypeDefinition> mapping = dialect.mapToPlatformType(new JdbcTypeDescriptor(
                column.jdbcType(), column.nativeType(), column.length(), column.precision(), column.scale(), null));
        if (!mapping.acceptable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "字段无法无损映射到平台类型：" + column.name());
        }
        PlatformTypeDefinition type = mapping.definition();
        return new CanvasColumnSchema(
                column.name(),
                type.type(),
                type.length(),
                type.precision(),
                type.scale(),
                column.nullable(),
                column.defaultValue(),
                column.autoIncrement(),
                column.generated(),
                column.comment()
        );
    }

    private static CanvasColumnSchema columnSchema(
            DatabaseDialect dialect,
            ColumnMetadata column
    ) {
        TypeMappingResult<PlatformTypeDefinition> mapping = dialect.mapToPlatformType(JdbcTypeDescriptor.from(column));
        if (!mapping.acceptable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "字段无法无损映射到平台类型：" + column.name());
        }
        PlatformTypeDefinition type = mapping.definition();
        return new CanvasColumnSchema(
                column.name(),
                type.type(),
                type.length(),
                type.precision(),
                type.scale(),
                column.nullable(),
                column.defaultValue(),
                column.autoIncrement(),
                column.generated(),
                column.comment()
        );
    }

    private static CanvasTaskRunManifest.RuntimeDataSource runtimeDataSource(
            DataSource source,
            DatabaseDialect dialect,
            RequestedDataSource requested
    ) {
        JdbcConnectionConfig config = source.getConnection().toJdbcConnectionConfig();
        JdbcConnectionSpec spec = dialect.createConnectionSpec(config);
        Map<String, String> properties = new LinkedHashMap<>();
        Properties jdbcProperties = spec.properties();
        jdbcProperties.stringPropertyNames().stream().sorted().forEach(key -> {
            if (!"user".equalsIgnoreCase(key) && !"password".equalsIgnoreCase(key)) {
                properties.put(key, jdbcProperties.getProperty(key));
            }
        });
        return new CanvasTaskRunManifest.RuntimeDataSource(
                source.getId(),
                ConnectionKind.JDBC,
                CanvasTaskRunManifest.RuntimeDatabaseType.valueOf(source.getType().name()),
                executionPurposes(requested),
                new CanvasTaskRunManifest.RuntimeJdbcConnection(
                        spec.driverClassName(),
                        spec.jdbcUrl(),
                        dialect.resolveCatalog(config, null),
                        dialect.resolveSchema(config, null),
                        config.username(),
                        config.password(),
                        properties
                ),
                null,
                List.of()
        );
    }

    private CanvasTaskRunManifest.RuntimeDataSource httpApiRuntimeDataSource(
            DataSource source,
            RequestedDataSource requested,
            List<HttpApiContracts.ResourceDefinition> resources
    ) {
        return new CanvasTaskRunManifest.RuntimeDataSource(
                source.getId(),
                ConnectionKind.HTTP_API,
                null,
                executionPurposes(requested),
                null,
                runtimeService.runtimeConnection(source),
                resources
        );
    }

    private static CanvasTaskRunManifest.RuntimeDataSource kafkaRuntimeDataSource(
            DataSource source,
            RequestedDataSource requested
    ) {
        String protocol = source.getConnection().getOptions().getOrDefault("securityProtocol", "PLAINTEXT");
        String mechanism = source.getConnection().getOptions().get("saslMechanism");
        if (mechanism != null) {
            mechanism = mechanism.replace('-', '_');
        }
        return new CanvasTaskRunManifest.RuntimeDataSource(
                source.getId(),
                ConnectionKind.KAFKA,
                null,
                executionPurposes(requested),
                null,
                null,
                List.of(),
                new CanvasTaskRunManifest.RuntimeKafkaConnection(
                        source.getConnection().getEndpoint(),
                        protocol,
                        mechanism,
                        source.getConnection().getPrincipal(),
                        source.getConnection().secretValue()
                )
        );
    }

    private static Set<cn.superhuang.data.scalpel.contract.task.DataSourcePurpose> executionPurposes(
            RequestedDataSource requested
    ) {
        EnumSet<cn.superhuang.data.scalpel.contract.task.DataSourcePurpose> purposes =
                EnumSet.noneOf(cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.class);
        if (requested.source() || requested.modelRead()) {
            purposes.add(cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.SOURCE);
        }
        if (requested.storage() || requested.modelWrite()) {
            purposes.add(cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.STORAGE);
        }
        if (requested.distribution()) {
            purposes.add(cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.DISTRIBUTION);
        }
        return Set.copyOf(purposes);
    }

    public void assertDataSourcesUnchanged(Map<UUID, Instant> expectedVersions) {
        Map<UUID, DataSource> current = loadDataSources(expectedVersions.keySet());
        boolean changed = current.values().stream().anyMatch(source ->
                !source.isEnabled() || !source.getUpdatedAt().equals(expectedVersions.get(source.getId())));
        if (changed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 引用的数据源已变化，请重新预检");
        }
    }

    public void assertModelsUnchanged(Map<UUID, ModelVersion> expectedVersions) {
        Map<UUID, DataModel> current = loadModels(expectedVersions.keySet());
        boolean changed = current.size() != expectedVersions.size()
                || current.values().stream().anyMatch(model ->
                !ModelVersion.from(model).equals(expectedVersions.get(model.getId())));
        if (changed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 引用的模型已变化，请重新预检");
        }
    }

    private Map<UUID, DataSource> loadDataSources(Set<UUID> ids) {
        Map<UUID, DataSource> result = dataSourceRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(DataSource::getId, source -> source));
        if (result.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 引用的数据源不存在");
        }
        return Map.copyOf(result);
    }

    private Map<UUID, DataModel> loadModels(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return modelRepository.findAllById(ids).stream()
                .collect(Collectors.toUnmodifiableMap(DataModel::getId, model -> model));
    }

    private Map<UUID, List<DataModelField>> loadModelFields(Set<UUID> modelIds) {
        if (modelIds.isEmpty()) return Map.of();
        Map<UUID, List<DataModelField>> fields = modelFieldRepository
                .findAllByModelIdInOrderByModelAndSort(modelIds).stream()
                .collect(Collectors.groupingBy(
                        DataModelField::getModelId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        return Map.copyOf(fields);
    }

    private FileDatasetPreparation prepareFileDatasets(CanvasDefinition definition) {
        Set<UUID> tableIds = referencedFileDatasetTables(definition);
        if (tableIds.isEmpty()) {
            return FileDatasetPreparation.empty();
        }
        Map<UUID, FileDatasetTable> tables = fileDatasetTableRepository.findAllById(tableIds).stream()
                .collect(Collectors.toMap(FileDatasetTable::getId, table -> table));
        Set<UUID> datasetIds = tables.values().stream()
                .map(FileDatasetTable::getFileDatasetId)
                .collect(Collectors.toSet());
        Map<UUID, List<FileDatasetTableSource>> sources = fileDatasetTableSourceRepository
                .findByFileDatasetTableIdInOrderByFileDatasetTableIdAscSourceOrderAsc(tableIds).stream()
                .collect(Collectors.groupingBy(
                        FileDatasetTableSource::getFileDatasetTableId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Set<UUID> fileIds = sources.values().stream().flatMap(List::stream)
                .map(FileDatasetTableSource::getSourceFileId)
                .collect(Collectors.toSet());
        Map<UUID, FileDataset> datasets = fileDatasetRepository.findAllById(datasetIds).stream()
                .collect(Collectors.toMap(FileDataset::getId, dataset -> dataset));
        Map<UUID, FileDatasetFile> files = fileDatasetFileRepository.findAllById(fileIds).stream()
                .collect(Collectors.toMap(FileDatasetFile::getId, file -> file));
        Map<UUID, List<FileDatasetField>> fields = fileDatasetFieldRepository
                .findByFileDatasetTableIdInOrderByFileDatasetTableIdAscSortOrderAsc(tableIds)
                .stream()
                .collect(Collectors.groupingBy(
                        FileDatasetField::getFileDatasetTableId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        CanvasFileStorageRuntimeProvider storage = fileStorageRuntimeProvider.getIfAvailable();
        if (storage == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "文件数据集对象存储尚未配置");
        }

        List<MetadataFileDatasetTable> metadata = new ArrayList<>();
        List<CanvasTaskRunManifest.RuntimeFileInput> runtimeInputs = new ArrayList<>();
        tables.values().stream().sorted(Comparator.comparing(FileDatasetTable::getId)).forEach(table -> {
            FileDataset dataset = datasets.get(table.getFileDatasetId());
            List<FileDatasetTableSource> tableSources = sources.getOrDefault(table.getId(), List.of());
            if (dataset == null || tableSources.isEmpty()) {
                return;
            }
            boolean filesReady = tableSources.stream()
                    .map(FileDatasetTableSource::getSourceFileId)
                    .map(files::get)
                    .allMatch(file -> file != null && file.getStatus() == FileDatasetFileStatus.READY);
            List<CanvasColumnSchema> columns =
                    fileColumns(fields.getOrDefault(table.getId(), List.of()));
            FileDatasetParseStatus effectiveStatus = table.getParseStatus();
            metadata.add(new MetadataFileDatasetTable(
                    table.getId(),
                    table.getCode(),
                    table.getName(),
                    FileDatasetType.valueOf(dataset.getType().name()),
                    cn.superhuang.data.scalpel.contract.task.FileDatasetParseStatus.valueOf(effectiveStatus.name()),
                    filesReady
                            ? cn.superhuang.data.scalpel.contract.task.FileDatasetFileStatus.READY
                            : cn.superhuang.data.scalpel.contract.task.FileDatasetFileStatus.PREPARING,
                    columns
            ));
            if ((effectiveStatus != FileDatasetParseStatus.READY
                    && effectiveStatus != FileDatasetParseStatus.SCHEMA_READY)
                    || !filesReady
                    || columns.isEmpty()) {
                return;
            }
            String fingerprint = CanvasFileDatasetSchemaFingerprint.calculate(columns);
            runtimeInputs.add(new CanvasTaskRunManifest.RuntimeFileInput(
                    dataset.getId(),
                    table.getId(),
                    fingerprint,
                    runtimeParsingOptions(readFileParsingOptions(dataset.getParsingOptions())),
                    tableSources.stream().map(source -> {
                        FileDatasetFile file = files.get(source.getSourceFileId());
                        return new CanvasTaskRunManifest.RuntimeFileSource(
                                source.getId(),
                                file.getId(),
                                file.getFormat(),
                                file.getCompression(),
                                file.getStorageKind(),
                                storage.resolveObjectKey(file.getObjectKey()),
                                resolveOptionalObjectKey(storage, file.getMaterializedPrefix()),
                                source.getSourceKey()
                        );
                    }).toList()
            ));
        });
        return new FileDatasetPreparation(
                List.copyOf(metadata),
                storage.runtimeStorage(),
                List.copyOf(runtimeInputs)
        );
    }

    private static List<CanvasColumnSchema> fileColumns(List<FileDatasetField> fields) {
        return fields.stream()
                .sorted(Comparator.comparingInt(FileDatasetField::getSortOrder))
                .map(field -> new CanvasColumnSchema(
                        field.getName(),
                        field.getFieldType(),
                        field.getLength(),
                        field.getPrecision(),
                        field.getScale(),
                        field.isNullable(),
                        null,
                        false,
                        false,
                        null
                ))
                .toList();
    }

    private FileDatasetParsingOptionsResponse readFileParsingOptions(String value) {
        try {
            return objectMapper.readValue(value, FileDatasetParsingOptionsResponse.class);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "文件数据集已保存的解析参数无效",
                    exception
            );
        }
    }

    private static CanvasTaskRunManifest.RuntimeFileParsingOptions runtimeParsingOptions(
            FileDatasetParsingOptionsResponse options
    ) {
        return switch (options) {
            case FileDatasetParsingOptionsResponse.Csv value ->
                    new CanvasTaskRunManifest.RuntimeFileParsingOptions.Csv(
                            value.charset(),
                            value.fieldDelimiter(),
                            value.recordDelimiter(),
                            value.quoteCharacter(),
                            value.escapeCharacter(),
                            value.firstRowHeader()
                    );
            case FileDatasetParsingOptionsResponse.Text value ->
                    new CanvasTaskRunManifest.RuntimeFileParsingOptions.Text(
                            value.charset(), value.recordDelimiter()
                    );
            case FileDatasetParsingOptionsResponse.Json value ->
                    new CanvasTaskRunManifest.RuntimeFileParsingOptions.Json(
                            value.charset(), value.rootPointer()
                    );
            case FileDatasetParsingOptionsResponse.JsonLines value ->
                    new CanvasTaskRunManifest.RuntimeFileParsingOptions.JsonLines(
                            value.charset(), value.recordDelimiter()
                    );
            case FileDatasetParsingOptionsResponse.Spreadsheet value ->
                    new CanvasTaskRunManifest.RuntimeFileParsingOptions.Spreadsheet(
                            value.headerRowIndex(), value.dataStartRowIndex()
                    );
            case FileDatasetParsingOptionsResponse.Parquet ignored ->
                    new CanvasTaskRunManifest.RuntimeFileParsingOptions.Parquet();
            case FileDatasetParsingOptionsResponse.Avro ignored ->
                    new CanvasTaskRunManifest.RuntimeFileParsingOptions.Avro();
            case FileDatasetParsingOptionsResponse.Gdb ignored ->
                    new CanvasTaskRunManifest.RuntimeFileParsingOptions.Gdb();
            case FileDatasetParsingOptionsResponse.Shp value ->
                    new CanvasTaskRunManifest.RuntimeFileParsingOptions.Shp(
                            value.dbfCharsetOverride(), value.dbfFallbackCharset()
                    );
        };
    }

    private static String resolveOptionalObjectKey(
            CanvasFileStorageRuntimeProvider storage,
            String objectKey
    ) {
        return objectKey == null || objectKey.isBlank() ? null : storage.resolveObjectKey(objectKey);
    }

    private static Set<UUID> referencedFileDatasetTables(CanvasDefinition definition) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (CanvasDefinition.CanvasNodeDefinition node : definition.nodes()) {
            if (node instanceof CanvasDefinition.FileDatasetInputNodeDefinition input) {
                try {
                    ids.add(UUID.fromString(input.configuration().fileDatasetTableId()));
                } catch (RuntimeException ignored) {
                    // The compiler owns the stable invalid-ID issue. Missing metadata makes the node invalid.
                }
            }
        }
        return Set.copyOf(ids);
    }

    private static void validateSource(DataSource source, RequestedDataSource requested) {
        if (!source.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 引用的数据源已停用：" + source.getName());
        }
        boolean jdbc = source.getType() == DataSourceType.POSTGRESQL || source.getType() == DataSourceType.MYSQL;
        boolean httpApi = source.getType() == DataSourceType.HTTP_API;
        boolean kafka = source.getType() == DataSourceType.KAFKA;
        if (!jdbc && !httpApi && !kafka) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Canvas 执行只支持 PostgreSQL、MySQL、HTTP API 和 Kafka");
        }
        if (httpApi && (requested.storage() || requested.modelRead() || requested.modelWrite()
                || requested.apiResourceIds().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "HTTP API 只能作为 API 输入节点的数据源");
        }
        if (jdbc && !requested.apiResourceIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "HTTP API 输入节点引用了非 API 数据源");
        }
        if (kafka && (!requested.tableNames().isEmpty() || !requested.apiResourceIds().isEmpty()
                || requested.storage() || requested.modelRead() || requested.modelWrite())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kafka 只能用于 Kafka 输入或输出节点");
        }
        if (requested.source() && !source.getPurposes().contains(DataSourcePurpose.SOURCE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "输入数据源不具有 SOURCE 用途：" + source.getName());
        }
        if (requested.storage() && !source.getPurposes().contains(DataSourcePurpose.STORAGE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "输出数据源不具有 STORAGE 用途：" + source.getName());
        }
        if (requested.distribution() && !source.getPurposes().contains(DataSourcePurpose.DISTRIBUTION)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Kafka 输出数据源不具有 DISTRIBUTION 用途：" + source.getName());
        }
        if (requested.modelRead()
                && !source.getPurposes().contains(DataSourcePurpose.SOURCE)
                && !source.getPurposes().contains(DataSourcePurpose.STORAGE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "模型输入数据源不具有 SOURCE 或 STORAGE 用途：" + source.getName());
        }
        if (requested.modelWrite() && !source.getPurposes().contains(DataSourcePurpose.STORAGE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "模型输出数据源不具有 STORAGE 用途：" + source.getName());
        }
    }

    private static Map<UUID, RequestedDataSourceBuilder> referencedDataSourceBuilders(CanvasDefinition definition) {
        Map<UUID, RequestedDataSourceBuilder> builders = new LinkedHashMap<>();
        for (CanvasDefinition.CanvasNodeDefinition node : definition.nodes()) {
            if (node instanceof CanvasDefinition.JdbcInputNodeDefinition input) {
                UUID id = uuid(input.configuration().dataSourceId(), input.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .source(input.configuration().tableName());
            } else if (node instanceof CanvasDefinition.HttpApiInputNodeDefinition input) {
                UUID id = uuid(input.configuration().dataSourceId(), input.name());
                UUID resourceId = uuid(input.configuration().resourceId(), input.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .apiResource(resourceId);
            } else if (node instanceof CanvasDefinition.KafkaInputNodeDefinition input) {
                UUID id = uuid(input.configuration().dataSourceId(), input.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .kafkaSource(input.configuration().topic());
            } else if (node instanceof CanvasDefinition.JdbcOutputNodeDefinition output) {
                UUID id = uuid(output.configuration().dataSourceId(), output.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .storage(output.configuration().targetTableName());
            } else if (node instanceof CanvasDefinition.KafkaOutputNodeDefinition output) {
                UUID id = uuid(output.configuration().dataSourceId(), output.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .distribution(output.configuration().topic());
            }
        }
        return builders;
    }

    private static Map<UUID, RequestedDataSource> buildDataSourceRequests(
            Map<UUID, RequestedDataSourceBuilder> builders
    ) {
        Map<UUID, RequestedDataSource> result = new LinkedHashMap<>();
        builders.forEach((id, builder) -> result.put(id, builder.build()));
        return Map.copyOf(result);
    }

    private static Map<UUID, RequestedModel> referencedModels(CanvasDefinition definition) {
        Map<UUID, RequestedModelBuilder> builders = new LinkedHashMap<>();
        for (CanvasDefinition.CanvasNodeDefinition node : definition.nodes()) {
            if (node instanceof CanvasDefinition.ModelInputNodeDefinition input) {
                UUID id = modelUuid(input.configuration().modelId(), input.name());
                builders.computeIfAbsent(id, ignored -> new RequestedModelBuilder()).input();
            } else if (node instanceof CanvasDefinition.ModelOutputNodeDefinition output) {
                UUID id = modelUuid(output.configuration().targetModelId(), output.name());
                builders.computeIfAbsent(id, ignored -> new RequestedModelBuilder()).output();
            }
        }
        Map<UUID, RequestedModel> result = new LinkedHashMap<>();
        builders.forEach((id, builder) -> result.put(id, builder.build()));
        return Map.copyOf(result);
    }

    private static UUID uuid(String value, String nodeName) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "节点数据源 ID 无效：" + nodeName, exception);
        }
    }

    private static UUID modelUuid(String value, String nodeName) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "节点模型 ID 无效：" + nodeName, exception);
        }
    }

    private static UUID fileDatasetTableUuid(String value, String nodeName) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "节点文件数据集表 ID 无效：" + nodeName,
                    exception
            );
        }
    }

    private static cn.superhuang.data.scalpel.contract.task.CanvasDefinition compilationDefinition(
            CanvasDefinition definition
    ) {
        List<CanvasNodeDefinition> nodes = definition.nodes().stream()
                .<CanvasNodeDefinition>map(node -> switch (node) {
            case CanvasDefinition.ModelInputNodeDefinition input -> new ModelInputNodeDefinition(
                    input.id(), input.name(), layout(input.layout()),
                    new ModelInputConfiguration(
                            modelUuid(input.configuration().modelId(), input.name())));
            case CanvasDefinition.JdbcInputNodeDefinition input -> new JdbcInputNodeDefinition(
                    input.id(), input.name(), layout(input.layout()),
                    new JdbcInputConfiguration(
                            uuid(input.configuration().dataSourceId(), input.name()).toString(),
                            input.configuration().tableName()));
            case CanvasDefinition.FileDatasetInputNodeDefinition input ->
                    new FileDatasetInputNodeDefinition(
                            input.id(),
                            input.name(),
                            layout(input.layout()),
                            new FileDatasetInputConfiguration(
                                    fileDatasetTableUuid(
                                            input.configuration().fileDatasetTableId(),
                                            input.name()
                                    ).toString()
                            )
                    );
            case CanvasDefinition.HttpApiInputNodeDefinition input -> new HttpApiInputNodeDefinition(
                    input.id(), input.name(), layout(input.layout()),
                    new HttpApiInputConfiguration(
                            uuid(input.configuration().dataSourceId(), input.name()).toString(),
                            uuid(input.configuration().resourceId(), input.name()).toString(),
                            input.configuration().outputTableName(),
                            input.configuration().runtimeParameters()));
            case CanvasDefinition.KafkaInputNodeDefinition input -> new KafkaInputNodeDefinition(
                    input.id(), input.name(), layout(input.layout()),
                    new KafkaInputConfiguration(
                            uuid(input.configuration().dataSourceId(), input.name()),
                            input.configuration().topic(),
                            kafkaValueSchema(input.configuration().valueSchema()),
                            input.configuration().outputTableName(),
                            KafkaStartingOffsets.valueOf(
                                    input.configuration().startingOffsets().name())));
            case CanvasDefinition.JoinNodeDefinition join -> new JoinNodeDefinition(
                    join.id(), join.name(), layout(join.layout()),
                    new JoinConfiguration(
                            join.configuration().leftTableName(), join.configuration().rightTableName(),
                            join.configuration().outputTableName(),
                            JoinType.valueOf(join.configuration().joinType().name()),
                            join.configuration().conditions().stream().map(condition -> new JoinCondition(
                                    condition.leftColumnName(),
                                    JoinOperator.valueOf(condition.operator().name()),
                                    condition.rightColumnName())).toList()));
            case CanvasDefinition.StreamJoinNodeDefinition join -> new StreamJoinNodeDefinition(
                    join.id(), join.name(), layout(join.layout()),
                    new StreamJoinConfiguration(
                            join.configuration().leftTableName(),
                            join.configuration().rightTableName(),
                            join.configuration().outputTableName(),
                            StreamJoinType.valueOf(join.configuration().joinType().name()),
                            join.configuration().conditions().stream().map(condition ->
                                    new JoinCondition(
                                            condition.leftColumnName(),
                                            JoinOperator.valueOf(condition.operator().name()),
                                            condition.rightColumnName())).toList()));
            case CanvasDefinition.RenameNodeDefinition rename -> new RenameNodeDefinition(
                    rename.id(), rename.name(), layout(rename.layout()),
                    new RenameConfiguration(
                            rename.configuration().sourceTableName(),
                            rename.configuration().outputTableName(),
                            rename.configuration().columnMappings().stream()
                                    .map(mapping -> new RenameColumnMapping(
                                            mapping.sourceColumnName(),
                                            mapping.targetColumnName()))
                                    .toList()));
            case CanvasDefinition.JdbcOutputNodeDefinition output -> new JdbcOutputNodeDefinition(
                    output.id(), output.name(), layout(output.layout()),
                    new JdbcOutputConfiguration(
                            output.configuration().sourceTableName(),
                            uuid(output.configuration().dataSourceId(), output.name()).toString(),
                            output.configuration().targetTableName(),
                            JdbcWriteMode.valueOf(output.configuration().writeMode().name()),
                            ColumnMappingMode.valueOf(output.configuration().columnMappingMode().name()),
                            output.configuration().columnMappings().stream().map(mapping ->
                                    new JdbcColumnMapping(
                                            mapping.sourceColumnName(), mapping.targetColumnName())).toList()));
            case CanvasDefinition.ModelOutputNodeDefinition output -> new ModelOutputNodeDefinition(
                    output.id(), output.name(), layout(output.layout()),
                    new ModelOutputConfiguration(
                            output.configuration().sourceTableName(),
                            modelUuid(output.configuration().targetModelId(), output.name()),
                            JdbcWriteMode.valueOf(output.configuration().writeMode().name()),
                            ColumnMappingMode.valueOf(output.configuration().columnMappingMode().name()),
                            output.configuration().columnMappings().stream().map(mapping ->
                                    new JdbcColumnMapping(
                                            mapping.sourceColumnName(), mapping.targetColumnName())).toList()));
            case CanvasDefinition.KafkaOutputNodeDefinition output -> new KafkaOutputNodeDefinition(
                    output.id(), output.name(), layout(output.layout()),
                    new KafkaOutputConfiguration(
                            output.configuration().sourceTableName(),
                            uuid(output.configuration().dataSourceId(), output.name()),
                            output.configuration().topic(),
                            kafkaValueSchema(output.configuration().valueSchema()),
                            output.configuration().keyColumnName(),
                            ColumnMappingMode.valueOf(
                                    output.configuration().columnMappingMode().name()),
                            output.configuration().columnMappings().stream().map(mapping ->
                                    new JdbcColumnMapping(
                                            mapping.sourceColumnName(), mapping.targetColumnName())).toList()));
        }).toList();
        return new cn.superhuang.data.scalpel.contract.task.CanvasDefinition(
                definition.schemaVersion(),
                definition.schemaMinorVersion(),
                nodes,
                definition.edges().stream().map(edge -> new CanvasEdgeDefinition(
                        edge.id(), edge.sourceNodeId(), edge.targetNodeId())).toList()
        );
    }

    private static CanvasNodeLayout layout(CanvasDefinition.CanvasNodeLayout layout) {
        return new CanvasNodeLayout(layout.x(), layout.y(), layout.width(), layout.height());
    }

    private static KafkaValueSchema kafkaValueSchema(
            CanvasDefinition.KafkaValueSchema schema
    ) {
        return schema == null
                ? null
                : new KafkaValueSchema(schema.columns().stream()
                        .map(column -> new KafkaValueColumn(
                                column.name(),
                                column.fieldType(),
                                column.length(),
                                column.precision(),
                                column.scale(),
                                column.nullable(),
                                column.comment()
                        ))
                        .toList());
    }

    public record Preparation(
            MetadataSnapshot metadataSnapshot,
            List<CanvasTaskRunManifest.RuntimeDataSource> runtimeDataSources,
            TaskCompilationResponse compilation,
            Map<UUID, Instant> dataSourceVersions,
            Map<UUID, ModelVersion> modelVersions,
            CanvasTaskRunManifest.RuntimeFileStorage runtimeFileStorage,
            List<CanvasTaskRunManifest.RuntimeFileInput> runtimeFileInputs
    ) {
        public Preparation {
            runtimeDataSources = List.copyOf(runtimeDataSources);
            dataSourceVersions = Map.copyOf(dataSourceVersions);
            modelVersions = Map.copyOf(modelVersions);
            runtimeFileInputs = List.copyOf(runtimeFileInputs);
        }
    }

    private record FileDatasetPreparation(
            List<MetadataFileDatasetTable> metadata,
            CanvasTaskRunManifest.RuntimeFileStorage runtimeStorage,
            List<CanvasTaskRunManifest.RuntimeFileInput> runtimeInputs
    ) {
        private static FileDatasetPreparation empty() {
            return new FileDatasetPreparation(List.of(), null, List.of());
        }
    }

    public record ModelVersion(
            Instant updatedAt,
            int schemaVersion,
            DataModelStatus status,
            UUID dataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName
    ) {
        private static ModelVersion from(DataModel model) {
            return new ModelVersion(
                    model.getUpdatedAt(),
                    model.getSchemaVersion(),
                    model.getStatus(),
                    model.getStorageDataSourceId(),
                    model.getCatalogName(),
                    model.getSchemaName(),
                    model.getPhysicalTableName()
            );
        }
    }

    private record RequestedDataSource(
            boolean source,
            boolean storage,
            boolean modelRead,
            boolean modelWrite,
            boolean distribution,
            Set<String> tableNames,
            Set<UUID> apiResourceIds,
            Set<String> kafkaTopics
    ) {
    }

    private static final class RequestedDataSourceBuilder {
        private boolean source;
        private boolean storage;
        private boolean modelRead;
        private boolean modelWrite;
        private boolean distribution;
        private final Set<String> tables = new LinkedHashSet<>();
        private final Set<UUID> apiResources = new LinkedHashSet<>();
        private final Set<String> kafkaTopics = new LinkedHashSet<>();

        private RequestedDataSourceBuilder source(String table) {
            source = true;
            tables.add(table);
            return this;
        }

        private RequestedDataSourceBuilder storage(String table) {
            storage = true;
            tables.add(table);
            return this;
        }

        private RequestedDataSourceBuilder modelRead() {
            modelRead = true;
            return this;
        }

        private RequestedDataSourceBuilder modelWrite() {
            modelWrite = true;
            return this;
        }

        private RequestedDataSourceBuilder apiResource(UUID resourceId) {
            source = true;
            apiResources.add(resourceId);
            return this;
        }

        private RequestedDataSourceBuilder kafkaSource(String topic) {
            source = true;
            kafkaTopics.add(topic);
            return this;
        }

        private RequestedDataSourceBuilder distribution(String topic) {
            distribution = true;
            kafkaTopics.add(topic);
            return this;
        }

        private RequestedDataSource build() {
            return new RequestedDataSource(
                    source, storage, modelRead, modelWrite, distribution,
                    Set.copyOf(tables), Set.copyOf(apiResources), Set.copyOf(kafkaTopics));
        }
    }

    private record RequestedModel(boolean input, boolean output) {
    }

    private static final class RequestedModelBuilder {
        private boolean input;
        private boolean output;

        private void input() {
            input = true;
        }

        private void output() {
            output = true;
        }

        private RequestedModel build() {
            return new RequestedModel(input, output);
        }
    }
}
