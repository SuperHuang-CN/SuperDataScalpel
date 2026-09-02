package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.service.DataSourceRuntimeService;
import cn.superhuang.data.scalpel.business.datasource.service.ApiResourceService;
import cn.superhuang.data.scalpel.business.datasource.service.SpatialFeatureResourceService;
import cn.superhuang.data.scalpel.business.datasource.web.response.ColumnMetadataResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableMetadataResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TdEngineTmqTopicDetailResponse;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
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
import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.execution.CanvasTrialSpec;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasFieldPredicate;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterCondition;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterGroup;
import cn.superhuang.data.scalpel.contract.task.CanvasLiteral;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CompilationSeverity;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetType;
import cn.superhuang.data.scalpel.contract.task.FileOutputConfiguration;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FilterConfiguration;
import cn.superhuang.data.scalpel.contract.task.FilterGroupOperator;
import cn.superhuang.data.scalpel.contract.task.FilterNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FilterOperator;
import cn.superhuang.data.scalpel.contract.task.SelectColumnsConfiguration;
import cn.superhuang.data.scalpel.contract.task.SelectColumnsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.BinaryExpression;
import cn.superhuang.data.scalpel.contract.task.CanvasExpression;
import cn.superhuang.data.scalpel.contract.task.CaseWhenBranch;
import cn.superhuang.data.scalpel.contract.task.CaseWhenExpression;
import cn.superhuang.data.scalpel.contract.task.ColumnDerivation;
import cn.superhuang.data.scalpel.contract.task.ColumnExpression;
import cn.superhuang.data.scalpel.contract.task.DeriveBinaryOperator;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsConfiguration;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.DeriveFunction;
import cn.superhuang.data.scalpel.contract.task.FunctionExpression;
import cn.superhuang.data.scalpel.contract.task.LiteralExpression;
import cn.superhuang.data.scalpel.contract.task.CastFailureStrategy;
import cn.superhuang.data.scalpel.contract.task.ColumnTypeCast;
import cn.superhuang.data.scalpel.contract.task.TypeCastConfiguration;
import cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.AggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.AggregateFunction;
import cn.superhuang.data.scalpel.contract.task.AggregateItem;
import cn.superhuang.data.scalpel.contract.task.AggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.UnionConfiguration;
import cn.superhuang.data.scalpel.contract.task.UnionMode;
import cn.superhuang.data.scalpel.contract.task.UnionNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.DeduplicateConfiguration;
import cn.superhuang.data.scalpel.contract.task.DeduplicateKeepStrategy;
import cn.superhuang.data.scalpel.contract.task.DeduplicateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.NullOrdering;
import cn.superhuang.data.scalpel.contract.task.SortDirection;
import cn.superhuang.data.scalpel.contract.task.SortField;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcColumnMapping;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputTableSelection;
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
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
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
    private final SpatialFeatureResourceService spatialFeatureResourceService;
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
            SpatialFeatureResourceService spatialFeatureResourceService,
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
        this.spatialFeatureResourceService = spatialFeatureResourceService;
        this.fileDatasetRepository = fileDatasetRepository;
        this.fileDatasetTableRepository = fileDatasetTableRepository;
        this.fileDatasetFileRepository = fileDatasetFileRepository;
        this.fileDatasetFieldRepository = fileDatasetFieldRepository;
        this.fileDatasetTableSourceRepository = fileDatasetTableSourceRepository;
        this.fileStorageRuntimeProvider = fileStorageRuntimeProvider;
        this.objectMapper = objectMapper;
    }

    public Preparation prepare(CanvasDefinition definition) {
        return prepare(definition, CanvasExecutionMode.BATCH, null);
    }

    public Preparation prepare(
            CanvasDefinition definition,
            CanvasExecutionMode executionMode
    ) {
        return prepare(definition, executionMode, null);
    }

    public Preparation prepareTrial(CanvasDefinition definition, CanvasTrialSpec trialSpec) {
        if (trialSpec == null) throw new IllegalArgumentException("Canvas 试运行目标不能为空");
        return prepare(definition, CanvasExecutionMode.BATCH, trialSpec);
    }

    private Preparation prepare(
            CanvasDefinition definition,
            CanvasExecutionMode executionMode,
            CanvasTrialSpec trialSpec
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
            if (source.getType() == DataSourceType.ARCGIS_REST || source.getType() == DataSourceType.WFS) {
                List<SpatialServiceResourceDefinition> resources = requested.spatialResourceIds().stream()
                        .sorted()
                        .map(resourceId -> spatialFeatureResourceService.runtimeDefinition(source.getId(), resourceId))
                        .toList();
                if (resources.stream().anyMatch(resource -> !resource.enabled())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 引用的空间要素资源已停用");
                }
                metadataSources.add(new MetadataDataSource(
                        source.getId(), source.isEnabled(), ConnectionKind.HTTP_API,
                        source.getPurposes().stream()
                                .map(purpose -> cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.valueOf(purpose.name()))
                                .collect(Collectors.toUnmodifiableSet()),
                        resources.stream().map(CanvasTaskRunPreparationService::spatialMetadataTable).toList()
                ));
                runtimeSources.add(spatialRuntimeDataSource(source, requested, resources));
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
            if (source.getType() == DataSourceType.S3) {
                metadataSources.add(new MetadataDataSource(
                        source.getId(),
                        source.isEnabled(),
                        ConnectionKind.S3,
                        source.getPurposes().stream()
                                .map(purpose -> cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.valueOf(
                                        purpose.name()))
                                .collect(Collectors.toUnmodifiableSet()),
                        List.of()
                ));
                runtimeSources.add(s3RuntimeDataSource(source, requested));
                return;
            }
            DatabaseDialect dialect = dialectRegistry.require(source.getType().name());
            List<MetadataTable> tables = requested.tableNames().stream()
                    .sorted()
                    .map(table -> metadataTable(source, table))
                    .toList();
            List<MetadataTdEngineTmqTopic> tmqTopics = requested.tdEngineTmqTopics().stream()
                    .sorted(Comparator.comparing(RequestedTdEngineTmqTopic::topicName))
                    .map(topic -> tdEngineTmqMetadata(source, topic))
                    .toList();
            if (source.getType().isTdEngine() && requested.modelRead()) {
                models.entrySet().stream()
                        .filter(model -> modelRequests.get(model.getKey()).input())
                        .map(Map.Entry::getValue)
                        .filter(model -> source.getId().equals(model.getStorageDataSourceId()))
                        .forEach(model -> validateTdEngineModelInput(source, model));
            }
            metadataSources.add(new MetadataDataSource(
                    source.getId(),
                    source.isEnabled(),
                    ConnectionKind.JDBC,
                    jdbcDatabaseType(source.getType()),
                    source.getPurposes().stream()
                            .map(purpose -> cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.valueOf(
                                    purpose.name()))
                            .collect(Collectors.toUnmodifiableSet()),
                    tables,
                    tmqTopics
            ));
            runtimeSources.add(runtimeDataSource(source, dialect, requested));
        });
        validateS3BucketConfigurations(runtimeSources, fileDatasets.runtimeStorage());

        List<MetadataModel> metadataModels = models.values().stream()
                .sorted(Comparator.comparing(DataModel::getId))
                .map(model -> metadataModel(
                        model,
                        modelFields.getOrDefault(model.getId(), List.of())
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
                        definition,
                        executionMode
                ),
                metadata,
                trialSpec
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
        if (trialSpec != null) validateTrialCompilation(compilation, trialSpec);
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

    private static void validateTrialCompilation(
            TaskCompilationResponse compilation,
            CanvasTrialSpec trialSpec
    ) {
        List<NodeCompilationResult> targets = compilation.nodeResults().stream()
                .filter(result -> trialSpec.targetNodeId().equals(result.nodeId()))
                .toList();
        if (targets.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "试运行目标节点不存在或不唯一");
        }
        CanvasTableSchema table = targets.getFirst().outputTables().stream()
                .filter(candidate -> trialSpec.tableName().equals(candidate.name()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "试运行目标表已变化，请重新选择"));
        Set<String> availableColumns = table.columns().stream()
                .map(CanvasColumnSchema::name)
                .collect(Collectors.toUnmodifiableSet());
        if (trialSpec.columnNames().stream().anyMatch(column -> !availableColumns.contains(column))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "试运行目标字段已变化，请重新选择");
        }
    }

    private MetadataTable metadataTable(
            DataSource source,
            String tableName
    ) {
        TableMetadataResponse metadata = runtimeService.readTable(source.getId(), null, null, tableName);
        String actualName = metadata.table().identifier().table();
        if (!tableName.equals(actualName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "物理表名称与 Canvas 引用不一致：" + tableName);
        }
        return new MetadataTable(
                tableName,
                switch (metadata.table().type().toUpperCase(java.util.Locale.ROOT)) {
                    case "VIEW" -> DatabaseObjectType.VIEW;
                    case "SUPERTABLE" -> DatabaseObjectType.SUPERTABLE;
                    default -> DatabaseObjectType.TABLE;
                },
                metadata.columns().stream().sorted(Comparator.comparingInt(ColumnMetadataResponse::ordinal))
                        .map(CanvasTaskRunPreparationService::columnSchema).toList(),
                metadata.uniqueKeys().stream().map(key -> new MetadataUniqueKey(
                        key.name(),
                        MetadataUniqueKeyType.valueOf(key.type()),
                        key.columns()
                )).toList(),
                metadata.indexes().stream().flatMap(index -> index.columns().stream())
                        .collect(Collectors.toUnmodifiableSet()),
                metadata.table().identifier().catalog(),
                metadata.table().identifier().schema(),
                metadata.table().identifier().table()
        );
    }

    private void validateTdEngineModelInput(DataSource source, DataModel model) {
        if (model.getPhysicalTableMode() != cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode.EXTERNAL) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "TDengine 模型输入只能读取绑定已有超级表的外部模型：" + model.getName()
            );
        }
        TableMetadataResponse metadata = runtimeService.readTable(
                source.getId(),
                model.getCatalogName(),
                model.getSchemaName(),
                model.getPhysicalTableName()
        );
        if (!"SUPERTABLE".equalsIgnoreCase(metadata.table().type())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "TDengine 模型绑定对象不是超级表：" + model.getName()
            );
        }
    }

    private MetadataTdEngineTmqTopic tdEngineTmqMetadata(
            DataSource source,
            RequestedTdEngineTmqTopic requested
    ) {
        TdEngineTmqTopicDetailResponse topic = runtimeService.readTdEngineTmqTopic(
                source.getId(), requested.topicName());
        if (!topic.supported()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "TDENGINE_TMQ_TOPIC_UNSUPPORTED：" + topic.unsupportedReason()
            );
        }
        if (!requested.definitionFingerprint().equals(topic.definitionFingerprint())
                && !requested.definitionFingerprint().equals(topic.legacyDefinitionFingerprint())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "TDENGINE_TMQ_TOPIC_CHANGED：Topic 定义已变化，请重新选择 Topic"
            );
        }
        if (!requested.catalogName().equals(topic.databaseName())
                || !requested.supertableName().equals(topic.supertableName())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "TDENGINE_TMQ_TOPIC_CHANGED：Topic 来源数据库或超级表已变化"
            );
        }
        if (topic.columns().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "TDENGINE_TMQ_SCHEMA_MISMATCH：Topic 超级表结构为空"
            );
        }
        return new MetadataTdEngineTmqTopic(
                topic.topicName(), topic.databaseName(), topic.supertableName(),
                topic.definitionFingerprint(), topic.legacyDefinitionFingerprint(), topic.timePrecision(),
                topic.columns().stream()
                        .sorted(Comparator.comparingInt(ColumnMetadataResponse::ordinal))
                        .map(CanvasTaskRunPreparationService::columnSchema)
                        .toList()
        );
    }

    private static CanvasJdbcDatabaseType jdbcDatabaseType(DataSourceType type) {
        if (!type.isJdbc()) return null;
        try {
            return CanvasJdbcDatabaseType.valueOf(type.name());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "当前 Canvas Runner 尚不支持 JDBC 类型：" + type.displayName(),
                    exception
            );
        }
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

    private static MetadataTable spatialMetadataTable(SpatialServiceResourceDefinition resource) {
        return new MetadataTable(
                resource.id().toString(),
                DatabaseObjectType.SPATIAL_FEATURE_RESOURCE,
                resource.columns()
        );
    }

    private MetadataModel metadataModel(
            DataModel model,
            List<DataModelField> fields
    ) {
        List<CanvasColumnSchema> columns = fields.stream()
                .sorted(Comparator.comparingInt(DataModelField::getSortOrder))
                .map(CanvasTaskRunPreparationService::modelColumn)
                .toList();
        List<String> primaryKeyColumns = fields.stream()
                .filter(DataModelField::isPrimaryKey)
                .sorted(Comparator.comparingInt(DataModelField::getSortOrder))
                .map(DataModelField::getCode)
                .toList();
        List<MetadataUniqueKey> uniqueKeys = primaryKeyColumns.isEmpty()
                ? List.of()
                : List.of(new MetadataUniqueKey(
                        "MODEL_PRIMARY_KEY",
                        MetadataUniqueKeyType.PRIMARY_KEY,
                        primaryKeyColumns
                ));
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
                columns,
                uniqueKeys,
                fields.stream()
                        .sorted(Comparator.comparingInt(DataModelField::getSortOrder))
                        .map(field -> new MetadataModelField(
                                field.getId(), field.getCode(), field.getName(), field.getSortOrder()
                        )).toList()
        );
    }

    private static CanvasColumnSchema modelColumn(DataModelField field) {
        return new CanvasColumnSchema(
                field.getCode(),
                field.getFieldType(),
                field.getLength(),
                field.getPrecision(),
                field.getScale(),
                field.isNullable(),
                null,
                false,
                false,
                field.getDescription(),
                field.getGeometry()
        );
    }

    private static CanvasColumnSchema columnSchema(ColumnMetadataResponse column) {
        PlatformTypeDefinition type = column.platformTypeDefinition();
        if (type == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "字段无法无损映射到平台类型：" + column.name());
        }
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
                column.comment(),
                type.geometry()
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
                List.of(),
                null,
                null,
                List.of(),
                requested.tdEngineTmqTopics().isEmpty()
                        ? null
                        : tdEngineTmqConnection(source)
        );
    }

    private static CanvasTaskRunManifest.RuntimeTdEngineTmqConnection tdEngineTmqConnection(DataSource source) {
        return new CanvasTaskRunManifest.RuntimeTdEngineTmqConnection(
                source.getConnection().getHost() + ":" + source.getConnection().getPort(),
                source.getConnection().getUsername(),
                source.getConnection().secretValue(),
                Boolean.parseBoolean(source.getConnection().getOptions().getOrDefault("useSSL", "false"))
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

    private CanvasTaskRunManifest.RuntimeDataSource spatialRuntimeDataSource(
            DataSource source,
            RequestedDataSource requested,
            List<SpatialServiceResourceDefinition> resources
    ) {
        return new CanvasTaskRunManifest.RuntimeDataSource(
                source.getId(), ConnectionKind.HTTP_API, null, executionPurposes(requested), null,
                runtimeService.runtimeConnection(source), List.of(), null, null, resources
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
                ),
                null
        );
    }

    private static CanvasTaskRunManifest.RuntimeDataSource s3RuntimeDataSource(
            DataSource source,
            RequestedDataSource requested
    ) {
        Map<String, String> options = source.getConnection().getOptions();
        return new CanvasTaskRunManifest.RuntimeDataSource(
                source.getId(),
                ConnectionKind.S3,
                null,
                executionPurposes(requested),
                null,
                null,
                List.of(),
                null,
                new CanvasTaskRunManifest.RuntimeS3Connection(
                        source.getConnection().getEndpoint(),
                        options.getOrDefault("region", "us-east-1"),
                        source.getConnection().getTarget(),
                        source.getConnection().getNamespace(),
                        Boolean.parseBoolean(options.getOrDefault("pathStyleAccess", "true")),
                        source.getConnection().getPrincipal(),
                        source.getConnection().secretValue()
                )
        );
    }

    private static void validateS3BucketConfigurations(
            List<CanvasTaskRunManifest.RuntimeDataSource> runtimeSources,
            CanvasTaskRunManifest.RuntimeFileStorage runtimeFileStorage
    ) {
        Map<String, S3BucketConfiguration> buckets = new LinkedHashMap<>();
        for (CanvasTaskRunManifest.RuntimeDataSource source : runtimeSources) {
            CanvasTaskRunManifest.RuntimeS3Connection connection = source.s3Connection();
            if (connection == null) continue;
            requireCompatibleBucketConfiguration(
                    buckets,
                    connection.bucket(),
                    new S3BucketConfiguration(
                            connection.endpoint(), connection.region(), connection.pathStyleAccess(),
                            connection.accessKey(), connection.secretKey())
            );
        }
        if (runtimeFileStorage != null) {
            requireCompatibleBucketConfiguration(
                    buckets,
                    runtimeFileStorage.bucket(),
                    new S3BucketConfiguration(
                            runtimeFileStorage.endpoint(), runtimeFileStorage.region(),
                            runtimeFileStorage.pathStyleAccess(),
                            runtimeFileStorage.accessKey(), runtimeFileStorage.secretKey())
            );
        }
    }

    private static void requireCompatibleBucketConfiguration(
            Map<String, S3BucketConfiguration> buckets,
            String bucket,
            S3BucketConfiguration configuration
    ) {
        S3BucketConfiguration previous = buckets.putIfAbsent(bucket, configuration);
        if (previous != null && !previous.equals(configuration)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "S3_BUCKET_CONFIGURATION_CONFLICT：同一任务中的 Bucket 运行连接配置不一致"
            );
        }
    }

    private static Set<cn.superhuang.data.scalpel.contract.task.DataSourcePurpose> executionPurposes(
            RequestedDataSource requested
    ) {
        EnumSet<cn.superhuang.data.scalpel.contract.task.DataSourcePurpose> purposes =
                EnumSet.noneOf(cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.class);
        if (requested.source() || requested.modelRead()) {
            purposes.add(cn.superhuang.data.scalpel.contract.task.DataSourcePurpose.SOURCE);
        }
        if (requested.modelWrite()) {
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
                    dataset.getId(),
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
                        null,
                        field.getGeometry()
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
        for (CanvasNodeDefinition node : definition.nodes()) {
            if (node instanceof FileDatasetInputNodeDefinition input) {
                input.configuration().tables().forEach(selection -> {
                    try {
                        ids.add(UUID.fromString(selection.fileDatasetTableId()));
                    } catch (RuntimeException ignored) {
                        // The compiler owns the stable invalid-ID issue. Missing metadata makes the node invalid.
                    }
                });
            }
        }
        return Set.copyOf(ids);
    }

    private void validateSource(DataSource source, RequestedDataSource requested) {
        if (!source.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Canvas 引用的数据源已停用：" + source.getName());
        }
        boolean jdbc = source.getType().isJdbc();
        boolean httpApi = source.getType() == DataSourceType.HTTP_API;
        boolean spatialService = source.getType() == DataSourceType.ARCGIS_REST || source.getType() == DataSourceType.WFS;
        boolean kafka = source.getType() == DataSourceType.KAFKA;
        boolean s3 = source.getType() == DataSourceType.S3;
        if (!jdbc && !httpApi && !spatialService && !kafka && !s3) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Canvas 执行只支持 JDBC、HTTP API、空间服务、Kafka 和 S3 数据源");
        }
        if (httpApi && (requested.modelRead() || requested.modelWrite()
                || requested.apiResourceIds().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "HTTP API 只能作为 API 输入节点的数据源");
        }
        if (spatialService && (requested.modelRead() || requested.modelWrite()
                || requested.spatialResourceIds().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间服务只能作为空间服务输入节点的数据源");
        }
        if (jdbc && (!requested.apiResourceIds().isEmpty() || !requested.spatialResourceIds().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "HTTP API 或空间服务输入节点引用了非对应数据源");
        }
        if (!requested.tdEngineTmqTopics().isEmpty()) {
            if (source.getType() != DataSourceType.TDENGINE_WEBSOCKET) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "TDengine TMQ 输入只能引用 TDENGINE_WEBSOCKET 数据源"
                );
            }
            if (!dialectRegistry.require(source.getType().name()).definition().capabilities()
                    .contains(DatabaseCapability.TMQ_SUBSCRIBE)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源不具备 TMQ_SUBSCRIBE 能力");
            }
        }
        if (requested.incrementalSource()
                && !dialectRegistry.require(source.getType().name()).definition().capabilities()
                .contains(DatabaseCapability.JDBC_INCREMENTAL_READ)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源不具备 JDBC_INCREMENTAL_READ 能力");
        }
        if (kafka && (!requested.tableNames().isEmpty() || !requested.apiResourceIds().isEmpty() || !requested.spatialResourceIds().isEmpty()
                || !requested.tdEngineTmqTopics().isEmpty()
                || requested.modelRead() || requested.modelWrite() || requested.fileOutput())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Kafka 只能用于 Kafka 输入或输出节点");
        }
        if (s3 && (!requested.fileOutput() || requested.source() || requested.modelRead()
                || requested.modelWrite() || !requested.tableNames().isEmpty()
                || !requested.apiResourceIds().isEmpty() || !requested.spatialResourceIds().isEmpty()
                || !requested.kafkaTopics().isEmpty() || !requested.tdEngineTmqTopics().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "S3 数据源只能用于文件输出节点");
        }
        if (!s3 && requested.fileOutput()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件输出节点只能引用 S3 数据源");
        }
        if (source.getType().isTdEngine() && requested.querySource()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "TDengine 第一版只允许 JDBC 表输入节点读取超级表，不开放自定义 SQL 查询输入"
            );
        }
        if (requested.source() && !source.getPurposes().contains(DataSourcePurpose.SOURCE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "输入数据源不具有 SOURCE 用途：" + source.getName());
        }
        if (requested.distribution() && !source.getPurposes().contains(DataSourcePurpose.DISTRIBUTION)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "输出数据源不具有 DISTRIBUTION 用途：" + source.getName());
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
        for (CanvasNodeDefinition node : definition.nodes()) {
            if (node instanceof JdbcInputNodeDefinition input) {
                UUID id = uuid(input.configuration().dataSourceId(), input.name());
                RequestedDataSourceBuilder builder = builders.computeIfAbsent(
                        id, ignored -> new RequestedDataSourceBuilder()).source();
                if (input.configuration().tables() != null) {
                    input.configuration().tables().stream()
                            .map(JdbcInputTableSelection::tableName)
                            .filter(tableName -> tableName != null && !tableName.isBlank())
                            .forEach(builder::sourceTable);
                }
            } else if (node instanceof JdbcIncrementalInputNodeDefinition input) {
                UUID id = uuid(input.configuration().dataSourceId(), input.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .incrementalSource(input.configuration().tableName());
            } else if (node instanceof JdbcQueryInputNodeDefinition input) {
                UUID id = uuid(input.configuration().dataSourceId(), input.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .querySource();
            } else if (node instanceof HttpApiInputNodeDefinition input) {
                UUID id = uuid(input.configuration().dataSourceId(), input.name());
                RequestedDataSourceBuilder builder = builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder());
                input.configuration().resources().forEach(selection ->
                        builder.apiResource(uuid(selection.resourceId(), input.name())));
            } else if (node instanceof SpatialServiceInputNodeDefinition input) {
                UUID id = uuid(input.configuration().dataSourceId(), input.name());
                RequestedDataSourceBuilder builder = builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder());
                input.configuration().resources().forEach(selection ->
                        builder.spatialResource(uuid(selection.resourceId(), input.name())));
            } else if (node instanceof KafkaInputNodeDefinition input) {
                UUID id = uuid(input.configuration().dataSourceId(), input.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .kafkaSource(input.configuration().topic());
            } else if (node instanceof TdEngineTmqInputNodeDefinition input) {
                UUID id = uuid(input.configuration().dataSourceId(), input.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .tdEngineTmqSource(new RequestedTdEngineTmqTopic(
                                input.configuration().topicName(),
                                input.configuration().catalogName(),
                                input.configuration().supertableName(),
                                input.configuration().topicDefinitionFingerprint()
                        ));
            } else if (node instanceof JdbcOutputNodeDefinition output) {
                UUID id = uuid(output.configuration().dataSourceId(), output.name());
                RequestedDataSourceBuilder builder = builders.computeIfAbsent(
                        id, ignored -> new RequestedDataSourceBuilder());
                output.configuration().writes().forEach(write -> builder.distributionTable(write.targetTableName()));
            } else if (node instanceof JdbcSnapshotSyncOutputNodeDefinition output) {
                UUID id = uuid(output.configuration().dataSourceId(), output.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .distributionTable(output.configuration().targetTableName());
            } else if (node instanceof KafkaOutputNodeDefinition output) {
                UUID id = uuid(output.configuration().dataSourceId(), output.name());
                RequestedDataSourceBuilder builder = builders.computeIfAbsent(
                        id, ignored -> new RequestedDataSourceBuilder());
                output.configuration().writes().forEach(write -> builder.distribution(write.topic()));
            } else if (node instanceof FileOutputNodeDefinition output) {
                UUID id = uuid(output.configuration().dataSourceId(), output.name());
                builders.computeIfAbsent(id, ignored -> new RequestedDataSourceBuilder())
                        .fileDistribution();
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
        for (CanvasNodeDefinition node : definition.nodes()) {
            if (node instanceof ModelInputNodeDefinition input) {
                input.configuration().models().forEach(selection -> {
                    UUID id = modelUuid(selection.modelId(), input.name());
                    builders.computeIfAbsent(id, ignored -> new RequestedModelBuilder()).input();
                });
            } else if (node instanceof ModelOutputNodeDefinition output) {
                output.configuration().writes().forEach(write -> {
                    UUID id = modelUuid(write.targetModelId(), output.name());
                    builders.computeIfAbsent(id, ignored -> new RequestedModelBuilder()).output();
                });
            } else if (node instanceof ModelSnapshotSyncOutputNodeDefinition output) {
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
            boolean modelRead,
            boolean modelWrite,
            boolean distribution,
            boolean fileOutput,
            boolean querySource,
            boolean incrementalSource,
            Set<String> tableNames,
            Set<UUID> apiResourceIds,
            Set<UUID> spatialResourceIds,
            Set<String> kafkaTopics,
            Set<RequestedTdEngineTmqTopic> tdEngineTmqTopics
    ) {
    }

    private record RequestedTdEngineTmqTopic(
            String topicName,
            String catalogName,
            String supertableName,
            String definitionFingerprint
    ) {
    }

    private static final class RequestedDataSourceBuilder {
        private boolean source;
        private boolean modelRead;
        private boolean modelWrite;
        private boolean distribution;
        private boolean fileOutput;
        private boolean querySource;
        private boolean incrementalSource;
        private final Set<String> tables = new LinkedHashSet<>();
        private final Set<UUID> apiResources = new LinkedHashSet<>();
        private final Set<UUID> spatialResources = new LinkedHashSet<>();
        private final Set<String> kafkaTopics = new LinkedHashSet<>();
        private final Set<RequestedTdEngineTmqTopic> tdEngineTmqTopics = new LinkedHashSet<>();

        private RequestedDataSourceBuilder source() {
            source = true;
            return this;
        }

        private RequestedDataSourceBuilder sourceTable(String table) {
            source = true;
            tables.add(table);
            return this;
        }

        private RequestedDataSourceBuilder querySource() {
            source = true;
            querySource = true;
            return this;
        }

        private RequestedDataSourceBuilder incrementalSource(String table) {
            source = true;
            incrementalSource = true;
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

        private RequestedDataSourceBuilder spatialResource(UUID resourceId) {
            source = true;
            spatialResources.add(resourceId);
            return this;
        }

        private RequestedDataSourceBuilder kafkaSource(String topic) {
            source = true;
            kafkaTopics.add(topic);
            return this;
        }

        private RequestedDataSourceBuilder tdEngineTmqSource(RequestedTdEngineTmqTopic topic) {
            source = true;
            tdEngineTmqTopics.add(topic);
            return this;
        }

        private RequestedDataSourceBuilder distribution(String topic) {
            distribution = true;
            kafkaTopics.add(topic);
            return this;
        }

        private RequestedDataSourceBuilder distributionTable(String table) {
            distribution = true;
            tables.add(table);
            return this;
        }

        private RequestedDataSourceBuilder fileDistribution() {
            distribution = true;
            fileOutput = true;
            return this;
        }

        private RequestedDataSource build() {
            return new RequestedDataSource(
                    source, modelRead, modelWrite, distribution, fileOutput, querySource, incrementalSource,
                    Set.copyOf(tables), Set.copyOf(apiResources), Set.copyOf(spatialResources),
                    Set.copyOf(kafkaTopics), Set.copyOf(tdEngineTmqTopics));
        }
    }

    private record S3BucketConfiguration(
            String endpoint,
            String region,
            boolean pathStyleAccess,
            String accessKey,
            String secretKey
    ) {
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
