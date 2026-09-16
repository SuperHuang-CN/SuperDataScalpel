package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDataset;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetField;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetParseStatus;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetTable;
import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetType;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetFieldRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetRepository;
import cn.superhuang.data.scalpel.business.filedataset.repository.FileDatasetTableRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalColumnRole;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChange;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalChangeStatus;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.ModelWarehouseLayer;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelPhysicalChangeRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelPhysicalStatisticsRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelWarehouseLayerRepository;
import cn.superhuang.data.scalpel.business.model.web.request.CreatePhysicalTableChangePlanRequest;
import cn.superhuang.data.scalpel.business.model.web.request.CreateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.request.CreateManagedDraftRequest;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryRequest;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelFieldInput;
import cn.superhuang.data.scalpel.business.model.web.request.ExecutePhysicalTableChangePlanRequest;
import cn.superhuang.data.scalpel.business.model.web.request.FileDatasetImportPreviewRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ImportModelMetadataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ImportModelMetadataRequest;
import cn.superhuang.data.scalpel.business.model.web.request.ManagedImportPreviewRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelFieldsRequest;
import cn.superhuang.data.scalpel.business.model.web.request.UpdateDataModelRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDetailResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDataQueryResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelFieldResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPhysicalChangeResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPhysicalStatisticsResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ExternalTableImportColumnResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ExternalTableImportPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.FileDatasetImportColumnResponse;
import cn.superhuang.data.scalpel.business.model.web.response.FileDatasetImportPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ManagedImportColumnResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ManagedImportPreviewResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ImportedModelMetadataResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ModelMetadataImportResultResponse;
import cn.superhuang.data.scalpel.business.model.web.response.ModelWarehouseLayerSummaryResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PlatformTypeCapabilityResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PhysicalTableDdlPlanResponse;
import cn.superhuang.data.scalpel.business.model.web.response.PhysicalTableInspectionResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableIdentifierResponse;
import cn.superhuang.data.scalpel.business.quality.service.ModelQualityRuleService;
import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryValueSupport;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingQuality;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.search.SearchEngine;
import cn.superhuang.data.scalpel.web.error.CodedProblemException;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.time.Instant;

import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.NormalizedField;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.normalizeCode;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.normalizeFields;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.platformType;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.remoteAccessException;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.requireTransactionResult;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.validateClickHouseOrderByFields;

@Service
public class DataModelService {


