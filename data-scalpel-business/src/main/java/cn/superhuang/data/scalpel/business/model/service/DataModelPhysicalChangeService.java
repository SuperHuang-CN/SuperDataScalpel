package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChange;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChangeStatus;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelPhysicalChangeRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelPhysicalStatisticsRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.web.request.CreatePhysicalTableChangePlanRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ExecutePhysicalTableChangePlanRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPhysicalChangeResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PhysicalTableDdlPlanResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PhysicalTableInspectionResponse;
import cn.superhuang.data.scalpel.business.quality.service.ModelQualityRuleService;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableStorageDefinition;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Instant;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.ModelOperationPreparation;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.NormalizedField;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.normalizeFields;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.platformType;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.remoteAccessException;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.requireTransactionResult;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.validateClickHouseOrderByFields;

@Service
public class DataModelPhysicalChangeService {


    private final DataModelRepository repository;
    private final DataModelFieldRepository fieldRepository;
    private final DataModelPhysicalChangeRepository physicalChangeRepository;
    private final DataModelPhysicalStatisticsRepository physicalStatisticsRepository;
    private final SearchEngine searchEngine;
    private final ModelPhysicalTablePort physicalTablePort;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final DialectRegistry dialectRegistry;
    private final ModelQualityRuleService qualityRuleService;
    private final cn.superhuang.data.scalpel.business.metric.service.MetricReferenceGuard metricReferenceGuard;
    private final cn.superhuang.data.scalpel.business.ontology.service.BusinessObjectTypeReferenceGuard businessObjectTypeReferenceGuard;
    private final ModelDefinitionService definitions;
    public DataModelPhysicalChangeService(
            DataModelRepository repository,
            DataModelFieldRepository fieldRepository,
            DataModelPhysicalChangeRepository physicalChangeRepository,
            DataModelPhysicalStatisticsRepository physicalStatisticsRepository,
            SearchEngine searchEngine,
            ModelPhysicalTablePort physicalTablePort,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager,
            DialectRegistry dialectRegistry,
            ModelQualityRuleService qualityRuleService,
            cn.superhuang.data.scalpel.business.metric.service.MetricReferenceGuard metricReferenceGuard,
            cn.superhuang.data.scalpel.business.ontology.service.BusinessObjectTypeReferenceGuard businessObjectTypeReferenceGuard,
            ModelDefinitionService definitions
    ) {
        this.repository = repository;
        this.fieldRepository = fieldRepository;
        this.physicalChangeRepository = physicalChangeRepository;
        this.physicalStatisticsRepository = physicalStatisticsRepository;
        this.searchEngine = searchEngine;
        this.physicalTablePort = physicalTablePort;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dialectRegistry = dialectRegistry;
        this.qualityRuleService = qualityRuleService;
        this.metricReferenceGuard = metricReferenceGuard;
        this.businessObjectTypeReferenceGuard = businessObjectTypeReferenceGuard;
        this.definitions = definitions;
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
        DataModel model = definitions.requireModel(id);
        if (model.getStatus() != DataModelStatus.DRAFT && model.getStatus() != DataModelStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先停用已发布模型后再修改物理表结构");
        }
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "绑定已有表模式暂不支持修改物理表");
        }

        DataSource storage = definitions.requireStorageDataSource(model.getStorageDataSourceId(), true);
        List<DataModelField> currentFields = definitions.fieldsFor(id);
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
        if (containsGeometry(currentFields)) {
            requireSpatialConstraintOnlyChange(currentFields, targetFields);
        }
        definitions.validateStandardDictionaryAssignments(targetFields, existingFields);
        return new ChangePlanPreparation(
                model, model.getUpdatedAt(), model.getSchemaVersion(), storage, currentFields, targetFields
        );
    }

    private DataModelPhysicalChangeResponse completeChangePlan(
            UUID id,
            ChangePlanPreparation preparation,
            TableChangePlan plan
    ) {
        DataModel model = definitions.requireModel(id);
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
        definitions.requireModel(modelId);
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
        definitions.requireModel(modelId);
        return physicalChangeResponse(requirePhysicalChange(modelId, planId));
    }

    @Transactional
    public DataModelPhysicalChangeResponse cancelPhysicalTableChangePlan(UUID modelId, UUID planId) {
        definitions.requireModel(modelId);
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

    private static boolean containsGeometry(List<DataModelField> fields) {
        return fields.stream().anyMatch(field -> field.getFieldType() == PlatformDataType.GEOMETRY);
    }

    private static void requireSpatialConstraintOnlyChange(
            List<DataModelField> currentFields,
            List<NormalizedField> requestedFields
    ) {
        if (currentFields.size() != requestedFields.size()) {
            throw spatialStructureChangeNotSupported();
        }
        Map<UUID, DataModelField> currentById = currentFields.stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        for (NormalizedField requested : requestedFields) {
            DataModelField current = requested.input().id() == null
                    ? null
                    : currentById.get(requested.input().id());
            if (current == null
                    || !current.getCode().equals(requested.code())
                    || current.getFieldType() != requested.input().fieldType()
                    || !Objects.equals(current.getLength(), requested.length())
                    || !Objects.equals(current.getPrecision(), requested.precision())
                    || !Objects.equals(current.getScale(), requested.scale())
                    || !Objects.equals(current.getGeometry(), requested.geometry())) {
                throw spatialStructureChangeNotSupported();
            }
        }
    }

    private static ResponseStatusException spatialStructureChangeNotSupported() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "空间字段所在受管表当前仅支持修改可空性和非空间字段主键约束"
        );
    }

    public PhysicalTableInspectionResponse inspectPhysicalTable(UUID id) {
        ModelOperationPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            DataModel model = definitions.requireModel(id);
            return new ModelOperationPreparation(
                    model, model.getUpdatedAt(),
                    definitions.requireModelDataSource(model.getStorageDataSourceId(), false, model.getPhysicalTableMode()),
                    definitions.fieldsFor(id)
            );
        }));
        return PhysicalTableInspectionResponse.from(
                preparation.model().getPhysicalTableMode(),
                physicalTablePort.inspect(preparation.storage(), preparation.model(), preparation.fields())
        );
    }

    public PhysicalTableDdlPlanResponse physicalTableDdl(UUID id) {
        ModelOperationPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            DataModel model = definitions.requireModel(id);
            return new ModelOperationPreparation(
                    model,
                    model.getUpdatedAt(),
                    definitions.requireModelDataSource(
                            model.getStorageDataSourceId(), false, model.getPhysicalTableMode()
                    ),
                    definitions.fieldsFor(id)
            );
        }));
        DataModel model = preparation.model();
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            return PhysicalTableDdlPlanResponse.unsupported(
                    model.getPhysicalTableMode(), "绑定已有表模式不生成建表 SQL"
            );
        }
        List<DataModelField> fields = preparation.fields();
        if (fields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先定义至少一个模型字段");
        }
        DataSource storage = preparation.storage();
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
        DataModel model = definitions.requireModel(id);
        if (model.getStatus() != DataModelStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿模型可以创建物理表");
        }
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "绑定已有表模式不能创建物理表");
        }
        List<DataModelField> fields = definitions.fieldsFor(id);
        if (fields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先定义至少一个模型字段");
        }
        DataSource storage = definitions.requireStorageDataSource(model.getStorageDataSourceId(), true);
        validateClickHouseOrderByFields(
                storage,
                model,
                fields.stream().map(DataModelField::getCode).toList(),
                fields.stream().map(DataModelField::getFieldType).toList()
        );
        PhysicalTableInspectionResponse response = PhysicalTableInspectionResponse.from(
                model.getPhysicalTableMode(), physicalTablePort.create(storage, model, fields)
        );
        if (response.state() == PhysicalTableState.MATCHED) {
            transactionTemplate.executeWithoutResult(status -> physicalStatisticsRepository.deleteByModelId(id));
        }
        return response;
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
        DataModel model = definitions.requireModel(modelId);
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
        Set<UUID> retainedFieldIds = readTargetFieldSnapshot(change).stream()
                .map(ModelPhysicalTableChangeTargetField::id).filter(Objects::nonNull).collect(Collectors.toSet());
        metricReferenceGuard.assertFieldsRemovable(modelId, fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(modelId)
                .stream().map(DataModelField::getId).filter(fieldId -> !retainedFieldIds.contains(fieldId)).toList());
        businessObjectTypeReferenceGuard.assertFieldsRemovable(modelId, fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(modelId)
                .stream().map(DataModelField::getId).filter(fieldId -> !retainedFieldIds.contains(fieldId)).toList());
        TableChangePlan plan = readChangePlan(change);
        if (!change.getBeforeFingerprint().equals(plan.beforeFingerprint().value())
                || !change.getTargetFingerprint().equals(plan.targetFingerprint().value())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "物理表变更规则版本已变化，请重新生成计划"
            );
        }
        try {
            plan.requireExecutionOption(mode);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前计划不提供所选执行方式", exception);
        }
        DataSource storage = definitions.requireStorageDataSource(model.getStorageDataSourceId(), true);
        change.startApplying(mode);
        physicalChangeRepository.saveAndFlush(change);
        return new ChangeExecutionContext(storage, model, plan, mode);
    }

    private DataModelPhysicalChangeResponse completeChangeExecution(UUID modelId, UUID planId) {
        DataModel model = definitions.requireModel(modelId);
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
        qualityRuleService.reconcileModelFields(modelId);
        physicalStatisticsRepository.deleteByModelId(modelId);
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
                        target.scale(), target.geometry(), target.nullable(), target.primaryKey(),
                        target.sortOrder(), target.description()
                );
            } else {
                field = existingFields.get(target.id());
                if (field == null || !retainedIds.add(target.id())) {
                    throw new IllegalStateException("目标字段快照与当前模型字段不一致");
                }
                field.update(
                        target.code(), target.name(), target.fieldType(), target.length(), target.precision(), target.scale(),
                        target.geometry(), target.nullable(), target.primaryKey(), target.sortOrder(), target.description()
                );
            }
            field.assignStandardDictionary(target.standardDictionaryId());
            fieldsToSave.add(field);
        }
        List<DataModelField> removedFields = existingFields.values().stream()
                .filter(field -> !retainedIds.contains(field.getId()))
                .toList();
        if (!removedFields.isEmpty()) {
            metricReferenceGuard.assertFieldsRemovable(model.getId(), removedFields.stream().map(DataModelField::getId).toList());
            businessObjectTypeReferenceGuard.assertFieldsRemovable(model.getId(), removedFields.stream().map(DataModelField::getId).toList());
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
                        field.getScale(), field.getGeometry(), field.isNullable(), field.getId()
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
                        field.scale(), field.geometry(), field.input().nullable(), field.input().id()
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
            GeometryTypeDefinition geometry,
            boolean nullable,
            UUID columnId
    ) {
        TypeMappingResult<PhysicalTypeDefinition> mapping = dialect.mapToPhysicalType(
                platformType(type, length, precision, scale, geometry)
        );
        if (!mapping.acceptable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, code + "：" + mapping.message());
        }
        return mapping.definition().column(code, nullable, columnId);
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

    private record ChangePlanPreparation(
            DataModel model,
            Instant expectedUpdatedAt,
            int schemaVersion,
            DataSource storage,
            List<DataModelField> currentFields,
            List<NormalizedField> targetFields
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
