package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryForm;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryFormStatus;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryModelLookup;
import cn.superhuang.data.scalpel.business.dataentry.repository.DataEntryFormRepository;
import cn.superhuang.data.scalpel.business.dataentry.repository.DataEntryModelLookupRepository;
import cn.superhuang.data.scalpel.business.dataentry.repository.DataEntryOperationLogRepository;
import cn.superhuang.data.scalpel.business.dataentry.repository.DataEntryRecordChangeRepository;
import cn.superhuang.data.scalpel.business.dataentry.web.request.CreateDataEntryFormRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.UpdateDataEntryLookupsRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryFieldResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryFormDetailResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryFormResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryHealthIssueResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryHealthResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryLookupResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryModelCandidateResponse;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryRepository;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataEntryFormService {

    private final DataEntryFormRepository formRepository;
    private final DataEntryModelLookupRepository lookupRepository;
    private final DataEntryOperationLogRepository logRepository;
    private final DataEntryRecordChangeRepository changeRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final StandardDictionaryRepository dictionaryRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DataEntryHealthService healthService;
    private final TransactionTemplate transactionTemplate;
    private final SearchEngine searchEngine;

    public DataEntryFormService(
            DataEntryFormRepository formRepository,
            DataEntryModelLookupRepository lookupRepository,
            DataEntryOperationLogRepository logRepository,
            DataEntryRecordChangeRepository changeRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            StandardDictionaryRepository dictionaryRepository,
            DataSourceRepository dataSourceRepository,
            DataEntryHealthService healthService,
            PlatformTransactionManager transactionManager,
            SearchEngine searchEngine
    ) {
        this.formRepository = formRepository;
        this.lookupRepository = lookupRepository;
        this.logRepository = logRepository;
        this.changeRepository = changeRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.healthService = healthService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.searchEngine = searchEngine;
    }

    public PageResponse<DataEntryFormResponse> search(
            DataEntryFormStatus status,
            String keyword,
            Integer requestedPage,
            Integer requestedSize
    ) {
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? 20 : requestedSize;
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数超出允许范围");
        }
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        Set<UUID> matchingModelIds = normalizedKeyword == null || normalizedKeyword.isBlank()
                ? Set.of()
                : modelRepository.findAll().stream()
                        .filter(model -> matches(model, normalizedKeyword))
                        .map(DataModel::getId)
                        .collect(Collectors.toSet());
        Specification<DataEntryForm> fixed = (root, query, builder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (normalizedKeyword != null && !normalizedKeyword.isBlank()) {
                predicates.add(matchingModelIds.isEmpty() ? builder.disjunction() : root.get("modelId").in(matchingModelIds));
            }
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        SearchPageSnapshot pageSnapshot = requireResult(transactionTemplate.execute(statusValue -> {
            Page<DataEntryForm> result = searchEngine.search(
                    new SearchRequest(null, page, size, "-updatedAt"), DataEntryForm.class, formRepository, fixed
            );
            Map<UUID, DataModel> models = modelRepository.findAllById(result.getContent().stream().map(DataEntryForm::getModelId).toList())
                    .stream().collect(Collectors.toMap(DataModel::getId, Function.identity()));
            return new SearchPageSnapshot(
                    List.copyOf(result.getContent()), Map.copyOf(models), result.getTotalElements(), result.getTotalPages(),
                    result.getNumber(), result.getSize()
            );
        }));
        Map<UUID, DataModel> models = pageSnapshot.models();
        List<DataEntryFormResponse> content = pageSnapshot.forms().stream()
                .map(form -> listSummary(form, models.get(form.getModelId()), healthService.inspect(healthService.snapshot(form), false)))
                .toList();
        return new PageResponse<>(content, pageSnapshot.totalElements(), pageSnapshot.totalPages(), pageSnapshot.page(), pageSnapshot.size());
    }

    @Transactional(readOnly = true)
    public List<DataEntryModelCandidateResponse> candidates(String keyword) {
        Set<UUID> used = formRepository.findAll().stream().map(DataEntryForm::getModelId).collect(Collectors.toSet());
        String normalized = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        return modelRepository.findAll().stream()
                .filter(model -> !used.contains(model.getId()))
                .filter(model -> matches(model, normalized))
                .sorted(Comparator.comparing(DataModel::getName).thenComparing(DataModel::getCode))
                .limit(100)
                .map(model -> candidate(model))
                .toList();
    }

    public DataEntryFormDetailResponse get(UUID id) {
        DataEntryForm form = requireForm(id);
        DataEntryMetadataSnapshot snapshot = healthService.snapshot(form);
        DataEntryHealthResponse health = healthService.inspect(snapshot, true);
        return detail(snapshot, health);
    }

    public DataEntryHealthResponse health(UUID id) {
        return healthService.inspect(healthService.snapshot(requireForm(id)), true);
    }

    public DataEntryFormDetailResponse create(CreateDataEntryFormRequest request) {
        try {
            DataEntryForm form = requireResult(transactionTemplate.execute(status -> {
                DataModel model = modelRepository.findById(request.modelId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "目标模型不存在"));
                if (formRepository.existsByModelId(model.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "该模型已经建立填报表单");
                }
                return formRepository.saveAndFlush(DataEntryForm.create(model.getId()));
            }));
            DataEntryMetadataSnapshot snapshot = healthService.snapshot(form);
            return detail(snapshot, healthService.inspect(snapshot, true));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该模型已经建立填报表单", exception);
        }
    }

    public DataEntryFormDetailResponse updateLookups(UUID id, UpdateDataEntryLookupsRequest request) {
        DataEntryForm form = requireResult(transactionTemplate.execute(status -> {
            DataEntryForm current = requireConfigurable(id);
            List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(current.getModelId());
            Map<UUID, DataModelField> byId = fields.stream().collect(Collectors.toMap(DataModelField::getId, Function.identity()));
            Set<UUID> targetIds = new HashSet<>();
            for (UpdateDataEntryLookupsRequest.LookupInput input : request.lookups()) {
                if (!targetIds.add(input.targetFieldId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联下拉目标字段不能重复");
                }
                DataModelField target = byId.get(input.targetFieldId());
                if (target == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联下拉目标字段不属于当前模型");
                }
                if (target.getStandardDictionaryId() != null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "码表字段不能再配置关联模型下拉：" + target.getName());
                }
            }
            lookupRepository.deleteAllByFormId(id);
            lookupRepository.flush();
            lookupRepository.saveAll(request.lookups().stream().map(input -> DataEntryModelLookup.create(
                    id, input.targetFieldId(), input.sourceModelId(), input.sourceLabelFieldId()
            )).toList());
            lookupRepository.flush();
            return current;
        }));
        DataEntryMetadataSnapshot snapshot = healthService.snapshot(form);
        return detail(snapshot, healthService.inspect(snapshot, true));
    }

    public DataEntryFormDetailResponse publish(UUID id) {
        DataEntryForm form = requireForm(id);
        if (form.getStatus() == DataEntryFormStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "填报表单已经发布");
        }
        DataEntryMetadataSnapshot snapshot = healthService.snapshot(form);
        DataEntryHealthResponse health = healthService.inspect(snapshot, true);
        if (!health.canPublish()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, firstIssue(health, "填报表单不满足发布条件"));
        }
        int checkedSchemaVersion = snapshot.model().getSchemaVersion();
        DataEntryForm publishedForm = requireResult(transactionTemplate.execute(status -> {
            DataEntryForm current = requireForm(id);
            DataModel currentModel = modelRepository.findById(current.getModelId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "目标模型已不存在"));
            if (current.getStatus() == DataEntryFormStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "填报表单已经发布");
            }
            if (currentModel.getSchemaVersion() != checkedSchemaVersion) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "健康检查期间模型结构版本发生变化，请重新发布");
            }
            current.publish(checkedSchemaVersion);
            return formRepository.saveAndFlush(current);
        }));
        DataEntryMetadataSnapshot published = healthService.snapshot(publishedForm);
        return detail(published, healthService.inspect(published, true));
    }

    public DataEntryFormDetailResponse disable(UUID id) {
        DataEntryForm form = requireResult(transactionTemplate.execute(status -> {
            DataEntryForm current = requireForm(id);
            if (current.getStatus() != DataEntryFormStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布填报表单可以停用");
            }
            current.disable();
            return formRepository.saveAndFlush(current);
        }));
        DataEntryMetadataSnapshot snapshot = healthService.snapshot(form);
        return detail(snapshot, healthService.inspect(snapshot, true));
    }

    @Transactional
    public void delete(UUID id) {
        DataEntryForm form = requireForm(id);
        if (form.getStatus() == DataEntryFormStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布填报表单须先停用再删除");
        }
        lookupRepository.deleteAllByFormId(id);
        lookupRepository.flush();
        changeRepository.deleteAllByFormId(id);
        changeRepository.flush();
        logRepository.deleteAllByFormId(id);
        logRepository.flush();
        formRepository.delete(form);
        formRepository.flush();
    }

    @Transactional(readOnly = true)
    public DataEntryForm requireForm(UUID id) {
        return formRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "填报表单不存在"));
    }

    private DataEntryForm requireConfigurable(UUID id) {
        DataEntryForm form = requireForm(id);
        if (form.getStatus() == DataEntryFormStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布填报表单不能修改字段配置，请先停用");
        }
        return form;
    }

    private DataEntryModelCandidateResponse candidate(DataModel model) {
        List<DataEntryHealthIssueResponse> issues = knownIssues(model);
        return new DataEntryModelCandidateResponse(model.getId(), model.getCode(), model.getName(), model.getStatus().name(),
                model.getSchemaVersion(), issues.isEmpty(), issues);
    }

    private List<DataEntryHealthIssueResponse> knownIssues(DataModel model) {
        List<DataEntryHealthIssueResponse> issues = new ArrayList<>();
        if (model.getStatus() != cn.superhuang.data.scalpel.business.model.domain.DataModelStatus.PUBLISHED) {
            issues.add(new DataEntryHealthIssueResponse("TARGET_MODEL_NOT_PUBLISHED", "目标模型不是已发布状态", List.of("PUBLISH", "SUBMIT"), null, null));
        }
        if (model.getPhysicalTableMode() != cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode.MANAGED) {
            issues.add(new DataEntryHealthIssueResponse("TARGET_MODEL_NOT_MANAGED", "数据填报只支持受管模型", List.of("PUBLISH", "SUBMIT"), null, null));
        }
        DataSource dataSource = dataSourceRepository.findById(model.getStorageDataSourceId()).orElse(null);
        if (dataSource == null || !dataSource.isEnabled() || !dataSource.isStorageEnabled()) {
            issues.add(new DataEntryHealthIssueResponse("TARGET_DATASOURCE_UNAVAILABLE", "目标数据存储不存在、已停用或不具有存储用途", List.of("PUBLISH"), null, null));
        }
        if (dataSource != null && dataSource.getType() != DataSourceType.POSTGRESQL
                && dataSource.getType() != DataSourceType.HIGHGO
                && dataSource.getType() != DataSourceType.KINGBASE
                && dataSource.getType() != DataSourceType.OPENGAUSS
                && dataSource.getType() != DataSourceType.MYSQL
                && dataSource.getType() != DataSourceType.CLICKHOUSE) {
            issues.add(new DataEntryHealthIssueResponse(
                    "TARGET_DATABASE_UNSUPPORTED", "数据填报只支持 PostgreSQL、HighGo、人大金仓、openGauss、MySQL 和单机 ClickHouse",
                    List.of("PUBLISH"), null, null
            ));
        }
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
        if (fields.isEmpty()) issues.add(new DataEntryHealthIssueResponse("TARGET_FIELDS_EMPTY", "目标模型没有字段", List.of("PUBLISH"), null, null));
        if (fields.stream().noneMatch(DataModelField::isPrimaryKey)) issues.add(new DataEntryHealthIssueResponse(
                "TARGET_PRIMARY_KEY_MISSING", "目标模型没有业务主键字段", List.of("PUBLISH"), null, null
        ));
        fields.stream()
                .filter(field -> field.getFieldType() == cn.superhuang.data.scalpel.contract.type.PlatformDataType.BINARY
                        || field.getFieldType() == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY)
                .forEach(field -> issues.add(new DataEntryHealthIssueResponse(
                        "TARGET_FIELD_UNSUPPORTED", "字段“" + field.getName() + "”的类型暂不支持填报",
                        List.of("PUBLISH"), field.getId(), null
                )));
        return List.copyOf(issues);
    }

    private DataEntryFormDetailResponse detail(DataEntryMetadataSnapshot snapshot, DataEntryHealthResponse health) {
        Map<UUID, StandardDictionary> dictionaries = dictionaryRepository.findAllById(snapshot.fields().stream()
                        .map(DataModelField::getStandardDictionaryId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(StandardDictionary::getId, Function.identity()));
        List<DataEntryLookupResponse> lookupResponses = lookupResponses(snapshot.lookups());
        Map<UUID, DataEntryLookupResponse> lookupByTarget = lookupResponses.stream()
                .collect(Collectors.toMap(DataEntryLookupResponse::targetFieldId, Function.identity(), (first, ignored) -> first));
        List<DataEntryFieldResponse> fields = snapshot.fields().stream().map(field -> DataEntryFieldResponse.from(
                field,
                StandardDictionarySummaryResponse.from(dictionaries.get(field.getStandardDictionaryId())),
                lookupByTarget.get(field.getId())
        )).toList();
        return new DataEntryFormDetailResponse(summary(snapshot.form(), snapshot.model(), health), fields, lookupResponses, health);
    }

    private List<DataEntryLookupResponse> lookupResponses(List<DataEntryModelLookup> lookups) {
        Map<UUID, DataModel> models = modelRepository.findAllById(lookups.stream().map(DataEntryModelLookup::getSourceModelId).toList())
                .stream().collect(Collectors.toMap(DataModel::getId, Function.identity()));
        Set<UUID> modelIds = models.keySet();
        Map<UUID, DataModelField> fields = fieldRepository.findAllByModelIdInOrderByModelAndSort(modelIds).stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        Map<UUID, List<DataModelField>> primaryKeys = fieldRepository.findAllByModelIdInOrderByModelAndSort(modelIds).stream()
                .filter(DataModelField::isPrimaryKey)
                .collect(Collectors.groupingBy(DataModelField::getModelId));
        return lookups.stream().map(lookup -> {
            DataModel source = models.get(lookup.getSourceModelId());
            List<DataModelField> sourcePrimaryKeys = primaryKeys.getOrDefault(lookup.getSourceModelId(), List.of());
            DataModelField value = sourcePrimaryKeys.size() == 1 ? sourcePrimaryKeys.getFirst() : null;
            DataModelField label = fields.get(lookup.getSourceLabelFieldId());
            return new DataEntryLookupResponse(
                    lookup.getId(), lookup.getTargetFieldId(), lookup.getSourceModelId(),
                    source == null ? null : source.getCode(), source == null ? null : source.getName(),
                    value == null ? null : value.getId(), value == null ? null : value.getCode(),
                    lookup.getSourceLabelFieldId(), label == null ? null : label.getCode(), label == null ? null : label.getName()
            );
        }).toList();
    }

    private static DataEntryFormResponse summary(DataEntryForm form, DataModel model, DataEntryHealthResponse health) {
        return new DataEntryFormResponse(
                form.getId(), form.getModelId(), model == null ? null : model.getCode(), model == null ? null : model.getName(),
                model == null ? null : model.getDescription(), model == null ? null : model.getStatus().name(),
                model == null ? null : model.getSchemaVersion(), form.getStatus(), form.getPublishedModelSchemaVersion(),
                health.issues().isEmpty() ? "HEALTHY" : "CHECK_REQUIRED", health.issues(), form.getCreatedAt(), form.getUpdatedAt()
        );
    }

    private static DataEntryFormResponse listSummary(DataEntryForm form, DataModel model, DataEntryHealthResponse health) {
        DataEntryFormResponse response = summary(form, model, health);
        if (!health.issues().isEmpty()) return response;
        return new DataEntryFormResponse(
                response.id(), response.modelId(), response.modelCode(), response.modelName(), response.modelDescription(),
                response.modelStatus(), response.modelSchemaVersion(), response.status(), response.publishedModelSchemaVersion(),
                "DETAIL_CHECK_REQUIRED", response.issues(), response.createdAt(), response.updatedAt()
        );
    }

    private static boolean matches(DataModel model, String keyword) {
        if (keyword == null || keyword.isBlank()) return true;
        return model != null && (model.getName().toLowerCase(Locale.ROOT).contains(keyword)
                || model.getCode().toLowerCase(Locale.ROOT).contains(keyword));
    }

    private static String firstIssue(DataEntryHealthResponse health, String fallback) {
        return health.issues().isEmpty() ? fallback : health.issues().getFirst().message();
    }

    private static <T> T requireResult(T value) {
        if (value == null) throw new IllegalStateException("数据填报管理事务未返回结果");
        return value;
    }

    private record SearchPageSnapshot(
            List<DataEntryForm> forms,
            Map<UUID, DataModel> models,
            long totalElements,
            int totalPages,
            int page,
            int size
    ) {
    }
}
