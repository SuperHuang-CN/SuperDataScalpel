package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChange;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChangeStatus;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelPhysicalChangeRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.web.request.CreatePhysicalTableChangePlanRequest;
import cn.superhuang.data.scalpel.business.model.web.request.CreateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryRequest;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryFilterInput;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelFieldInput;
import cn.superhuang.data.scalpel.business.model.web.request.ExecutePhysicalTableChangePlanRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelFieldsRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDetailResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDataQueryResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelFieldResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPhysicalChangeResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ExternalTableImportColumnResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ExternalTableImportPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PlatformTypeCapabilityResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PhysicalTableDdlPlanResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PhysicalTableInspectionResponse;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskInputRepository;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableStorageDefinition;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingQuality;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.dialect.query.ConditionConjunction;
import cn.superhuang.data.scalpel.dialect.query.QueryFilterOperator;
import cn.superhuang.data.scalpel.dialect.query.QuerySortDirection;
import cn.superhuang.data.scalpel.dialect.query.QueryValueType;
import cn.superhuang.data.scalpel.dialect.query.StandardQuery;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryField;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryFilterInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryLimits;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryOrderInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryResult;
import cn.superhuang.data.scalpel.dialect.query.StandardTableQueryCompiler;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.time.Duration;
import java.time.Instant;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class DataModelService {

    private static final Pattern LOWER_TABLE_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,127}");
    private static final Pattern LOWER_FIELD_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final int QUICK_PREVIEW_ROW_COUNT = 50;
    private static final int MODEL_QUERY_MAXIMUM_PAGE_SIZE = 100;
    private static final Duration MODEL_QUERY_TIMEOUT = Duration.ofSeconds(15);
    private static final StandardTableQueryCompiler STANDARD_QUERY_COMPILER = new StandardTableQueryCompiler();
    private static final StandardQueryLimits MODEL_QUERY_LIMITS = new StandardQueryLimits(
            50, MODEL_QUERY_MAXIMUM_PAGE_SIZE, 20, 1_000, 3, 0, 0, 10_000
    );

    private final DataModelRepository repository;
    private final DataModelFieldRepository fieldRepository;
    private final DataModelPhysicalChangeRepository physicalChangeRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DirectoryService directoryService;
    private final SearchEngine searchEngine;
    private final ModelPhysicalTablePort physicalTablePort;
    private final DataServiceRepository dataServiceRepository;
    private final LocalSqlTaskDefinitionRepository localSqlTaskDefinitionRepository;
    private final LocalSqlTaskInputRepository localSqlTaskInputRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final DialectRegistry dialectRegistry;

    public DataModelService(
            DataModelRepository repository,
            DataModelFieldRepository fieldRepository,
            DataModelPhysicalChangeRepository physicalChangeRepository,
            DataSourceRepository dataSourceRepository,
            DirectoryService directoryService,
            SearchEngine searchEngine,
            ModelPhysicalTablePort physicalTablePort,
            DataServiceRepository dataServiceRepository,
            LocalSqlTaskDefinitionRepository localSqlTaskDefinitionRepository,
            LocalSqlTaskInputRepository localSqlTaskInputRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            DialectRegistry dialectRegistry
    ) {
        this.repository = repository;
        this.fieldRepository = fieldRepository;
        this.physicalChangeRepository = physicalChangeRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.directoryService = directoryService;
        this.searchEngine = searchEngine;
        this.physicalTablePort = physicalTablePort;
        this.dataServiceRepository = dataServiceRepository;
        this.localSqlTaskDefinitionRepository = localSqlTaskDefinitionRepository;
        this.localSqlTaskInputRepository = localSqlTaskInputRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dialectRegistry = dialectRegistry;
    }

    @Transactional(readOnly = true)
    public PageResponse<DataModelResponse> search(SearchRequest request) {
        Page<DataModel> result = searchEngine.search(request, DataModel.class, repository);
        Map<UUID, String> storageNames = storageNames(result.getContent());
        return new PageResponse<>(
                result.getContent().stream()
                        .map(model -> DataModelResponse.from(model, storageNames.get(model.getStorageDataSourceId())))
                        .toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataModelDetailResponse get(UUID id) {
        return detail(requireModel(id));
    }

    public ExternalTableImportPreviewResponse previewExternalTableImport(
            UUID storageDataSourceId,
            String physicalTableName
    ) {
        DataSource storage = requireStorageDataSource(storageDataSourceId, true);
        validateExternalPhysicalTableName(PhysicalTableMode.EXTERNAL, physicalTableName);
        PhysicalNamespace namespace = resolvePhysicalNamespace(storage);
        TableIdentifier table = new TableIdentifier(
                namespace.catalogName(), namespace.schemaName(), physicalTableName.trim()
        );
        TableMetadata metadata;
        try {
            metadata = physicalTablePort.readExternalTable(storage, table);
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
        DatabaseDialect dialect = dialectRegistry.require(storage.getType().name());
        List<ExternalColumnMapping> mappings = externalColumnMappings(metadata, dialect);
        List<String> issues = mappings.isEmpty()
                ? List.of("外部表至少需要一个可导入字段")
                : mappings.stream()
                .filter(mapping -> !mapping.importable())
                .map(ExternalColumnMapping::issue)
                .toList();
        List<ExternalTableImportColumnResponse> columns = mappings.stream()
                .map(mapping -> new ExternalTableImportColumnResponse(
                        mapping.column().name(),
                        mapping.column().nativeType(),
                        mapping.platformType() == null ? null : mapping.platformType().type(),
                        mapping.platformType() == null ? null : mapping.platformType().length(),
                        mapping.platformType() == null ? null : mapping.platformType().precision(),
                        mapping.platformType() == null ? null : mapping.platformType().scale(),
                        mapping.column().nullable(),
                        mapping.primaryKey(),
                        mapping.quality(),
                        mapping.mappingMessage(),
                        mapping.importable(),
                        truncateExternalFieldComment(mapping.column().comment())
                ))
                .toList();
        return new ExternalTableImportPreviewResponse(table, !columns.isEmpty() && issues.isEmpty(), columns, issues);
    }

    @Transactional(readOnly = true)
    public List<PlatformTypeCapabilityResponse> platformTypeCapabilities(UUID storageDataSourceId) {
        DataSource storage = requireStorageDataSource(storageDataSourceId, false);
        DatabaseDialect dialect = dialectRegistry.require(storage.getType().name());
        List<PlatformTypeCapabilityResponse> result = new ArrayList<>();
        for (PlatformDataType type : PlatformDataType.values()) {
            if (type == PlatformDataType.STRING) {
                TypeMappingResult<PhysicalTypeDefinition> bounded = dialect.mapToPhysicalType(
                        PlatformTypeDefinition.string(255)
                );
                TypeMappingResult<PhysicalTypeDefinition> unbounded = dialect.mapToPhysicalType(
                        PlatformTypeDefinition.string(null)
                );
                boolean supported = bounded.acceptable() || unbounded.acceptable();
                result.add(new PlatformTypeCapabilityResponse(
                        type,
                        supported,
                        firstMessage(bounded, unbounded),
                        bounded.acceptable(),
                        unbounded.acceptable()
                ));
                continue;
            }
            PlatformTypeDefinition definition = type == PlatformDataType.DECIMAL
                    ? PlatformTypeDefinition.decimal(38, 18)
                    : PlatformTypeDefinition.of(type);
            TypeMappingResult<PhysicalTypeDefinition> mapping = dialect.mapToPhysicalType(definition);
            result.add(new PlatformTypeCapabilityResponse(
                    type, mapping.acceptable(), mapping.message(), false, false
            ));
        }
        return List.copyOf(result);
    }

    private static String firstMessage(
            TypeMappingResult<PhysicalTypeDefinition> first,
            TypeMappingResult<PhysicalTypeDefinition> second
    ) {
        if (first.message() != null) {
            return first.message();
        }
        return second.message();
    }

    public DataModelDetailResponse create(CreateDataModelRequest request) {
        CreatePreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> prepareCreate(request))
        );
        List<ExternalTableField> importedFields = preparation.physicalTableMode() == PhysicalTableMode.EXTERNAL
                ? importExternalTableFields(preparation.storage(), preparation.model())
                : List.of();
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeCreate(request, preparation, importedFields)
        ));
    }

    private CreatePreparation prepareCreate(CreateDataModelRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型编码已存在");
        }
        directoryService.validateAssignment(DirectoryScope.MODEL, request.directoryId());
        DataSource storage = requireStorageDataSource(request.storageDataSourceId(), false);

        PhysicalNamespace namespace = resolvePhysicalNamespace(storage);
        String physicalTableName = normalizeCode(request.physicalTableName());
        PhysicalTableMode physicalTableMode = physicalTableMode(request.physicalTableMode());
        validateExternalPhysicalTableName(physicalTableMode, request.physicalTableName());
        List<String> clickHouseOrderByColumns = normalizeClickHouseOrderByColumns(request.clickHouseOrderByColumns());
        validateClickHouseStorageConfiguration(storage, physicalTableMode, clickHouseOrderByColumns);
        validatePhysicalLocationAvailable(
                request.storageDataSourceId(), namespace.catalogName(), namespace.schemaName(), physicalTableName, null
        );

        DataModel model = DataModel.create(
                code, request.name(), request.directoryId(), request.storageDataSourceId(),
                namespace.catalogName(), namespace.schemaName(), physicalTableName,
                request.physicalTableMode(), clickHouseOrderByColumns, request.description()
        );
        return new CreatePreparation(model, storage, physicalTableMode, namespace, physicalTableName);
    }

    private DataModelDetailResponse completeCreate(
            CreateDataModelRequest request,
            CreatePreparation preparation,
            List<ExternalTableField> importedFields
    ) {
        if (repository.existsByCode(preparation.model().getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型编码已存在");
        }
        directoryService.validateAssignment(DirectoryScope.MODEL, request.directoryId());
        validatePhysicalLocationAvailable(
                request.storageDataSourceId(), preparation.namespace().catalogName(), preparation.namespace().schemaName(),
                preparation.physicalTableName(), null
        );
        DataModel model = preparation.model();
        DataModel saved = repository.saveAndFlush(model);
        if (!importedFields.isEmpty()) {
            saveImportedExternalFields(saved, importedFields, false);
        }
        return detail(saved);
    }

    public DataModelDetailResponse update(UUID id, UpdateDataModelRequest request) {
        UpdatePreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> prepareUpdate(id, request))
        );
        if (preparation.requiresMissingTableCheck()) {
            ModelPhysicalTableInspection inspection = physicalTablePort.inspect(
                    preparation.storage(), preparation.model(), preparation.currentFields()
            );
            if (inspection.state() != PhysicalTableState.NOT_FOUND) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "物理表已存在，ClickHouse 排序键需要通过后续换表流程调整，不能直接修改"
                );
            }
        }
        preparation.model().update(
                request.name(), request.directoryId(), request.storageDataSourceId(),
                preparation.namespace().catalogName(), preparation.namespace().schemaName(),
                preparation.physicalTableName(), request.physicalTableMode(), preparation.clickHouseOrderByColumns(),
                request.description()
        );
        List<ExternalTableField> importedFields = preparation.importExternalFields()
                ? importExternalTableFields(preparation.storage(), preparation.model())
                : List.of();
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeUpdate(id, request, preparation, importedFields)
        ));
    }

    private UpdatePreparation prepareUpdate(UUID id, UpdateDataModelRequest request) {
        DataModel model = requireModel(id);
        directoryService.validateAssignment(DirectoryScope.MODEL, request.directoryId());

        boolean storageChanged = !Objects.equals(model.getStorageDataSourceId(), request.storageDataSourceId());
        DataSource storage = model.getStatus() == DataModelStatus.DRAFT
                ? requireStorageDataSource(request.storageDataSourceId(), false)
                : null;
        PhysicalNamespace namespace = storageChanged && storage != null
                ? resolvePhysicalNamespace(storage)
                : new PhysicalNamespace(model.getCatalogName(), model.getSchemaName());
        String physicalTableName = normalizeCode(request.physicalTableName());
        PhysicalTableMode physicalTableMode = physicalTableMode(request.physicalTableMode());
        validateExternalPhysicalTableName(physicalTableMode, request.physicalTableName());
        List<String> clickHouseOrderByColumns = request.clickHouseOrderByColumns() == null
                ? model.getClickHouseOrderByColumns()
                : normalizeClickHouseOrderByColumns(request.clickHouseOrderByColumns());
        boolean physicalLocationChanged = storageChanged
                || !Objects.equals(model.getPhysicalTableName(), physicalTableName)
                || model.getPhysicalTableMode() != physicalTableMode;
        boolean physicalDefinitionChanged = physicalLocationChanged
                || !model.getClickHouseOrderByColumns().equals(clickHouseOrderByColumns);
        if (model.getStatus() != DataModelStatus.DRAFT && physicalDefinitionChanged) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布模型不能修改数据存储或物理位置");
        }
        if (physicalDefinitionChanged) {
            requireNoActivePhysicalChange(id);
        }
        if (model.getStatus() == DataModelStatus.DRAFT) {
            validateClickHouseStorageConfiguration(storage, physicalTableMode, clickHouseOrderByColumns);
            validatePhysicalLocationAvailable(
                    request.storageDataSourceId(), namespace.catalogName(), namespace.schemaName(), physicalTableName, id
            );
        }
        boolean requiresMissingTableCheck = model.getStatus() == DataModelStatus.DRAFT
                && !physicalLocationChanged
                && !model.getClickHouseOrderByColumns().equals(clickHouseOrderByColumns)
                && model.getPhysicalTableMode() == PhysicalTableMode.MANAGED;
        return new UpdatePreparation(
                model, model.getUpdatedAt(), storage, namespace, physicalTableName, physicalTableMode,
                clickHouseOrderByColumns, physicalLocationChanged, physicalDefinitionChanged,
                model.getStatus() == DataModelStatus.DRAFT
                        && physicalTableMode == PhysicalTableMode.EXTERNAL
                        && physicalLocationChanged,
                requiresMissingTableCheck ? fieldsFor(id) : List.of(),
                requiresMissingTableCheck
        );
    }

    private DataModelDetailResponse completeUpdate(
            UUID id,
            UpdateDataModelRequest request,
            UpdatePreparation preparation,
            List<ExternalTableField> importedFields
    ) {
        DataModel model = requireModel(id);
        if (!Objects.equals(model.getUpdatedAt(), preparation.expectedUpdatedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型定义已发生变化，请重新提交");
        }
        directoryService.validateAssignment(DirectoryScope.MODEL, request.directoryId());
        if (preparation.physicalDefinitionChanged()) {
            requireNoActivePhysicalChange(id);
        }
        if (preparation.physicalLocationChanged()) {
            validatePhysicalLocationAvailable(
                    request.storageDataSourceId(), preparation.namespace().catalogName(), preparation.namespace().schemaName(),
                    preparation.physicalTableName(), id
            );
        }
        model.update(
                request.name(), request.directoryId(), request.storageDataSourceId(),
                preparation.namespace().catalogName(), preparation.namespace().schemaName(),
                preparation.physicalTableName(), request.physicalTableMode(), preparation.clickHouseOrderByColumns(),
                request.description()
        );
        DataModel saved = repository.saveAndFlush(model);
        if (!importedFields.isEmpty()) {
            saveImportedExternalFields(saved, importedFields, true);
        }
        return detail(saved);
    }

    public DataModelDetailResponse updateFields(UUID id, UpdateDataModelFieldsRequest request) {
        UpdateFieldsPreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> prepareUpdateFields(id, request))
        );
        if (preparation.model().getPhysicalTableMode() == PhysicalTableMode.EXTERNAL) {
            validateExternalFieldUpdate(preparation.storage(), preparation.model(), preparation.normalizedFields());
        }
        requireDirectFieldUpdateAllowed(preparation.model(), preparation.currentFields());
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeUpdateFields(id, preparation)
        ));
    }

    private UpdateFieldsPreparation prepareUpdateFields(UUID id, UpdateDataModelFieldsRequest request) {
        DataModel model = requireModel(id);
        if (model.getStatus() != DataModelStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿模型可以修改字段结构");
        }
        requireNoActivePhysicalChange(id);

        List<NormalizedField> normalizedFields = normalizeFields(request.fields());
        DataSource storage = requireStorageDataSource(model.getStorageDataSourceId(), false);
        if (model.getPhysicalTableMode() == PhysicalTableMode.MANAGED) {
            validatePhysicalTypeMappings(storage, normalizedFields);
        }
        validateClickHouseOrderByFields(
                storage,
                model,
                normalizedFields.stream().map(NormalizedField::code).toList(),
                normalizedFields.stream().map(field -> field.input().fieldType()).toList()
        );
        List<DataModelField> currentFields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(id);
        return new UpdateFieldsPreparation(
                model, model.getUpdatedAt(), storage, normalizedFields, currentFields
        );
    }

    private DataModelDetailResponse completeUpdateFields(UUID id, UpdateFieldsPreparation preparation) {
        DataModel model = requireModel(id);
        if (!Objects.equals(model.getUpdatedAt(), preparation.expectedUpdatedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型字段已发生变化，请重新提交");
        }
        if (model.getStatus() != DataModelStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿模型可以修改字段结构");
        }
        requireNoActivePhysicalChange(id);
        List<NormalizedField> normalizedFields = preparation.normalizedFields();
        List<DataModelField> currentFields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(id);
        Map<UUID, DataModelField> existingFields = currentFields.stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        Set<UUID> retainedIds = new HashSet<>();
        List<DataModelField> fieldsToSave = new ArrayList<>();

        for (NormalizedField normalized : normalizedFields) {
            DataModelFieldInput input = normalized.input();
            DataModelField field;
            if (input.id() == null) {
                field = DataModelField.create(
                        id, normalized.code(), input.name(), input.fieldType(), normalized.length(),
                        normalized.precision(), normalized.scale(), input.nullable(), input.primaryKey(),
                        input.sortOrder(), input.description()
                );
            } else {
                field = existingFields.get(input.id());
                if (field == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型字段不存在或不属于当前模型");
                }
                if (!retainedIds.add(input.id())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型字段 ID 重复");
                }
                field.update(
                        normalized.code(), input.name(), input.fieldType(), normalized.length(),
                        normalized.precision(), normalized.scale(), input.nullable(), input.primaryKey(),
                        input.sortOrder(), input.description()
                );
            }
            fieldsToSave.add(field);
        }

        List<DataModelField> removedFields = existingFields.values().stream()
                .filter(field -> !retainedIds.contains(field.getId()))
                .toList();
        if (!removedFields.isEmpty()) {
            fieldRepository.deleteAll(removedFields);
            fieldRepository.flush();
        }
        fieldRepository.saveAllAndFlush(fieldsToSave);
        model.advanceSchemaVersion();
        return detail(repository.saveAndFlush(model));
    }

    public DataModelPhysicalChangeResponse createPhysicalTableChangePlan(
            UUID id,
            CreatePhysicalTableChangePlanRequest request
    ) {
        ChangePlanPreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> prepareChangePlan(id, request))
        );
        DataModel model = preparation.model();
        DataSource storage = preparation.storage();
        List<DataModelField> currentFields = preparation.currentFields();
        List<NormalizedField> targetFields = preparation.targetFields();
        ModelPhysicalTableInspection inspection = physicalTablePort.inspect(storage, model, currentFields);
        if (inspection.state() != PhysicalTableState.MATCHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "物理表未就绪：" + inspection.message());
        }
        DatabaseDialect dialect = dialectRegistry.require(storage.getType().name());
        TableDefinition before = currentTableDefinition(dialect, storage, model, inspection.table(), currentFields);
        TableDefinition target = targetTableDefinition(dialect, storage, model, inspection.table(), targetFields);
        TableChangePlan plan;
        try {
            plan = physicalTablePort.planChange(storage, model, before, target);
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        } catch (UnsupportedOperationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        if (!before.structureFingerprint().equals(plan.beforeFingerprint())
                || !target.structureFingerprint().equals(plan.targetFingerprint())) {
            throw new IllegalStateException("物理表变更规划返回的结构快照与当前请求不一致");
        }
        if (plan.strategy() == TableChangeStrategy.METADATA_ONLY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "未检测到物理表结构变化，请直接保存字段信息");
        }
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeChangePlan(id, preparation, plan)
        ));
    }

    private ChangePlanPreparation prepareChangePlan(UUID id, CreatePhysicalTableChangePlanRequest request) {
        DataModel model = requireModel(id);
        if (model.getStatus() != DataModelStatus.DRAFT && model.getStatus() != DataModelStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先停用已发布模型后再修改物理表结构");
        }
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "绑定已有表模式暂不支持修改物理表");
        }

        DataSource storage = requireStorageDataSource(model.getStorageDataSourceId(), true);
        List<DataModelField> currentFields = fieldsFor(id);
        if (currentFields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前模型没有可变更的字段结构");
        }
        List<NormalizedField> targetFields = normalizeFields(request.fields());
        validateClickHouseOrderByFields(
                storage,
                model,
                targetFields.stream().map(NormalizedField::code).toList(),
                targetFields.stream().map(field -> field.input().fieldType()).toList()
        );
        Map<UUID, DataModelField> existingFields = currentFields.stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        validateTargetFieldIds(targetFields, existingFields);
        return new ChangePlanPreparation(
                model, model.getUpdatedAt(), model.getSchemaVersion(), storage, currentFields, targetFields
        );
    }

    private DataModelPhysicalChangeResponse completeChangePlan(
            UUID id,
            ChangePlanPreparation preparation,
            TableChangePlan plan
    ) {
        DataModel model = requireModel(id);
        if (!Objects.equals(model.getUpdatedAt(), preparation.expectedUpdatedAt())
                || model.getSchemaVersion() != preparation.schemaVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型字段已发生变化，请重新生成变更计划");
        }
        supersedePlannedChanges(id);
        DataModelPhysicalChange change = DataModelPhysicalChange.planned(
                id,
                model.getSchemaVersion(),
                Math.addExact(model.getSchemaVersion(), 1),
                plan.strategy(),
                plan.risk(),
                plan.atomicity(),
                plan.beforeFingerprint().value(),
                plan.targetFingerprint().value(),
                writeSnapshot(plan, "无法保存物理表变更计划"),
                writeSnapshot(
                        preparation.targetFields().stream().map(ModelPhysicalTableChangeTargetField::from).toList(),
                        "无法保存目标字段快照"
                )
        );
        return physicalChangeResponse(physicalChangeRepository.saveAndFlush(change));
    }

    @Transactional(readOnly = true)
    public PageResponse<DataModelPhysicalChangeResponse> searchPhysicalTableChangePlans(UUID modelId, SearchRequest request) {
        requireModel(modelId);
        Page<DataModelPhysicalChange> result = searchEngine.search(
                request,
                DataModelPhysicalChange.class,
                physicalChangeRepository,
                (root, query, builder) -> builder.equal(root.get("modelId"), modelId)
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::physicalChangeResponse).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataModelPhysicalChangeResponse getPhysicalTableChangePlan(UUID modelId, UUID planId) {
        requireModel(modelId);
        return physicalChangeResponse(requirePhysicalChange(modelId, planId));
    }

    @Transactional
    public DataModelPhysicalChangeResponse cancelPhysicalTableChangePlan(UUID modelId, UUID planId) {
        requireModel(modelId);
        DataModelPhysicalChange change = requirePhysicalChange(modelId, planId);
        if (change.getStatus() != DataModelPhysicalChangeStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有待执行计划可以取消");
        }
        change.cancel();
        return physicalChangeResponse(physicalChangeRepository.saveAndFlush(change));
    }

    public DataModelPhysicalChangeResponse executePhysicalTableChangePlan(
            UUID modelId,
            UUID planId,
            ExecutePhysicalTableChangePlanRequest request
    ) {
        ChangeExecutionContext context = requireTransactionResult(transactionTemplate.execute(status -> prepareChangeExecution(
                modelId, planId, request.executionMode()
        )));
        try {
            physicalTablePort.executeChange(context.storage(), context.model(), context.plan(), context.mode());
        } catch (DatabaseAccessException exception) {
            transactionTemplate.executeWithoutResult(status -> {
                if (context.plan().atomicity() == cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity.ATOMIC_SINGLE_STATEMENT
                        && "POSTCHECK_FAILED".equals(exception.code())) {
                    markChangePartial(modelId, planId, exception.code(), exception.getMessage());
                } else {
                    markChangeFailed(modelId, planId, exception);
                }
            });
            throw changeExecutionException(exception);
        } catch (RuntimeException exception) {
            DatabaseAccessException databaseException = new DatabaseAccessException(
                    "CHANGE_EXECUTION_FAILED", "执行物理表变更失败", exception
            );
            transactionTemplate.executeWithoutResult(status -> markChangeFailed(modelId, planId, databaseException));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, databaseException.getMessage(), exception);
        }
        try {
            return requireTransactionResult(transactionTemplate.execute(status -> completeChangeExecution(modelId, planId)));
        } catch (RuntimeException exception) {
            transactionTemplate.executeWithoutResult(status -> markChangePartial(modelId, planId));
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "物理表已变更，但模型字段快照保存失败", exception);
        }
    }

    public DataModelDetailResponse publish(UUID id) {
        ModelOperationPreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> prepareLifecycleOperation(id, DataModelStatus.DRAFT, true))
        );
        requirePhysicalTableMatched(preparation.storage(), preparation.model(), preparation.fields());
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeLifecycleOperation(preparation, DataModelStatus.DRAFT)
        ));
    }

    @Transactional
    public DataModelDetailResponse disable(UUID id) {
        DataModel model = requireModel(id);
        if (model.getStatus() != DataModelStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布模型可以停用");
        }
        model.disable();
        return detail(repository.saveAndFlush(model));
    }

    public DataModelDetailResponse enable(UUID id) {
        ModelOperationPreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> prepareLifecycleOperation(id, DataModelStatus.DISABLED, false))
        );
        requirePhysicalTableMatched(preparation.storage(), preparation.model(), preparation.fields());
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeLifecycleOperation(preparation, DataModelStatus.DISABLED)
        ));
    }

    private ModelOperationPreparation prepareLifecycleOperation(
            UUID id,
            DataModelStatus expectedStatus,
            boolean requireFields
    ) {
        DataModel model = requireModel(id);
        if (model.getStatus() != expectedStatus) {
            String message = expectedStatus == DataModelStatus.DRAFT ? "只有草稿模型可以发布" : "只有已停用模型可以启用";
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
        List<DataModelField> fields = fieldsFor(id);
        if (requireFields && fields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先定义至少一个模型字段");
        }
        DataSource storage = requireStorageDataSource(model.getStorageDataSourceId(), true);
        return new ModelOperationPreparation(model, model.getUpdatedAt(), storage, fields);
    }

    private DataModelDetailResponse completeLifecycleOperation(
            ModelOperationPreparation preparation,
            DataModelStatus expectedStatus
    ) {
        DataModel model = requireModel(preparation.model().getId());
        if (model.getStatus() != expectedStatus
                || !Objects.equals(model.getUpdatedAt(), preparation.expectedUpdatedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型状态已发生变化，请重试");
        }
        model.publish();
        return detail(repository.saveAndFlush(model));
    }

    @Transactional
    public void delete(UUID id) {
        DataModel model = requireModel(id);
        if (model.getStatus() == DataModelStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布模型请先停用后再删除");
        }
        if (dataServiceRepository.existsByModelId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型已被数据服务使用，不能删除");
        }
        if (localSqlTaskDefinitionRepository.existsByOutputModelId(id)
                || localSqlTaskInputRepository.existsByModelId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型已被本地 SQL 任务引用，不能删除");
        }
        physicalChangeRepository.deleteAllByModelId(id);
        fieldRepository.deleteAllByModelId(id);
        repository.delete(model);
    }

    private DataModelDetailResponse detail(DataModel model) {
        String storageName = dataSourceRepository.findById(model.getStorageDataSourceId())
                .map(DataSource::getName)
                .orElse("已删除的数据存储");
        List<DataModelFieldResponse> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId())
                .stream().map(DataModelFieldResponse::from).toList();
        return new DataModelDetailResponse(DataModelResponse.from(model, storageName), fields);
    }

    private void requireDirectFieldUpdateAllowed(DataModel model, List<DataModelField> currentFields) {
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED || currentFields.isEmpty()) {
            return;
        }
        DataSource storage = requireStorageDataSource(model.getStorageDataSourceId(), false);
        ModelPhysicalTableInspection inspection;
        try {
            inspection = physicalTablePort.inspect(storage, model, currentFields);
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
        if (inspection.state() == PhysicalTableState.NOT_FOUND) {
            return;
        }
        if (inspection.state() == PhysicalTableState.MATCHED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "受管物理表已存在，请先生成并执行物理表变更计划"
            );
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "受管物理表未处于可直接保存状态：" + inspection.message() + "；请先检查物理表状态"
        );
    }

    public PhysicalTableInspectionResponse inspectPhysicalTable(UUID id) {
        ModelOperationPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            DataModel model = requireModel(id);
            return new ModelOperationPreparation(
                    model, model.getUpdatedAt(), requireStorageDataSource(model.getStorageDataSourceId(), false), fieldsFor(id)
            );
        }));
        return PhysicalTableInspectionResponse.from(
                preparation.model().getPhysicalTableMode(),
                physicalTablePort.inspect(preparation.storage(), preparation.model(), preparation.fields())
        );
    }

    @Transactional(readOnly = true)
    public PhysicalTableDdlPlanResponse physicalTableDdl(UUID id) {
        DataModel model = requireModel(id);
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            return PhysicalTableDdlPlanResponse.unsupported(
                    model.getPhysicalTableMode(), "绑定已有表模式不生成建表 SQL"
            );
        }
        List<DataModelField> fields = fieldsFor(id);
        if (fields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先定义至少一个模型字段");
        }
        DataSource storage = requireStorageDataSource(model.getStorageDataSourceId(), false);
        validateClickHouseOrderByFields(
                storage,
                model,
                fields.stream().map(DataModelField::getCode).toList(),
                fields.stream().map(DataModelField::getFieldType).toList()
        );
        try {
            DdlPlan plan = physicalTablePort.planCreate(storage, model, fields);
            return PhysicalTableDdlPlanResponse.supported(model.getPhysicalTableMode(), plan);
        } catch (DatabaseAccessException | UnsupportedOperationException exception) {
            return PhysicalTableDdlPlanResponse.unsupported(model.getPhysicalTableMode(), exception.getMessage());
        }
    }

    public PhysicalTableInspectionResponse createPhysicalTable(UUID id) {
        DataModel model = requireModel(id);
        if (model.getStatus() != DataModelStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿模型可以创建物理表");
        }
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "绑定已有表模式不能创建物理表");
        }
        List<DataModelField> fields = fieldsFor(id);
        if (fields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先定义至少一个模型字段");
        }
        DataSource storage = requireStorageDataSource(model.getStorageDataSourceId(), true);
        validateClickHouseOrderByFields(
                storage,
                model,
                fields.stream().map(DataModelField::getCode).toList(),
                fields.stream().map(DataModelField::getFieldType).toList()
        );
        return PhysicalTableInspectionResponse.from(
                model.getPhysicalTableMode(), physicalTablePort.create(storage, model, fields)
        );
    }

    public DataModelPreviewResponse previewPhysicalTable(UUID id) {
        ModelOperationPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            DataModel model = requireModel(id);
            return new ModelOperationPreparation(
                    model, model.getUpdatedAt(), requireStorageDataSource(model.getStorageDataSourceId(), true), fieldsFor(id)
            );
        }));
        DataModel model = preparation.model();
        DataSource storage = preparation.storage();
        List<DataModelField> fields = preparation.fields();
        ModelPhysicalTableInspection inspection = requireQueryablePhysicalTable(storage, model, fields);
        StandardQueryInput input = new StandardQueryInput(
                1, QUICK_PREVIEW_ROW_COUNT, ConditionConjunction.AND, List.of(), List.of(),
                defaultClickHouseOrders(storage, model, fields, List.of()), List.of(), List.of(), false
        );
        DataQueryResult result = executeDataQuery(storage, model, fields, inspection.table(), input);
        return DataModelPreviewResponse.from(model, result.fields(), result.rows(), QUICK_PREVIEW_ROW_COUNT, result.hasNext());
    }

    public DataModelDataQueryResponse queryPhysicalTableData(UUID id, DataModelDataQueryRequest request) {
        ModelOperationPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            DataModel model = requireModel(id);
            return new ModelOperationPreparation(
                    model, model.getUpdatedAt(), requireStorageDataSource(model.getStorageDataSourceId(), true), fieldsFor(id)
            );
        }));
        DataModel model = preparation.model();
        DataSource storage = preparation.storage();
        List<DataModelField> fields = preparation.fields();
        ModelPhysicalTableInspection inspection = requireQueryablePhysicalTable(storage, model, fields);
        StandardQueryInput input = new StandardQueryInput(
                request.pageNo(), request.pageSize(),
                "OR".equals(request.conditionType()) ? ConditionConjunction.OR : ConditionConjunction.AND,
                request.columns(),
                request.filters().stream().map(this::queryFilter).toList(),
                defaultClickHouseOrders(
                        storage,
                        model,
                        fields,
                        request.orders().stream().map(order -> new StandardQueryOrderInput(
                                order.field(), QuerySortDirection.valueOf(order.direction().name())
                        )).toList()
                ),
                List.of(), List.of(), request.returnCount()
        );
        DataQueryResult result = executeDataQuery(storage, model, fields, inspection.table(), input);
        return new DataModelDataQueryResponse(
                result.fields().stream().map(DataModelDataQueryResponse.Column::from).toList(),
                result.rows(), result.pageNo(), result.pageSize(), result.hasNext(), result.totalCount(), result.stableOrder()
        );
    }

    private DataQueryResult executeDataQuery(
            DataSource storage,
            DataModel model,
            List<DataModelField> fields,
            TableIdentifier table,
            StandardQueryInput input
    ) {
        final cn.superhuang.data.scalpel.dialect.query.CompiledStandardTableQuery compiled;
        try {
            compiled = STANDARD_QUERY_COMPILER.compile(
                    table, fields.stream().map(DataModelService::queryField).toList(), input, MODEL_QUERY_LIMITS
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        int fetchSize = Math.addExact(compiled.pageSize(), 1);
        StandardQuery query = withLimit(compiled.query(), fetchSize);
        StandardQueryResult result;
        try {
            result = physicalTablePort.query(storage, model, query, fetchSize, MODEL_QUERY_TIMEOUT);
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        } catch (UnsupportedOperationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        boolean hasNext = result.rows().size() > compiled.pageSize();
        List<Map<String, Object>> rows = hasNext ? result.rows().subList(0, compiled.pageSize()) : result.rows();
        Map<String, DataModelField> fieldsByCode = fields.stream()
                .collect(Collectors.toMap(DataModelField::getCode, Function.identity()));
        List<DataModelField> selectedFields = compiled.query().projections().stream()
                .map(projection -> fieldsByCode.get(projection.alias()))
                .filter(Objects::nonNull)
                .toList();
        return new DataQueryResult(
                selectedFields, List.copyOf(rows), compiled.pageNo(), compiled.pageSize(), hasNext,
                result.totalCount(), !compiled.query().orders().isEmpty()
        );
    }

    private ModelPhysicalTableInspection requireQueryablePhysicalTable(
            DataSource storage,
            DataModel model,
            List<DataModelField> fields
    ) {
        ModelPhysicalTableInspection inspection = physicalTablePort.inspect(storage, model, fields);
        if (inspection.state() != PhysicalTableState.MATCHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "物理表未就绪：" + inspection.message());
        }
        return inspection;
    }

    private StandardQueryFilterInput queryFilter(DataModelDataQueryFilterInput input) {
        try {
            return new StandardQueryFilterInput(
                    input.field(), QueryFilterOperator.valueOf(input.operator().trim().toUpperCase(Locale.ROOT)),
                    input.value(), input.secondValue(), input.values()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的过滤运算符：" + input.operator(), exception);
        }
    }

    private static List<StandardQueryOrderInput> defaultClickHouseOrders(
            DataSource storage,
            DataModel model,
            List<DataModelField> fields,
            List<StandardQueryOrderInput> requestedOrders
    ) {
        if (!requestedOrders.isEmpty() || storage.getType() != DataSourceType.CLICKHOUSE) {
            return requestedOrders;
        }
        Set<String> fieldCodes = fields.stream().map(DataModelField::getCode).collect(Collectors.toSet());
        return model.getClickHouseOrderByColumns().stream()
                .filter(fieldCodes::contains)
                .map(column -> new StandardQueryOrderInput(column, QuerySortDirection.ASC))
                .toList();
    }

    private static StandardQueryField queryField(DataModelField field) {
        if (field.getFieldType() == PlatformDataType.BINARY) {
            return new StandardQueryField(field.getCode(), field.getCode(), null, field.isPrimaryKey(), false);
        }
        return new StandardQueryField(
                field.getCode(), field.getCode(), queryValueType(field.getFieldType()), field.isPrimaryKey(), true
        );
    }

    private static QueryValueType queryValueType(PlatformDataType type) {
        return switch (type) {
            case STRING -> QueryValueType.STRING;
            case BYTE, SHORT, INTEGER -> QueryValueType.INTEGER;
            case LONG -> QueryValueType.LONG;
            case FLOAT, DOUBLE, DECIMAL -> QueryValueType.DECIMAL;
            case BOOLEAN -> QueryValueType.BOOLEAN;
            case DATE -> QueryValueType.DATE;
            case TIMESTAMP, TIMESTAMP_NTZ -> QueryValueType.DATETIME;
            case BINARY -> throw new IllegalArgumentException("BINARY 字段不支持数据查询");
        };
    }

    private static StandardQuery withLimit(StandardQuery source, int limit) {
        return new StandardQuery(
                source.table(), source.projections(), source.conjunction(), source.filters(), source.groups(),
                source.aggregates(), source.orders(), source.offset(), limit, source.returnCount()
        );
    }

    private Map<UUID, String> storageNames(List<DataModel> models) {
        Set<UUID> ids = models.stream().map(DataModel::getStorageDataSourceId).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        dataSourceRepository.findAllById(ids).forEach(source -> names.put(source.getId(), source.getName()));
        return names;
    }

    private DataModel requireModel(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在"));
    }

    private DataModelPhysicalChange requirePhysicalChange(UUID modelId, UUID planId) {
        return physicalChangeRepository.findByIdAndModelId(planId, modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "物理表变更计划不存在"));
    }

    private ChangeExecutionContext prepareChangeExecution(
            UUID modelId,
            UUID planId,
            TableChangeExecutionMode mode
    ) {
        DataModel model = requireModel(modelId);
        DataModelPhysicalChange change = requirePhysicalChange(modelId, planId);
        if (change.getStatus() != DataModelPhysicalChangeStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有待执行计划可以执行");
        }
        if (model.getSchemaVersion() != change.getBaseSchemaVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型字段已变化，请重新生成物理表变更计划");
        }
        if (model.getStatus() != DataModelStatus.DRAFT && model.getStatus() != DataModelStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先停用已发布模型后再执行物理表变更");
        }
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "绑定已有表模式不允许执行物理表变更");
        }
        TableChangePlan plan = readChangePlan(change);
        try {
            plan.requireExecutionOption(mode);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前计划不提供所选执行方式", exception);
        }
        DataSource storage = requireStorageDataSource(model.getStorageDataSourceId(), true);
        change.startApplying(mode);
        physicalChangeRepository.saveAndFlush(change);
        return new ChangeExecutionContext(storage, model, plan, mode);
    }

    private DataModelPhysicalChangeResponse completeChangeExecution(UUID modelId, UUID planId) {
        DataModel model = requireModel(modelId);
        DataModelPhysicalChange change = requirePhysicalChange(modelId, planId);
        if (change.getStatus() != DataModelPhysicalChangeStatus.APPLYING) {
            throw new IllegalStateException("物理表变更计划不处于执行状态");
        }
        if (model.getSchemaVersion() != change.getBaseSchemaVersion()) {
            throw new IllegalStateException("模型字段版本在执行期间发生变化");
        }
        applyTargetFieldSnapshot(model, change);
        model.advanceSchemaVersion();
        if (model.getSchemaVersion() != change.getTargetSchemaVersion()) {
            throw new IllegalStateException("目标模型结构版本不一致");
        }
        change.succeed();
        repository.saveAndFlush(model);
        return physicalChangeResponse(physicalChangeRepository.saveAndFlush(change));
    }

    private void markChangeFailed(UUID modelId, UUID planId, DatabaseAccessException exception) {
        DataModelPhysicalChange change = requirePhysicalChange(modelId, planId);
        if (change.getStatus() == DataModelPhysicalChangeStatus.APPLYING) {
            change.fail(exception.code(), exception.getMessage());
            physicalChangeRepository.saveAndFlush(change);
        }
    }

    private void markChangePartial(UUID modelId, UUID planId) {
        markChangePartial(modelId, planId, "MODEL_METADATA_UPDATE_FAILED", "物理表已变更，但模型字段快照保存失败");
    }

    private void markChangePartial(UUID modelId, UUID planId, String errorCode, String errorMessage) {
        DataModelPhysicalChange change = requirePhysicalChange(modelId, planId);
        if (change.getStatus() == DataModelPhysicalChangeStatus.APPLYING) {
            change.markPartial(errorCode, errorMessage);
            physicalChangeRepository.saveAndFlush(change);
        }
    }

    private void applyTargetFieldSnapshot(DataModel model, DataModelPhysicalChange change) {
        List<ModelPhysicalTableChangeTargetField> targetFields = readTargetFieldSnapshot(change);
        Map<UUID, DataModelField> existingFields = fieldRepository
                .findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId()).stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        Set<UUID> retainedIds = new HashSet<>();
        List<DataModelField> fieldsToSave = new ArrayList<>();
        for (ModelPhysicalTableChangeTargetField target : targetFields) {
            DataModelField field;
            if (target.id() == null) {
                field = DataModelField.create(
                        model.getId(), target.code(), target.name(), target.fieldType(), target.length(), target.precision(),
                        target.scale(), target.nullable(), target.primaryKey(), target.sortOrder(), target.description()
                );
            } else {
                field = existingFields.get(target.id());
                if (field == null || !retainedIds.add(target.id())) {
                    throw new IllegalStateException("目标字段快照与当前模型字段不一致");
                }
                field.update(
                        target.code(), target.name(), target.fieldType(), target.length(), target.precision(), target.scale(),
                        target.nullable(), target.primaryKey(), target.sortOrder(), target.description()
                );
            }
            fieldsToSave.add(field);
        }
        List<DataModelField> removedFields = existingFields.values().stream()
                .filter(field -> !retainedIds.contains(field.getId()))
                .toList();
        if (!removedFields.isEmpty()) {
            fieldRepository.deleteAll(removedFields);
            fieldRepository.flush();
        }
        fieldRepository.saveAllAndFlush(fieldsToSave);
    }

    private List<ModelPhysicalTableChangeTargetField> readTargetFieldSnapshot(DataModelPhysicalChange change) {
        try {
            return objectMapper.readValue(change.getTargetFieldsSnapshot(), new TypeReference<List<ModelPhysicalTableChangeTargetField>>() {
            });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("已保存的目标字段快照无效", exception);
        }
    }

    private DataSource requireStorageDataSource(UUID id, boolean requireEnabled) {
        DataSource dataSource = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据存储不存在"));
        if (!dataSource.getPurposes().contains(DataSourcePurpose.STORAGE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型只能绑定具有数据存储用途的数据源");
        }
        if (requireEnabled && !dataSource.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "关联的数据存储已停用");
        }
        return dataSource;
    }

    private List<ExternalTableField> importExternalTableFields(DataSource storage, DataModel model) {
        TableMetadata metadata;
        try {
            metadata = physicalTablePort.readExternalTable(storage, model);
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        } catch (UnsupportedOperationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        if (metadata.columns().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "外部表至少需要一个可导入字段");
        }

        DatabaseDialect dialect = dialectRegistry.require(storage.getType().name());
        List<ExternalColumnMapping> mappings = externalColumnMappings(metadata, dialect);
        List<ExternalTableField> fields = new ArrayList<>();
        for (ExternalColumnMapping mapping : mappings) {
            if (!mapping.importable()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mapping.issue());
            }
            PlatformTypeDefinition platformType = mapping.platformType();
            fields.add(new ExternalTableField(
                    mapping.code(),
                    platformType.type(),
                    platformType.length(),
                    platformType.precision(),
                    platformType.scale(),
                    mapping.column().nullable(),
                    mapping.primaryKey(),
                    fields.size() * 10 + 10,
                    truncateExternalFieldComment(mapping.column().comment())
            ));
        }
        return List.copyOf(fields);
    }

    private static List<ExternalColumnMapping> externalColumnMappings(
            TableMetadata metadata,
            DatabaseDialect dialect
    ) {
        Set<String> primaryKeyCodes = metadata.primaryKey().columns().stream()
                .map(DataModelService::normalizeCode)
                .collect(Collectors.toSet());
        Set<String> codes = new HashSet<>();
        List<ExternalColumnMapping> mappings = new ArrayList<>();
        for (ColumnMetadata column : metadata.columns()) {
            String code = normalizeCode(column.name());
            TypeMappingResult<PlatformTypeDefinition> typeMapping = dialect.mapToPlatformType(
                    JdbcTypeDescriptor.from(column)
            );
            String issue = null;
            if (!LOWER_FIELD_IDENTIFIER.matcher(column.name()).matches() || !column.name().equals(code)) {
                issue = "外部表字段编码不符合当前模型规则（仅支持小写字母、数字和下划线）：" + column.name();
            } else if (!codes.add(code)) {
                issue = "外部表字段编码重复：" + code;
            } else if (!typeMapping.acceptable()) {
                issue = "外部表字段无法安全导入：" + code + "（" + typeMapping.message() + "）";
            }
            mappings.add(new ExternalColumnMapping(
                    column,
                    code,
                    typeMapping.definition(),
                    typeMapping.quality(),
                    typeMapping.message(),
                    issue,
                    primaryKeyCodes.contains(code)
            ));
        }
        return List.copyOf(mappings);
    }

    private static void validateExternalPhysicalTableName(PhysicalTableMode mode, String physicalTableName) {
        if (mode == PhysicalTableMode.EXTERNAL && !LOWER_TABLE_IDENTIFIER.matcher(physicalTableName.trim()).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "绑定已有表暂只支持小写字母、数字和下划线组成的表名"
            );
        }
    }

    private List<DataModelField> saveImportedExternalFields(
            DataModel model,
            List<ExternalTableField> importedFields,
            boolean replaceExisting
    ) {
        if (replaceExisting) {
            fieldRepository.deleteAllByModelId(model.getId());
            fieldRepository.flush();
        }
        List<DataModelField> fields = importedFields.stream()
                .map(field -> DataModelField.create(
                        model.getId(), field.code(), field.code(), field.type(),
                        field.length(), field.precision(), field.scale(), field.nullable(), field.primaryKey(),
                        field.sortOrder(), field.description()
                ))
                .toList();
        List<DataModelField> saved = fieldRepository.saveAllAndFlush(fields);
        if (replaceExisting) {
            model.advanceSchemaVersion();
        }
        return saved;
    }

    private void validateExternalFieldUpdate(
            DataSource storage,
            DataModel model,
            List<NormalizedField> requestedFields
    ) {
        Map<String, ExternalTableField> externalFields = importExternalTableFields(storage, model).stream()
                .collect(Collectors.toMap(ExternalTableField::code, Function.identity()));
        if (requestedFields.size() != externalFields.size()) {
            throw externalFieldStructureChangeException();
        }
        for (NormalizedField requested : requestedFields) {
            ExternalTableField external = externalFields.get(requested.code());
            if (external == null
                    || requested.input().fieldType() != external.type()
                    || !Objects.equals(requested.length(), external.length())
                    || !Objects.equals(requested.precision(), external.precision())
                    || !Objects.equals(requested.scale(), external.scale())
                    || requested.input().nullable() != external.nullable()
                    || requested.input().primaryKey() != external.primaryKey()) {
                throw externalFieldStructureChangeException();
            }
        }
    }

    private static ResponseStatusException externalFieldStructureChangeException() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "外部表字段结构由物理表管理，只能修改字段名称、说明和排序"
        );
    }

    private static String truncateExternalFieldComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 499) + "…";
    }

    private PhysicalNamespace resolvePhysicalNamespace(DataSource storage) {
        if (!storage.getType().isJdbc()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型只能绑定 JDBC 数据存储");
        }
        DatabaseDialect dialect = dialectRegistry.require(storage.getType().name());
        JdbcConnectionConfig connection = storage.getConnection().toJdbcConnectionConfig();
        return new PhysicalNamespace(
                normalizeOptional(dialect.resolveCatalog(connection, null)),
                normalizeOptional(dialect.resolveSchema(connection, null))
        );
    }

    private void requirePhysicalTableMatched(DataSource storage, DataModel model, List<DataModelField> fields) {
        ModelPhysicalTableInspection inspection = physicalTablePort.inspect(storage, model, fields);
        if (inspection.state() != PhysicalTableState.MATCHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "物理表未就绪：" + inspection.message());
        }
    }

    private List<DataModelField> fieldsFor(UUID modelId) {
        return fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(modelId);
    }

    private void requireNoActivePhysicalChange(UUID modelId) {
        List<DataModelPhysicalChange> activeChanges = physicalChangeRepository.findAllByModelIdAndStatusIn(
                modelId, List.of(DataModelPhysicalChangeStatus.PLANNED, DataModelPhysicalChangeStatus.APPLYING)
        );
        if (!activeChanges.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "存在待处理的物理表变更计划，请先取消或完成该计划");
        }
    }

    private void supersedePlannedChanges(UUID modelId) {
        List<DataModelPhysicalChange> activeChanges = physicalChangeRepository.findAllByModelIdAndStatusIn(
                modelId, List.of(DataModelPhysicalChangeStatus.PLANNED, DataModelPhysicalChangeStatus.APPLYING)
        );
        if (activeChanges.stream().anyMatch(change -> change.getStatus() == DataModelPhysicalChangeStatus.APPLYING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "物理表变更正在执行，不能生成新计划");
        }
        activeChanges.forEach(DataModelPhysicalChange::supersede);
        if (!activeChanges.isEmpty()) {
            physicalChangeRepository.saveAll(activeChanges);
        }
    }

    private DataModelPhysicalChangeResponse physicalChangeResponse(DataModelPhysicalChange change) {
        return DataModelPhysicalChangeResponse.from(change, readChangePlan(change));
    }

    private TableChangePlan readChangePlan(DataModelPhysicalChange change) {
        try {
            return objectMapper.readValue(change.getPlanSnapshot(), TableChangePlan.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("已保存的物理表变更计划无效", exception);
        }
    }

    private static ResponseStatusException changeExecutionException(DatabaseAccessException exception) {
        HttpStatus status = switch (exception.code()) {
            case "TABLE_STRUCTURE_DRIFTED", "PRECHECK_FAILED", "EXECUTION_MODE_NOT_AVAILABLE",
                    "DDL_ATOMICITY_NOT_SUPPORTED", "POSTCHECK_FAILED" -> HttpStatus.CONFLICT;
            case "TABLE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new ResponseStatusException(status, exception.getMessage(), exception);
    }

    private static <T> T requireTransactionResult(T result) {
        if (result == null) {
            throw new IllegalStateException("事务未返回预期结果");
        }
        return result;
    }

    private String writeSnapshot(Object value, String message) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(message, exception);
        }
    }

    private static TableDefinition currentTableDefinition(
            DatabaseDialect dialect,
            DataSource storage,
            DataModel model,
            TableIdentifier table,
            List<DataModelField> fields
    ) {
        return new TableDefinition(
                table,
                fields.stream().map(field -> physicalColumn(
                        dialect, field.getCode(), field.getFieldType(), field.getLength(), field.getPrecision(),
                        field.getScale(), field.isNullable(), field.getId()
                )).toList(),
                storage.getType() == DataSourceType.CLICKHOUSE
                        ? List.of()
                        : fields.stream().filter(DataModelField::isPrimaryKey).map(DataModelField::getCode).toList(),
                storageDefinition(storage, model)
        );
    }

    private static TableDefinition targetTableDefinition(
            DatabaseDialect dialect,
            DataSource storage,
            DataModel model,
            TableIdentifier table,
            List<NormalizedField> fields
    ) {
        return new TableDefinition(
                table,
                fields.stream().map(field -> physicalColumn(
                        dialect, field.code(), field.input().fieldType(), field.length(), field.precision(),
                        field.scale(), field.input().nullable(), field.input().id()
                )).toList(),
                storage.getType() == DataSourceType.CLICKHOUSE
                        ? List.of()
                        : fields.stream().filter(field -> field.input().primaryKey()).map(NormalizedField::code).toList(),
                storageDefinition(storage, model)
        );
    }

    private static TableStorageDefinition storageDefinition(DataSource storage, DataModel model) {
        return storage.getType() == DataSourceType.CLICKHOUSE
                ? TableStorageDefinition.mergeTree(model.getClickHouseOrderByColumns())
                : TableStorageDefinition.none();
    }

    private static TableColumnDefinition physicalColumn(
            DatabaseDialect dialect,
            String code,
            PlatformDataType type,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            UUID columnId
    ) {
        TypeMappingResult<PhysicalTypeDefinition> mapping = dialect.mapToPhysicalType(
                platformType(type, length, precision, scale)
        );
        if (!mapping.acceptable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, code + "：" + mapping.message());
        }
        return mapping.definition().column(code, nullable, columnId);
    }

    private static PlatformTypeDefinition platformType(
            PlatformDataType type,
            Integer length,
            Integer precision,
            Integer scale
    ) {
        return switch (type) {
            case STRING -> PlatformTypeDefinition.string(length);
            case DECIMAL -> PlatformTypeDefinition.decimal(precision, scale);
            default -> PlatformTypeDefinition.of(type);
        };
    }

    private static List<String> normalizeClickHouseOrderByColumns(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new java.util.LinkedHashSet<>();
        for (String value : values) {
            String code = normalizeCode(value);
            if (!normalized.add(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ClickHouse 排序键字段不能重复：" + code);
            }
        }
        return List.copyOf(normalized);
    }

    private void validatePhysicalTypeMappings(DataSource storage, List<NormalizedField> fields) {
        DatabaseDialect dialect = dialectRegistry.require(storage.getType().name());
        for (NormalizedField field : fields) {
            TypeMappingResult<PhysicalTypeDefinition> mapping = dialect.mapToPhysicalType(platformType(
                    field.input().fieldType(), field.length(), field.precision(), field.scale()
            ));
            if (!mapping.acceptable()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        field.code() + "：" + mapping.message()
                );
            }
        }
    }

    private static void validateClickHouseStorageConfiguration(
            DataSource storage,
            PhysicalTableMode mode,
            List<String> orderByColumns
    ) {
        if (orderByColumns.isEmpty()) {
            return;
        }
        if (storage.getType() != DataSourceType.CLICKHOUSE || mode != PhysicalTableMode.MANAGED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ClickHouse 排序键只适用于 ClickHouse 数据存储的受控物理表"
            );
        }
    }

    private static void validateClickHouseOrderByFields(
            DataSource storage,
            DataModel model,
            List<String> fieldCodes,
            List<PlatformDataType> fieldTypes
    ) {
        if (storage.getType() != DataSourceType.CLICKHOUSE) {
            return;
        }
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            if (!model.getClickHouseOrderByColumns().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "绑定已有表模式不能配置 ClickHouse 排序键");
            }
            return;
        }
        Set<String> codes = new HashSet<>(fieldCodes);
        for (String orderByColumn : model.getClickHouseOrderByColumns()) {
            if (!codes.contains(orderByColumn)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ClickHouse 排序键字段不存在：" + orderByColumn);
            }
        }
        if (fieldTypes.contains(PlatformDataType.BINARY)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ClickHouse 受控物理表暂不支持二进制字段");
        }
    }

    private static void validateTargetFieldIds(
            List<NormalizedField> targetFields,
            Map<UUID, DataModelField> existingFields
    ) {
        Set<UUID> retainedIds = new HashSet<>();
        for (NormalizedField targetField : targetFields) {
            UUID id = targetField.input().id();
            if (id == null) {
                continue;
            }
            if (!existingFields.containsKey(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型字段不存在或不属于当前模型");
            }
            if (!retainedIds.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型字段 ID 重复");
            }
        }
    }

    private static PhysicalTableMode physicalTableMode(PhysicalTableMode mode) {
        return mode == null ? PhysicalTableMode.MANAGED : mode;
    }

    private static ResponseStatusException remoteAccessException(DatabaseAccessException exception) {
        HttpStatus status = switch (exception.code()) {
            case "TABLE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "INVALID_QUERY" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new ResponseStatusException(status, exception.getMessage(), exception);
    }

    private void validatePhysicalLocationAvailable(
            UUID storageDataSourceId,
            String catalogName,
            String schemaName,
            String physicalTableName,
            UUID currentId
    ) {
        boolean duplicate = currentId == null
                ? repository.existsByStorageDataSourceIdAndCatalogNameAndSchemaNameAndPhysicalTableName(
                        storageDataSourceId, catalogName, schemaName, physicalTableName)
                : repository.existsByStorageDataSourceIdAndCatalogNameAndSchemaNameAndPhysicalTableNameAndIdNot(
                        storageDataSourceId, catalogName, schemaName, physicalTableName, currentId);
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "同一数据存储下的物理表位置已被其他模型使用");
        }
    }

    private static List<NormalizedField> normalizeFields(List<DataModelFieldInput> fields) {
        Set<String> codes = new HashSet<>();
        List<NormalizedField> normalized = new ArrayList<>();
        for (DataModelFieldInput input : fields) {
            String code = normalizeCode(input.code());
            if (!codes.add(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型字段编码不能重复：" + code);
            }
            if (input.primaryKey() && input.nullable()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "主键字段不能为空：" + code);
            }

            Integer length = null;
            Integer precision = null;
            Integer scale = null;
            if (input.fieldType() == PlatformDataType.STRING) {
                length = input.length();
            } else if (input.fieldType() == PlatformDataType.DECIMAL) {
                if (input.precision() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小数字段必须指定精度：" + code);
                }
                precision = input.precision();
                scale = input.scale() == null ? 0 : input.scale();
                if (scale > precision) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "小数字段的小数位不能超过精度：" + code);
                }
            }
            normalized.add(new NormalizedField(input, code, length, precision, scale));
        }
        return normalized;
    }

    private static String normalizeCode(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    record NormalizedField(
            DataModelFieldInput input,
            String code,
            Integer length,
            Integer precision,
            Integer scale
    ) {
    }

    private record DataQueryResult(
            List<DataModelField> fields,
            List<Map<String, Object>> rows,
            int pageNo,
            int pageSize,
            boolean hasNext,
            Long totalCount,
            boolean stableOrder
    ) {
    }

    private record PhysicalNamespace(String catalogName, String schemaName) {
    }

    private record ExternalTableField(
            String code,
            PlatformDataType type,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            boolean primaryKey,
            int sortOrder,
            String description
    ) {
    }

    private record ExternalColumnMapping(
            ColumnMetadata column,
            String code,
            PlatformTypeDefinition platformType,
            TypeMappingQuality quality,
            String mappingMessage,
            String issue,
            boolean primaryKey
    ) {
        boolean importable() {
            return issue == null && quality.acceptable();
        }
    }

    private record CreatePreparation(
            DataModel model,
            DataSource storage,
            PhysicalTableMode physicalTableMode,
            PhysicalNamespace namespace,
            String physicalTableName
    ) {
    }

    private record UpdatePreparation(
            DataModel model,
            Instant expectedUpdatedAt,
            DataSource storage,
            PhysicalNamespace namespace,
            String physicalTableName,
            PhysicalTableMode physicalTableMode,
            List<String> clickHouseOrderByColumns,
            boolean physicalLocationChanged,
            boolean physicalDefinitionChanged,
            boolean importExternalFields,
            List<DataModelField> currentFields,
            boolean requiresMissingTableCheck
    ) {
    }

    private record UpdateFieldsPreparation(
            DataModel model,
            Instant expectedUpdatedAt,
            DataSource storage,
            List<NormalizedField> normalizedFields,
            List<DataModelField> currentFields
    ) {
    }

    private record ChangePlanPreparation(
            DataModel model,
            Instant expectedUpdatedAt,
            int schemaVersion,
            DataSource storage,
            List<DataModelField> currentFields,
            List<NormalizedField> targetFields
    ) {
    }

    private record ModelOperationPreparation(
            DataModel model,
            Instant expectedUpdatedAt,
            DataSource storage,
            List<DataModelField> fields
    ) {
    }

    private record ChangeExecutionContext(
            DataSource storage,
            DataModel model,
            TableChangePlan plan,
            TableChangeExecutionMode mode
    ) {
    }
}