    private static final Pattern LOWER_TABLE_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,127}");
    private static final Pattern LOWER_FIELD_IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,63}");

    private final DataModelRepository repository;
    private final DataModelFieldRepository fieldRepository;
    private final DataModelPhysicalChangeRepository physicalChangeRepository;
    private final DataModelPhysicalStatisticsRepository physicalStatisticsRepository;
    private final DataSourceRepository dataSourceRepository;
    private final FileDatasetRepository fileDatasetRepository;
    private final FileDatasetTableRepository fileDatasetTableRepository;
    private final FileDatasetFieldRepository fileDatasetFieldRepository;
    private final ModelWarehouseLayerRepository warehouseLayerRepository;
    private final DirectoryService directoryService;
    private final SearchEngine searchEngine;
    private final ModelPhysicalTablePort physicalTablePort;
    private final TransactionTemplate transactionTemplate;
    private final DialectRegistry dialectRegistry;
    private final StandardDictionaryValueSupport standardDictionaryValueSupport;
    private final ModelQualityRuleService qualityRuleService;
    private final cn.superhuang.data.scalpel.business.metric.service.MetricReferenceGuard metricReferenceGuard;
    private final cn.superhuang.data.scalpel.business.ontology.service.BusinessObjectTypeReferenceGuard businessObjectTypeReferenceGuard;
    private final DataModelReferenceQueryService referenceQueryService;
    private final ModelDefinitionService definitions;
    private final DataModelDataQueryService dataQueries;
    private final DataModelPhysicalChangeService physicalChanges;
    public DataModelService(
            DataModelRepository repository,
            DataModelFieldRepository fieldRepository,
            DataModelPhysicalChangeRepository physicalChangeRepository,
            DataModelPhysicalStatisticsRepository physicalStatisticsRepository,
            DataSourceRepository dataSourceRepository,
            FileDatasetRepository fileDatasetRepository,
            FileDatasetTableRepository fileDatasetTableRepository,
            FileDatasetFieldRepository fileDatasetFieldRepository,
            ModelWarehouseLayerRepository warehouseLayerRepository,
            DirectoryService directoryService,
            SearchEngine searchEngine,
            ModelPhysicalTablePort physicalTablePort,
            PlatformTransactionManager transactionManager,
            DialectRegistry dialectRegistry,
            StandardDictionaryValueSupport standardDictionaryValueSupport,
            ModelQualityRuleService qualityRuleService,
            cn.superhuang.data.scalpel.business.metric.service.MetricReferenceGuard metricReferenceGuard,
            cn.superhuang.data.scalpel.business.ontology.service.BusinessObjectTypeReferenceGuard businessObjectTypeReferenceGuard,
            DataModelReferenceQueryService referenceQueryService,
            ModelDefinitionService definitions,
            DataModelDataQueryService dataQueries,
            DataModelPhysicalChangeService physicalChanges
    ) {
        this.repository = repository;
        this.fieldRepository = fieldRepository;
        this.physicalChangeRepository = physicalChangeRepository;
        this.physicalStatisticsRepository = physicalStatisticsRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.fileDatasetRepository = fileDatasetRepository;
        this.fileDatasetTableRepository = fileDatasetTableRepository;
        this.fileDatasetFieldRepository = fileDatasetFieldRepository;
        this.warehouseLayerRepository = warehouseLayerRepository;
        this.directoryService = directoryService;
        this.searchEngine = searchEngine;
        this.physicalTablePort = physicalTablePort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.dialectRegistry = dialectRegistry;
        this.standardDictionaryValueSupport = standardDictionaryValueSupport;
        this.qualityRuleService = qualityRuleService;
        this.metricReferenceGuard = metricReferenceGuard;
        this.businessObjectTypeReferenceGuard = businessObjectTypeReferenceGuard;
        this.referenceQueryService = referenceQueryService;
        this.definitions = definitions;
        this.dataQueries = dataQueries;
        this.physicalChanges = physicalChanges;
    }




    @Transactional(readOnly = true)
    public PageResponse<DataModelResponse> search(SearchRequest request) {
        Page<DataModel> result = searchEngine.search(request, DataModel.class, repository);
        Map<UUID, String> storageNames = storageNames(result.getContent());
        Map<UUID, ModelWarehouseLayer> warehouseLayers = warehouseLayers(result.getContent());
        Map<UUID, DataModelPhysicalStatisticsResponse> physicalStatistics = physicalStatistics(result.getContent());
        return new PageResponse<>(
                result.getContent().stream()
                        .map(model -> DataModelResponse.from(
                                model,
                                storageNames.get(model.getStorageDataSourceId()),
                                ModelWarehouseLayerSummaryResponse.from(
                                        model.getWarehouseLayerId() == null
                                                ? null
                                                : warehouseLayers.get(model.getWarehouseLayerId())
                                ),
                                physicalStatistics.get(model.getId())
                        ))
                        .toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataModelDetailResponse get(UUID id) {
        return detail(definitions.requireModel(id));
    }

    public ExternalTableImportPreviewResponse previewExternalTableImport(
            UUID storageDataSourceId,
            String physicalTableName
    ) {
        DataSource storage = definitions.requireModelDataSource(storageDataSourceId, true, PhysicalTableMode.EXTERNAL);
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
                        mapping.platformType() == null ? null : mapping.platformType().geometry(),
                        mapping.column().nullable(),
                        mapping.primaryKey(),
                        mapping.quality(),
                        mapping.mappingMessage(),
                        mapping.importable(),
                        truncateExternalFieldComment(mapping.column().comment()),
                        mapping.column().role().name()
                ))
                .toList();
        return new ExternalTableImportPreviewResponse(TableIdentifierResponse.from(table), !columns.isEmpty() && issues.isEmpty(), columns, issues);
    }

    public ManagedImportPreviewResponse previewManagedImport(ManagedImportPreviewRequest request) {
        DataSource source = definitions.requireModelDataSource(
                request.sourceDataSourceId(), true, PhysicalTableMode.EXTERNAL
        );
        DataSource target = definitions.requireStorageDataSource(request.targetStorageDataSourceId(), true);
        TableIdentifier requestedTable = request.sourceTable().toIdentifier();
        TableMetadata metadata;
        try {
            metadata = physicalTablePort.readExternalTable(source, requestedTable);
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        } catch (UnsupportedOperationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }

        DatabaseDialect sourceDialect = dialectRegistry.require(source.getType().name());
        DatabaseDialect targetDialect = dialectRegistry.require(target.getType().name());
        List<ManagedImportColumnResponse> columns = managedImportColumns(metadata, sourceDialect, targetDialect);
        LinkedHashSet<String> tableIssues = new LinkedHashSet<>();
        String spatialRuntimeIssue = validateManagedImportSpatialRuntime(target, columns);
        if (spatialRuntimeIssue != null) {
            tableIssues.add(spatialRuntimeIssue);
        }
        if (!"TABLE".equalsIgnoreCase(metadata.table().type())) {
            tableIssues.add("只能导入普通物理表，当前对象类型为：" + metadata.table().type());
        }
        if (columns.isEmpty()) {
            tableIssues.add("源表至少需要一个可导入字段");
        }
        LinkedHashSet<String> issues = new LinkedHashSet<>(tableIssues);
        columns.forEach(column -> issues.addAll(column.issues()));

        String sourceTableName = metadata.table().identifier().table();
        String normalizedTableName = normalizeCode(sourceTableName);
        String suggestedCode = LOWER_FIELD_IDENTIFIER.matcher(normalizedTableName).matches()
                ? normalizedTableName
                : "";
        String suggestedPhysicalTableName = LOWER_TABLE_IDENTIFIER.matcher(normalizedTableName).matches()
                ? normalizedTableName
                : "";
        String tableComment = normalizeOptional(metadata.table().comment());
        String suggestedName = truncateText(tableComment == null ? sourceTableName : tableComment, 100);
        List<String> warnings = managedImportWarnings(metadata);
        return new ManagedImportPreviewResponse(
                TableIdentifierResponse.from(metadata.table().identifier()),
                suggestedCode,
                suggestedName,
                suggestedPhysicalTableName,
                tableIssues.isEmpty(),
                !columns.isEmpty() && issues.isEmpty(),
                columns,
                List.copyOf(tableIssues),
                List.copyOf(issues),
                warnings
        );
    }

    @Transactional(readOnly = true)
    public FileDatasetImportPreviewResponse previewFileDatasetImport(FileDatasetImportPreviewRequest request) {
        FileDataset dataset = fileDatasetRepository.findById(request.fileDatasetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集不存在"));
        FileDatasetTable table = fileDatasetTableRepository
                .findByIdAndFileDatasetId(request.fileDatasetTableId(), request.fileDatasetId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文件数据集逻辑表不存在"));
        if (table.getParseStatus() != FileDatasetParseStatus.READY
                && table.getParseStatus() != FileDatasetParseStatus.SCHEMA_READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件数据集逻辑表尚未完成结构解析");
        }

        List<FileDatasetField> sourceFields = fileDatasetFieldRepository
                .findByFileDatasetTableIdOrderBySortOrderAsc(table.getId());
        if (sourceFields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文件数据集逻辑表没有可创建模型的字段结构");
        }

        DataSource target = definitions.requireStorageDataSource(request.targetStorageDataSourceId(), true);
        DatabaseDialect targetDialect = dialectRegistry.require(target.getType().name());
        List<FileDatasetImportColumnResponse> columns = fileDatasetImportColumns(sourceFields, targetDialect);
        LinkedHashSet<String> issues = new LinkedHashSet<>();
        columns.forEach(column -> issues.addAll(column.issues()));

        String normalizedTableCode = normalizeCode(table.getCode());
        String suggestedCode = LOWER_FIELD_IDENTIFIER.matcher(normalizedTableCode).matches()
                ? normalizedTableCode : "";
        String suggestedPhysicalTableName = LOWER_TABLE_IDENTIFIER.matcher(normalizedTableCode).matches()
                ? normalizedTableCode : "";
        int unresolvedCount = (int) columns.stream().filter(column -> !column.importable()).count();
        List<String> warnings = List.of(fileDatasetSchemaSourceWarning(dataset.getType()));
        return new FileDatasetImportPreviewResponse(
                dataset.getId(), dataset.getName(), dataset.getType(),
                table.getId(), table.getCode(), table.getName(), table.getParseStatus(), table.getUpdatedAt(),
                suggestedCode, truncateText(table.getName(), 100), suggestedPhysicalTableName,
                true, issues.isEmpty(), unresolvedCount, columns, List.of(), List.copyOf(issues), warnings
        );
    }

    private String validateManagedImportSpatialRuntime(
            DataSource target,
            List<ManagedImportColumnResponse> columns
    ) {
        if (columns.stream().noneMatch(column -> column.fieldType() == PlatformDataType.GEOMETRY)
                || columns.stream().anyMatch(column -> !column.importable())) {
            return null;
        }
        PhysicalNamespace namespace = resolvePhysicalNamespace(target);
        DataModel previewModel = DataModel.create(
                "spatial_import_preview",
                "空间字段导入能力预览",
                null,
                target.getId(),
                namespace.catalogName(),
                namespace.schemaName(),
                "datascalpel_spatial_import_preview",
                PhysicalTableMode.MANAGED,
                List.of(),
                null
        );
        UUID previewModelId = UUID.randomUUID();
        List<DataModelField> previewFields = columns.stream()
                .map(column -> DataModelField.create(
                        previewModelId,
                        column.code(),
                        column.name(),
                        column.fieldType(),
                        column.length(),
                        column.precision(),
                        column.scale(),
                        column.geometry(),
                        column.nullable(),
                        column.fieldType() == PlatformDataType.GEOMETRY ? false : column.primaryKey(),
                        column.sortOrder(),
                        column.description()
                ))
                .toList();
        try {
            physicalTablePort.planCreate(target, previewModel, previewFields);
            return null;
        } catch (DatabaseAccessException | UnsupportedOperationException | IllegalArgumentException exception) {
            return "目标空间能力校验失败：" + exception.getMessage();
        }
    }

    public DataModelDetailResponse createManagedDraft(CreateManagedDraftRequest request) {
        ManagedDraftPreparation preparation = prepareManagedDraft(request);
        inspectManagedDraftTarget(preparation);
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeManagedDraft(request, preparation)
        ));
    }

    public ModelMetadataImportResultResponse importModelMetadata(ImportModelMetadataRequest request) {
        DirectoryService.DirectoryPathIndex directoryPaths = directoryService.pathIndex(DirectoryScope.MODEL);
        boolean usesDirectories = request.models().stream()
                .anyMatch(model -> model.directoryPath() != null && !model.directoryPath().isBlank());
        if (usesDirectories && !directoryPaths.issues().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "模型目录树无法用于 Excel 导入：" + String.join("；", directoryPaths.issues())
            );
        }
        List<String> directoryIssues = new ArrayList<>();
        List<CreateManagedDraftRequest> drafts = new ArrayList<>(request.models().size());
        for (ImportModelMetadataModelRequest model : request.models()) {
            DirectoryService.DirectoryPathResolution directory = directoryPaths.resolve(model.directoryPath());
            if (!directory.resolved()) {
                directoryIssues.add("模型“" + model.code() + "”：" + directory.issue());
                continue;
            }
            drafts.add(new CreateManagedDraftRequest(
                    model.code(),
                    model.name(),
                    directory.directoryId(),
                    model.warehouseLayerId(),
                    request.targetStorageDataSourceId(),
                    model.physicalTableName(),
                    model.clickHouseOrderByColumns(),
                    model.description(),
                    model.fields()
            ));
        }
        if (!directoryIssues.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("；", directoryIssues));
        }
        validateMetadataImportBatchUniqueness(drafts);
        List<ManagedDraftPreparation> preparations = drafts.stream()
                .map(this::prepareManagedDraft)
                .toList();
        preparations.forEach(this::inspectManagedDraftTarget);
        List<DataModelDetailResponse> imported = requireTransactionResult(transactionTemplate.execute(status -> {
            validateMetadataImportDirectoriesAtCommit(request.models(), drafts);
            List<DataModelDetailResponse> details = new ArrayList<>(drafts.size());
            for (int index = 0; index < drafts.size(); index++) {
                details.add(completeManagedDraft(drafts.get(index), preparations.get(index)));
            }
            return List.copyOf(details);
        }));
        return new ModelMetadataImportResultResponse(
                imported.size(),
                request.models().stream().mapToInt(model -> model.fields().size()).sum(),
                imported.stream().map(detail -> new ImportedModelMetadataResponse(
                        detail.model().id(),
                        detail.model().code(),
                        detail.model().name(),
                        detail.model().directoryId()
                )).toList()
        );
    }

    private void validateMetadataImportDirectoriesAtCommit(
            List<ImportModelMetadataModelRequest> models,
            List<CreateManagedDraftRequest> drafts
    ) {
        DirectoryService.DirectoryPathIndex directoryPaths = directoryService.pathIndex(DirectoryScope.MODEL);
        boolean usesDirectories = models.stream()
                .anyMatch(model -> model.directoryPath() != null && !model.directoryPath().isBlank());
        if (usesDirectories && !directoryPaths.issues().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "模型目录树在创建前发生变化：" + String.join("；", directoryPaths.issues())
            );
        }
        List<String> issues = new ArrayList<>();
        for (int index = 0; index < models.size(); index++) {
            ImportModelMetadataModelRequest model = models.get(index);
            DirectoryService.DirectoryPathResolution directory = directoryPaths.resolve(model.directoryPath());
            if (!directory.resolved()) {
                issues.add("模型“" + model.code() + "”：" + directory.issue());
            } else if (!Objects.equals(directory.directoryId(), drafts.get(index).directoryId())) {
                issues.add("模型“" + model.code() + "”：模型目录在创建前发生变化，请重新校对 Excel");
            }
        }
        if (!issues.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.join("；", issues));
        }
    }

    private void inspectManagedDraftTarget(ManagedDraftPreparation preparation) {
        ModelPhysicalTableInspection inspection;
        try {
            inspection = physicalTablePort.inspect(
                    preparation.storage(), preparation.model(), List.of()
            );
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
        if (inspection.state() != PhysicalTableState.NOT_FOUND) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "目标物理表必须不存在，当前状态为 " + inspection.state() + "：" + inspection.message()
            );
        }
    }

    private static void validateMetadataImportBatchUniqueness(List<CreateManagedDraftRequest> drafts) {
        Set<String> modelCodes = new HashSet<>();
        Set<String> physicalTableNames = new HashSet<>();
        List<String> issues = new ArrayList<>();
        for (CreateManagedDraftRequest draft : drafts) {
            String code = draft.code().trim().toLowerCase(Locale.ROOT);
            if (!modelCodes.add(code)) {
                issues.add("文件内模型编码重复：" + code);
            }
            String physicalTableName = draft.physicalTableName().trim().toLowerCase(Locale.ROOT);
            if (!physicalTableNames.add(physicalTableName)) {
                issues.add("文件内目标物理表名重复：" + physicalTableName);
            }
        }
        if (!issues.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("；", issues));
        }
    }

    private ManagedDraftPreparation prepareManagedDraft(CreateManagedDraftRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型编码已存在");
        }
        directoryService.validateAssignment(DirectoryScope.MODEL, request.directoryId());
        validateWarehouseLayerAssignment(request.warehouseLayerId(), null);
        DataSource storage = definitions.requireStorageDataSource(request.storageDataSourceId(), true);
        PhysicalNamespace namespace = resolvePhysicalNamespace(storage);
        String physicalTableName = normalizeCode(request.physicalTableName());
        List<String> clickHouseOrderByColumns = normalizeClickHouseOrderByColumns(request.clickHouseOrderByColumns());
        validateClickHouseStorageConfiguration(storage, PhysicalTableMode.MANAGED, clickHouseOrderByColumns);
        validatePhysicalLocationAvailable(
                request.storageDataSourceId(), namespace.catalogName(), namespace.schemaName(), physicalTableName, null
        );
        List<NormalizedField> fields = normalizeFields(request.fields());
        if (fields.stream().anyMatch(field -> field.input().id() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新建受管模型草稿的字段不能携带 ID");
        }
        definitions.validateStandardDictionaryAssignments(fields, Map.of());
        validatePhysicalTypeMappings(storage, fields);
        DataModel model = DataModel.create(
                code, request.name(), request.directoryId(), request.warehouseLayerId(), request.storageDataSourceId(),
                namespace.catalogName(), namespace.schemaName(), physicalTableName,
                PhysicalTableMode.MANAGED, clickHouseOrderByColumns, request.description()
        );
        validateClickHouseOrderByFields(
                storage,
                model,
                fields.stream().map(NormalizedField::code).toList(),
                fields.stream().map(field -> field.input().fieldType()).toList()
        );
        return new ManagedDraftPreparation(model, storage, namespace, physicalTableName, fields);
    }

    private DataModelDetailResponse completeManagedDraft(
            CreateManagedDraftRequest request,
            ManagedDraftPreparation preparation
    ) {
        if (repository.existsByCode(preparation.model().getCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型编码已存在");
        }
        directoryService.validateAssignment(DirectoryScope.MODEL, request.directoryId());
        validateWarehouseLayerAssignment(request.warehouseLayerId(), null);
        definitions.requireStorageDataSource(request.storageDataSourceId(), true);
        validatePhysicalLocationAvailable(
                request.storageDataSourceId(),
                preparation.namespace().catalogName(),
                preparation.namespace().schemaName(),
                preparation.physicalTableName(),
                null
        );
        DataModel saved = repository.saveAndFlush(preparation.model());
        definitions.validateStandardDictionaryAssignments(preparation.fields(), Map.of());
        List<DataModelField> fields = preparation.fields().stream()
                .map(field -> {
                    DataModelField entity = DataModelField.create(
                            saved.getId(), field.code(), field.input().name(), field.input().fieldType(),
                            field.length(), field.precision(), field.scale(), field.geometry(), field.input().nullable(),
                            field.input().primaryKey(), field.input().sortOrder(), field.input().description()
                    );
                    entity.assignStandardDictionary(field.input().standardDictionaryId());
                    return entity;
                })
                .toList();
        fieldRepository.saveAllAndFlush(fields);
        return detail(saved);
    }

    @Transactional(readOnly = true)
    public List<PlatformTypeCapabilityResponse> platformTypeCapabilities(UUID storageDataSourceId) {
        DataSource storage = definitions.requireStorageDataSource(storageDataSourceId, false);
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
                        unbounded.acceptable(),
                        List.of(),
                        List.of(),
                        List.of()
                ));
                continue;
            }
            if (type == PlatformDataType.GEOMETRY) {
                TypeMappingResult<PhysicalTypeDefinition> mapping = dialect.mapToPhysicalType(
                        PlatformTypeDefinition.geometry(new GeometryTypeDefinition(
                                GeometryKind.POINT, CrsReference.epsg(4326), CoordinateDimension.XY
                        ))
                );
                result.add(new PlatformTypeCapabilityResponse(
                        type, mapping.acceptable(), mapping.message(), false, false,
                        mapping.acceptable() ? List.of(GeometryKind.values()) : List.of(),
                        mapping.acceptable() ? List.of(CoordinateDimension.XY) : List.of(),
                        mapping.acceptable() ? List.of("EPSG") : List.of()
                ));
                continue;
            }
            PlatformTypeDefinition definition = type == PlatformDataType.DECIMAL
                    ? PlatformTypeDefinition.decimal(38, 18)
                    : PlatformTypeDefinition.of(type);
            TypeMappingResult<PhysicalTypeDefinition> mapping = dialect.mapToPhysicalType(definition);
            result.add(new PlatformTypeCapabilityResponse(
                    type, mapping.acceptable(), mapping.message(), false, false,
                    List.of(), List.of(), List.of()
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
        validateWarehouseLayerAssignment(request.warehouseLayerId(), null);
        PhysicalTableMode physicalTableMode = physicalTableMode(request.physicalTableMode());
        DataSource storage = definitions.requireModelDataSource(request.storageDataSourceId(), false, physicalTableMode);

        PhysicalNamespace namespace = resolvePhysicalNamespace(storage);
        String physicalTableName = normalizeCode(request.physicalTableName());
        validateExternalPhysicalTableName(physicalTableMode, request.physicalTableName());
        List<String> clickHouseOrderByColumns = normalizeClickHouseOrderByColumns(request.clickHouseOrderByColumns());
        validateClickHouseStorageConfiguration(storage, physicalTableMode, clickHouseOrderByColumns);
        validatePhysicalLocationAvailable(
                request.storageDataSourceId(), namespace.catalogName(), namespace.schemaName(), physicalTableName, null
        );

        DataModel model = DataModel.create(
                code, request.name(), request.directoryId(), request.warehouseLayerId(), request.storageDataSourceId(),
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
        validateWarehouseLayerAssignment(request.warehouseLayerId(), null);
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
                request.name(), request.directoryId(), request.warehouseLayerId(), request.storageDataSourceId(),
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
        DataModel model = definitions.requireModel(id);
        directoryService.validateAssignment(DirectoryScope.MODEL, request.directoryId());
        validateWarehouseLayerAssignment(request.warehouseLayerId(), model.getWarehouseLayerId());

        PhysicalTableMode physicalTableMode = physicalTableMode(request.physicalTableMode());
        boolean storageChanged = !Objects.equals(model.getStorageDataSourceId(), request.storageDataSourceId());
        DataSource storage = model.getStatus() == DataModelStatus.DRAFT
                ? definitions.requireModelDataSource(request.storageDataSourceId(), false, physicalTableMode)
                : null;
        PhysicalNamespace namespace = storageChanged && storage != null
                ? resolvePhysicalNamespace(storage)
                : new PhysicalNamespace(model.getCatalogName(), model.getSchemaName());
        String physicalTableName = normalizeCode(request.physicalTableName());
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
                requiresMissingTableCheck ? definitions.fieldsFor(id) : List.of(),
                requiresMissingTableCheck
        );
    }

    private DataModelDetailResponse completeUpdate(
            UUID id,
            UpdateDataModelRequest request,
            UpdatePreparation preparation,
            List<ExternalTableField> importedFields
    ) {
        DataModel model = definitions.requireModel(id);
        if (!Objects.equals(model.getUpdatedAt(), preparation.expectedUpdatedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型定义已发生变化，请重新提交");
        }
        directoryService.validateAssignment(DirectoryScope.MODEL, request.directoryId());
        validateWarehouseLayerAssignment(request.warehouseLayerId(), model.getWarehouseLayerId());
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
                request.name(), request.directoryId(), request.warehouseLayerId(), request.storageDataSourceId(),
                preparation.namespace().catalogName(), preparation.namespace().schemaName(),
                preparation.physicalTableName(), request.physicalTableMode(), preparation.clickHouseOrderByColumns(),
                request.description()
        );
        DataModel saved = repository.saveAndFlush(model);
        if (preparation.physicalLocationChanged()) {
            physicalStatisticsRepository.deleteByModelId(id);
        }
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
        requireDirectFieldUpdateAllowed(
                preparation.model(), preparation.storage(), preparation.currentFields(), preparation.normalizedFields()
        );
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeUpdateFields(id, preparation)
        ));
    }

    private UpdateFieldsPreparation prepareUpdateFields(UUID id, UpdateDataModelFieldsRequest request) {
        DataModel model = definitions.requireModel(id);
        requireFieldsEditable(model);
        requireNoActivePhysicalChange(id);

        List<NormalizedField> normalizedFields = normalizeFields(request.fields());
        DataSource storage = definitions.requireModelDataSource(
                model.getStorageDataSourceId(), false, model.getPhysicalTableMode()
        );
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
        definitions.validateStandardDictionaryAssignments(
                normalizedFields,
                currentFields.stream().collect(Collectors.toMap(DataModelField::getId, Function.identity()))
        );
        return new UpdateFieldsPreparation(
                model, model.getUpdatedAt(), storage, normalizedFields, currentFields
        );
    }

    private DataModelDetailResponse completeUpdateFields(UUID id, UpdateFieldsPreparation preparation) {
        DataModel model = definitions.requireModel(id);
        if (!Objects.equals(model.getUpdatedAt(), preparation.expectedUpdatedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型字段已发生变化，请重新提交");
        }
        requireFieldsEditable(model);
        requireNoActivePhysicalChange(id);
        List<NormalizedField> normalizedFields = preparation.normalizedFields();
        List<DataModelField> currentFields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(id);
        Map<UUID, DataModelField> existingFields = currentFields.stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        Map<String, DataModelPhysicalColumnRole> physicalRolesByCode = currentFields.stream()
                .collect(Collectors.toMap(DataModelField::getCode, DataModelField::getPhysicalColumnRole));
        definitions.validateStandardDictionaryAssignments(normalizedFields, existingFields);
        Set<UUID> retainedIds = new HashSet<>();
        List<DataModelField> fieldsToSave = new ArrayList<>();

        for (NormalizedField normalized : normalizedFields) {
            DataModelFieldInput input = normalized.input();
            DataModelField field;
            if (input.id() == null) {
                field = DataModelField.create(
                        id, normalized.code(), input.name(), input.fieldType(), normalized.length(),
                        normalized.precision(), normalized.scale(), normalized.geometry(),
                        input.nullable(), input.primaryKey(),
                        input.sortOrder(), input.description()
                );
                if (model.getPhysicalTableMode() == PhysicalTableMode.EXTERNAL) {
                    field.assignPhysicalColumnRole(physicalRolesByCode.get(normalized.code()));
                }
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
                        normalized.precision(), normalized.scale(), normalized.geometry(),
                        input.nullable(), input.primaryKey(),
                        input.sortOrder(), input.description()
                );
            }
            field.assignStandardDictionary(input.standardDictionaryId());
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
        model.advanceSchemaVersion();
        DataModelDetailResponse detail = detail(repository.saveAndFlush(model));
        qualityRuleService.reconcileModelFields(id);
        return detail;
    }

    public DataModelPhysicalChangeResponse createPhysicalTableChangePlan(
            UUID id,
            CreatePhysicalTableChangePlanRequest request
    ) {
        return physicalChanges.createPhysicalTableChangePlan(id, request);
    }

    @Transactional(readOnly = true)
    public PageResponse<DataModelPhysicalChangeResponse> searchPhysicalTableChangePlans(UUID modelId, SearchRequest request) {
        return physicalChanges.searchPhysicalTableChangePlans(modelId, request);
    }

    @Transactional(readOnly = true)
    public DataModelPhysicalChangeResponse getPhysicalTableChangePlan(UUID modelId, UUID planId) {
        return physicalChanges.getPhysicalTableChangePlan(modelId, planId);
    }

    @Transactional
    public DataModelPhysicalChangeResponse cancelPhysicalTableChangePlan(UUID modelId, UUID planId) {
        return physicalChanges.cancelPhysicalTableChangePlan(modelId, planId);
    }

    public DataModelPhysicalChangeResponse executePhysicalTableChangePlan(
            UUID modelId,
            UUID planId,
            ExecutePhysicalTableChangePlanRequest request
    ) {
        return physicalChanges.executePhysicalTableChangePlan(modelId, planId, request);
    }

    public DataModelDetailResponse publish(UUID id) {
        PublishPreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> preparePublish(id))
        );
        ensurePhysicalTableReadyForPublish(preparation);
        return requireTransactionResult(transactionTemplate.execute(
                status -> completePublish(preparation)
        ));
    }

    @Transactional
    public DataModelDetailResponse disable(UUID id) {
        DataModel model = definitions.requireModel(id);
        if (model.getStatus() != DataModelStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布模型可以停用");
        }
        model.disable();
        return detail(repository.saveAndFlush(model));
    }

    private PublishPreparation preparePublish(UUID id) {
        DataModel model = definitions.requireModel(id);
        DataModelStatus sourceStatus = model.getStatus();
        if (sourceStatus != DataModelStatus.DRAFT && sourceStatus != DataModelStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有草稿或已停用模型可以发布");
        }
        List<DataModelField> fields = definitions.fieldsFor(id);
        if (fields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先定义至少一个模型字段");
        }
        DataSource storage = definitions.requireModelDataSource(
                model.getStorageDataSourceId(), true, model.getPhysicalTableMode()
        );
        return new PublishPreparation(model, sourceStatus, model.getUpdatedAt(), storage, fields);
    }

    private void ensurePhysicalTableReadyForPublish(PublishPreparation preparation) {
        ModelPhysicalTableInspection inspection = physicalTablePort.inspect(
                preparation.storage(), preparation.model(), preparation.fields()
        );
        if (inspection.state() == PhysicalTableState.MATCHED) {
            return;
        }
        if (inspection.state() == PhysicalTableState.NOT_FOUND
                && preparation.model().getPhysicalTableMode() == PhysicalTableMode.MANAGED) {
            transactionTemplate.executeWithoutResult(
                    status -> physicalStatisticsRepository.deleteByModelId(preparation.model().getId())
            );
            inspection = physicalTablePort.create(
                    preparation.storage(), preparation.model(), preparation.fields()
            );
            if (inspection.state() == PhysicalTableState.MATCHED) {
                return;
            }
        }
        String reason = inspection.message() == null || inspection.message().isBlank()
                ? inspection.state().name()
                : inspection.message();
        throw new ResponseStatusException(HttpStatus.CONFLICT, "物理表未就绪：" + reason);
    }

    private DataModelDetailResponse completePublish(PublishPreparation preparation) {
        DataModel model = definitions.requireModel(preparation.model().getId());
        if (model.getStatus() != preparation.sourceStatus()
                || !Objects.equals(model.getUpdatedAt(), preparation.expectedUpdatedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型状态已发生变化，请重试");
        }
        model.publish();
        return detail(repository.saveAndFlush(model));
    }

    @Transactional
    public void delete(UUID id) {
        DataModel model = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在"));
        if (model.getStatus() == DataModelStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布模型请先停用后再删除");
        }
        if (!referenceQueryService.authoritative(id).deletable()) {
            throw new CodedProblemException(
                    HttpStatus.CONFLICT, "MODEL_REFERENCED", "模型仍被任务、数据服务或已发布指标引用，不能删除");
        }
        physicalChangeRepository.deleteAllByModelId(id);
        physicalStatisticsRepository.deleteByModelId(id);
        qualityRuleService.invalidateReferencesToDeletedModel(id);
        qualityRuleService.deleteByModelId(id);
        fieldRepository.deleteAllByModelId(id);
        repository.delete(model);
    }

    private DataModelDetailResponse detail(DataModel model) {
        String storageName = dataSourceRepository.findById(model.getStorageDataSourceId())
                .map(DataSource::getName)
                .orElse("已删除的数据存储");
        ModelWarehouseLayerSummaryResponse warehouseLayer = model.getWarehouseLayerId() == null
                ? null
                : warehouseLayerRepository.findById(model.getWarehouseLayerId())
                        .map(ModelWarehouseLayerSummaryResponse::from)
                        .orElse(null);
        List<DataModelField> modelFields =
                fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
        Map<UUID, StandardDictionarySummaryResponse> dictionaries = standardDictionaryValueSupport.summaries(
                modelFields.stream()
                        .map(DataModelField::getStandardDictionaryId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
        );
        List<DataModelFieldResponse> fields = modelFields.stream()
                .map(field -> DataModelFieldResponse.from(
                        field,
                        field.getStandardDictionaryId() == null
                                ? null
                                : dictionaries.get(field.getStandardDictionaryId())
                ))
                .toList();
        DataModelPhysicalStatisticsResponse physicalStatistics = physicalStatisticsRepository.findByModelId(model.getId())
                .map(DataModelPhysicalStatisticsResponse::from)
                .orElse(null);
        return new DataModelDetailResponse(
                DataModelResponse.from(model, storageName, warehouseLayer, physicalStatistics),
                fields
        );
    }

    private void validateWarehouseLayerAssignment(UUID requestedLayerId, UUID currentLayerId) {
        if (requestedLayerId == null || Objects.equals(requestedLayerId, currentLayerId)) {
            return;
        }
        ModelWarehouseLayer layer = warehouseLayerRepository.findById(requestedLayerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数仓分层不存在"));
        if (!layer.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数仓分层已停用");
        }
    }

    private Map<UUID, ModelWarehouseLayer> warehouseLayers(List<DataModel> models) {
        List<UUID> layerIds = models.stream()
                .map(DataModel::getWarehouseLayerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (layerIds.isEmpty()) {
            return Map.of();
        }
        return warehouseLayerRepository.findAllById(layerIds).stream()
                .collect(Collectors.toMap(ModelWarehouseLayer::getId, Function.identity()));
    }

    private Map<UUID, DataModelPhysicalStatisticsResponse> physicalStatistics(List<DataModel> models) {
        List<UUID> modelIds = models.stream().map(DataModel::getId).toList();
        if (modelIds.isEmpty()) {
            return Map.of();
        }
        return physicalStatisticsRepository.findAllByModelIdIn(modelIds).stream()
                .collect(Collectors.toMap(
                        statistics -> statistics.getModelId(),
                        DataModelPhysicalStatisticsResponse::from
                ));
    }

    private void requireDirectFieldUpdateAllowed(
            DataModel model,
            DataSource storage,
            List<DataModelField> currentFields,
            List<NormalizedField> requestedFields
    ) {
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED
                || currentFields.isEmpty()
                || isMetadataOnlyFieldUpdate(
                        currentFields,
                        requestedFields,
                        storage.getType() == DataSourceType.CLICKHOUSE
                )) {
            return;
        }
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

    private static void requireFieldsEditable(DataModel model) {
        if (model.getStatus() != DataModelStatus.DRAFT && model.getStatus() != DataModelStatus.DISABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布模型请先停用后再修改字段");
        }
    }

    private static boolean isMetadataOnlyFieldUpdate(
            List<DataModelField> currentFields,
            List<NormalizedField> requestedFields,
            boolean ignorePrimaryKey
    ) {
        if (currentFields.size() != requestedFields.size()) {
            return false;
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
                    || !Objects.equals(current.getGeometry(), requested.geometry())
                    || current.isNullable() != requested.input().nullable()
                    || (!ignorePrimaryKey && current.isPrimaryKey() != requested.input().primaryKey())) {
                return false;
            }
        }
        return true;
    }

    public PhysicalTableInspectionResponse inspectPhysicalTable(UUID id) {
        return physicalChanges.inspectPhysicalTable(id);
    }

    public PhysicalTableDdlPlanResponse physicalTableDdl(UUID id) {
        return physicalChanges.physicalTableDdl(id);
    }

    public PhysicalTableInspectionResponse createPhysicalTable(UUID id) {
        return physicalChanges.createPhysicalTable(id);
    }

    public DataModelPreviewResponse previewPhysicalTable(UUID id) {
        return dataQueries.previewPhysicalTable(id);
    }

    public DataModelDataQueryResponse queryPhysicalTableData(UUID id, DataModelDataQueryRequest request) {
        return dataQueries.queryPhysicalTableData(id, request);
    }

    private Map<UUID, String> storageNames(List<DataModel> models) {
        Set<UUID> ids = models.stream().map(DataModel::getStorageDataSourceId).collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        dataSourceRepository.findAllById(ids).forEach(source -> names.put(source.getId(), source.getName()));
        return names;
    }

    private List<ExternalTableField> importExternalTableFields(DataSource storage, DataModel model) {
        if (!storage.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "关联的 JDBC 数据源已停用");
        }
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
                    platformType.geometry(),
                    mapping.column().nullable(),
                    mapping.primaryKey(),
                    fields.size() * 10 + 10,
                    truncateExternalFieldComment(mapping.column().comment()),
                    DataModelPhysicalColumnRole.valueOf(mapping.column().role().name())
            ));
        }
        return List.copyOf(fields);
    }

    private static List<ExternalColumnMapping> externalColumnMappings(
            TableMetadata metadata,
            DatabaseDialect dialect
    ) {
        Set<String> primaryKeyCodes = metadata.primaryKey().columns().stream()
                .map(ModelDefinitionService::normalizeCode)
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
            } else if (typeMapping.definition().type() == PlatformDataType.GEOMETRY
                    && primaryKeyCodes.contains(code)) {
                issue = "Geometry 字段不能作为模型主键：" + code;
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

    private static List<ManagedImportColumnResponse> managedImportColumns(
            TableMetadata metadata,
            DatabaseDialect sourceDialect,
            DatabaseDialect targetDialect
    ) {
        Set<String> primaryKeyCodes = metadata.primaryKey().columns().stream()
                .map(ModelDefinitionService::normalizeCode)
                .collect(Collectors.toSet());
        Map<String, Integer> normalizedCodeCounts = new LinkedHashMap<>();
        for (ColumnMetadata column : metadata.columns()) {
            String normalized = normalizeCode(column.name());
            if (LOWER_FIELD_IDENTIFIER.matcher(normalized).matches()) {
                normalizedCodeCounts.merge(normalized, 1, Integer::sum);
            }
        }

        List<ManagedImportColumnResponse> result = new ArrayList<>();
        for (ColumnMetadata column : metadata.columns()) {
            String normalizedCode = normalizeCode(column.name());
            List<String> issues = new ArrayList<>();
            String code;
            if (!LOWER_FIELD_IDENTIFIER.matcher(normalizedCode).matches()) {
                code = "";
                issues.add("字段名小写化后仍不符合模型编码规则：" + column.name());
            } else if (normalizedCodeCounts.getOrDefault(normalizedCode, 0) > 1) {
                code = "";
                issues.add("字段名小写化后重复：" + normalizedCode);
            } else {
                code = normalizedCode;
            }

            TypeMappingResult<PlatformTypeDefinition> sourceMapping = sourceDialect.mapToPlatformType(
                    JdbcTypeDescriptor.from(column)
            );
            TypeMappingResult<PhysicalTypeDefinition> targetMapping = sourceMapping.acceptable()
                    ? targetDialect.mapToPhysicalType(sourceMapping.definition())
                    : null;
            if (!sourceMapping.acceptable()) {
                issues.add("源字段类型无法安全映射到平台类型：" + mappingMessage(sourceMapping));
            }
            if (targetMapping != null && !targetMapping.acceptable()) {
                issues.add("平台字段类型无法安全映射到目标数据存储：" + mappingMessage(targetMapping));
            }
            if (sourceMapping.acceptable()
                    && sourceMapping.definition().type() == PlatformDataType.GEOMETRY
                    && primaryKeyCodes.contains(normalizedCode)) {
                issues.add("Geometry 字段不能作为模型主键");
            }

            boolean safeType = sourceMapping.acceptable() && targetMapping != null && targetMapping.acceptable();
            PlatformTypeDefinition platformType = safeType ? sourceMapping.definition() : null;
            TypeMappingQuality quality = targetMapping == null
                    ? sourceMapping.quality()
                    : worseQuality(sourceMapping.quality(), targetMapping.quality());
            List<String> mappingMessages = new ArrayList<>();
            if (sourceMapping.message() != null) {
                mappingMessages.add("源类型：" + sourceMapping.message());
            }
            if (targetMapping != null && targetMapping.message() != null) {
                mappingMessages.add("目标类型：" + targetMapping.message());
            }
            String comment = truncateText(normalizeOptional(column.comment()), 500);
            String fieldName = truncateText(comment == null ? column.name() : comment, 100);
            int sortOrder = column.ordinal() > 0 ? Math.multiplyExact(column.ordinal(), 10) : (result.size() + 1) * 10;
            result.add(new ManagedImportColumnResponse(
                    column.name(),
                    column.nativeType(),
                    code,
                    fieldName,
                    platformType == null ? null : platformType.type(),
                    platformType == null ? null : platformType.length(),
                    platformType == null ? null : platformType.precision(),
                    platformType == null ? null : platformType.scale(),
                    platformType == null ? null : platformType.geometry(),
                    column.nullable(),
                    primaryKeyCodes.contains(normalizedCode),
                    sortOrder,
                    comment,
                    quality,
                    mappingMessages.isEmpty() ? null : String.join("；", mappingMessages),
                    code.length() > 0 && issues.isEmpty(),
                    issues
            ));
        }
        return List.copyOf(result);
    }

    private static List<FileDatasetImportColumnResponse> fileDatasetImportColumns(
            List<FileDatasetField> sourceFields,
            DatabaseDialect targetDialect
    ) {
        Map<String, Integer> normalizedCodeCounts = new LinkedHashMap<>();
        for (FileDatasetField field : sourceFields) {
            String normalized = normalizeCode(field.getName());
            if (LOWER_FIELD_IDENTIFIER.matcher(normalized).matches()) {
                normalizedCodeCounts.merge(normalized, 1, Integer::sum);
            }
        }

        List<FileDatasetImportColumnResponse> result = new ArrayList<>(sourceFields.size());
        for (FileDatasetField field : sourceFields) {
            String normalizedCode = normalizeCode(field.getName());
            List<String> issues = new ArrayList<>();
            String code;
            if (!LOWER_FIELD_IDENTIFIER.matcher(normalizedCode).matches()) {
                code = "";
                issues.add("字段名小写化后仍不符合模型编码规则：" + field.getName());
            } else if (normalizedCodeCounts.getOrDefault(normalizedCode, 0) > 1) {
                code = "";
                issues.add("字段名小写化后重复：" + normalizedCode);
            } else {
                code = normalizedCode;
            }

            PlatformTypeDefinition sourceType = field.getTypeDefinition();
            TypeMappingResult<PhysicalTypeDefinition> targetMapping = targetDialect.mapToPhysicalType(sourceType);
            if (!targetMapping.acceptable()) {
                issues.add("平台字段类型无法安全映射到目标数据存储：" + mappingMessage(targetMapping));
            }
            boolean safeType = targetMapping.acceptable();
            result.add(new FileDatasetImportColumnResponse(
                    field.getName(),
                    sourceType,
                    code,
                    truncateText(field.getName(), 100),
                    safeType ? sourceType.type() : null,
                    safeType ? sourceType.length() : null,
                    safeType ? sourceType.precision() : null,
                    safeType ? sourceType.scale() : null,
                    safeType ? sourceType.geometry() : null,
                    field.isNullable(),
                    false,
                    field.getSortOrder(),
                    null,
                    targetMapping.quality(),
                    targetMapping.message(),
                    !code.isEmpty() && issues.isEmpty(),
                    issues
            ));
        }
        return List.copyOf(result);
    }

    private static String fileDatasetSchemaSourceWarning(FileDatasetType type) {
        return switch (type) {
            case CSV, TSV, TXT, JSON, JSONL, GEOJSON, GEOJSONL, EXCEL ->
                    "字段类型来自文件样本推断，创建模型前请确认类型、长度和精度";
            case PARQUET, GEOPARQUET, GPKG, AVRO, GDB, SHP ->
                    "字段类型主要来自文件声明 Schema，创建模型前仍建议核对目标数据库兼容性";
        };
    }

    private static List<String> managedImportWarnings(TableMetadata metadata) {
        List<String> warnings = new ArrayList<>();
        for (ColumnMetadata column : metadata.columns()) {
            if (column.defaultValue() != null) {
                warnings.add("字段 " + column.name() + " 的默认值未导入");
            }
            if (column.autoIncrement()) {
                warnings.add("字段 " + column.name() + " 的自增属性未导入");
            }
            if (column.generated()) {
                warnings.add("字段 " + column.name() + " 的生成列表达式未导入");
            }
        }
        if (!metadata.indexes().isEmpty()) {
            warnings.add("源表的 " + metadata.indexes().size() + " 个索引未导入");
        }
        return List.copyOf(warnings);
    }

    private static String mappingMessage(TypeMappingResult<?> mapping) {
        return mapping.message() == null ? mapping.quality().name() : mapping.message();
    }

    private static TypeMappingQuality worseQuality(TypeMappingQuality first, TypeMappingQuality second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private static String truncateText(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maximumLength
                ? trimmed
                : trimmed.substring(0, maximumLength - 1) + "…";
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
            metricReferenceGuard.assertFieldsRemovable(model.getId(), fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId()).stream().map(DataModelField::getId).toList());
            businessObjectTypeReferenceGuard.assertFieldsRemovable(model.getId(), fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId()).stream().map(DataModelField::getId).toList());
            fieldRepository.deleteAllByModelId(model.getId());
            fieldRepository.flush();
        }
        List<DataModelField> fields = importedFields.stream()
                .map(field -> {
                    DataModelField imported = DataModelField.create(
                        model.getId(), field.code(), field.code(), field.type(),
                        field.length(), field.precision(), field.scale(), field.geometry(),
                        field.nullable(), field.primaryKey(),
                        field.sortOrder(), field.description()
                    );
                    imported.assignPhysicalColumnRole(field.physicalColumnRole());
                    return imported;
                })
                .toList();
        List<DataModelField> saved = fieldRepository.saveAllAndFlush(fields);
        if (replaceExisting) {
            model.advanceSchemaVersion();
            qualityRuleService.reconcileModelFields(model.getId());
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
                    || !Objects.equals(requested.geometry(), external.geometry())
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

    private void requireNoActivePhysicalChange(UUID modelId) {
        List<DataModelPhysicalChange> activeChanges = physicalChangeRepository.findAllByModelIdAndStatusIn(
                modelId, List.of(DataModelPhysicalChangeStatus.PLANNED, DataModelPhysicalChangeStatus.APPLYING)
        );
        if (!activeChanges.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "存在待处理的物理表变更计划，请先取消或完成该计划");
        }
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
                    field.input().fieldType(), field.length(), field.precision(), field.scale(), field.geometry()
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

    private static PhysicalTableMode physicalTableMode(PhysicalTableMode mode) {
        return mode == null ? PhysicalTableMode.MANAGED : mode;
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

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private record PhysicalNamespace(String catalogName, String schemaName) {
    }

    private record ExternalTableField(
            String code,
            PlatformDataType type,
            Integer length,
            Integer precision,
            Integer scale,
            GeometryTypeDefinition geometry,
            boolean nullable,
            boolean primaryKey,
            int sortOrder,
            String description,
            DataModelPhysicalColumnRole physicalColumnRole
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

    private record ManagedDraftPreparation(
            DataModel model,
            DataSource storage,
            PhysicalNamespace namespace,
            String physicalTableName,
            List<NormalizedField> fields
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

    private record PublishPreparation(
            DataModel model,
            DataModelStatus sourceStatus,
            Instant expectedUpdatedAt,
            DataSource storage,
            List<DataModelField> fields
    ) {
    }
}
