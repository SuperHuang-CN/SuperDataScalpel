package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryForm;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryFormStatus;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryModelLookup;
import cn.superhuang.data.scalpel.business.dataentry.repository.DataEntryModelLookupRepository;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryHealthIssueResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryHealthResponse;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.domain.PhysicalTableMode;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTableInspection;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTablePort;
import cn.superhuang.data.scalpel.business.model.service.PhysicalTableState;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryRepository;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataEntryHealthService {

    private static final List<String> PUBLISH_SUBMIT = List.of("PUBLISH", "SUBMIT");
    private static final List<String> ALL_DATA = List.of("PUBLISH", "SUBMIT", "DELETE", "QUERY");
    private static final List<String> WRITE_OPERATIONS = List.of("PUBLISH", "SUBMIT", "DELETE");
    private static final List<String> MUTATIONS = List.of("SUBMIT", "DELETE");
    private static final List<String> SUBMIT = List.of("SUBMIT");

    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DataEntryModelLookupRepository lookupRepository;
    private final StandardDictionaryRepository dictionaryRepository;
    private final ModelPhysicalTablePort physicalTablePort;
    private final DataEntryPhysicalMutationPort physicalMutationPort;

    public DataEntryHealthService(
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            DataEntryModelLookupRepository lookupRepository,
            StandardDictionaryRepository dictionaryRepository,
            ModelPhysicalTablePort physicalTablePort,
            DataEntryPhysicalMutationPort physicalMutationPort
    ) {
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.lookupRepository = lookupRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.physicalTablePort = physicalTablePort;
        this.physicalMutationPort = physicalMutationPort;
    }

    @Transactional(readOnly = true)
    public DataEntryMetadataSnapshot snapshot(DataEntryForm form) {
        DataModel model = modelRepository.findById(form.getModelId()).orElse(null);
        DataSource dataSource = model == null ? null : dataSourceRepository.findById(model.getStorageDataSourceId()).orElse(null);
        List<DataModelField> fields = model == null
                ? List.of()
                : fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
        return new DataEntryMetadataSnapshot(
                form, model, dataSource, fields, lookupRepository.findAllByFormIdOrderByTargetFieldId(form.getId())
        );
    }

    public DataEntryHealthResponse inspect(DataEntryMetadataSnapshot snapshot, boolean livePhysicalCheck) {
        List<DataEntryHealthIssueResponse> issues = new ArrayList<>();
        DataModel model = snapshot.model();
        if (model == null) {
            issue(issues, "TARGET_MODEL_MISSING", "目标模型已不存在", ALL_DATA, null, null);
            return result(snapshot.form(), issues);
        }

        if (model.getStatus() != DataModelStatus.PUBLISHED) {
            issue(issues, "TARGET_MODEL_NOT_PUBLISHED", "目标模型不是已发布状态", PUBLISH_SUBMIT, null, null);
        }
        if (snapshot.form().getStatus() == DataEntryFormStatus.PUBLISHED
                && !Objects.equals(snapshot.form().getPublishedModelSchemaVersion(), model.getSchemaVersion())) {
            issue(issues, "TARGET_SCHEMA_CHANGED", "目标模型结构版本已变化，请停用后重新发布表单", MUTATIONS, null, null);
        }
        if (model.getPhysicalTableMode() != PhysicalTableMode.MANAGED) {
            issue(issues, "TARGET_MODEL_NOT_MANAGED", "数据填报只支持受管模型", WRITE_OPERATIONS, null, null);
        }

        DataSource dataSource = snapshot.dataSource();
        if (dataSource == null || !dataSource.isEnabled() || !dataSource.getType().isJdbc()) {
            issue(issues, "TARGET_DATASOURCE_UNAVAILABLE", "目标数据源不存在、已停用或不是 JDBC 数据源", ALL_DATA, null, null);
        } else if (!dataSource.isStorageEnabled()) {
            issue(issues, "TARGET_DATASOURCE_UNAVAILABLE", "目标数据源不具有存储用途", WRITE_OPERATIONS, null, null);
        }
        if (dataSource != null && !supportedDatabase(dataSource)) {
            issue(issues, "TARGET_DATABASE_UNSUPPORTED", "数据填报只支持 PostgreSQL、MySQL 和单机 ClickHouse",
                    WRITE_OPERATIONS, null, null);
        }
        if (snapshot.fields().isEmpty()) {
            issue(issues, "TARGET_FIELDS_EMPTY", "目标模型没有字段", ALL_DATA, null, null);
        }
        if (snapshot.fields().stream().noneMatch(DataModelField::isPrimaryKey)) {
            issue(issues, "TARGET_PRIMARY_KEY_MISSING", "目标模型没有业务主键字段",
                    List.of("PUBLISH", "SUBMIT", "DELETE"), null, null);
        }
        for (DataModelField field : snapshot.fields()) {
            if (field.getFieldType() == PlatformDataType.BINARY || field.getFieldType() == PlatformDataType.GEOMETRY) {
                issue(issues, "TARGET_FIELD_UNSUPPORTED", "字段“" + field.getName() + "”的类型暂不支持填报",
                        field.isPrimaryKey() ? WRITE_OPERATIONS : PUBLISH_SUBMIT, field.getId(), null);
            }
        }
        if (!snapshot.fields().isEmpty() && snapshot.fields().stream().allMatch(field ->
                field.getFieldType() == PlatformDataType.BINARY || field.getFieldType() == PlatformDataType.GEOMETRY)) {
            issue(issues, "TARGET_FIELD_UNSUPPORTED", "目标模型没有可查询的标量字段", List.of("QUERY"), null, null);
        }

        validateDictionaries(snapshot.fields(), issues);
        validateLookups(snapshot, issues, livePhysicalCheck);

        if (livePhysicalCheck && physicalPrerequisitesMet(model, dataSource, snapshot.fields())) {
            try {
                ModelPhysicalTableInspection inspection = physicalTablePort.inspect(dataSource, model, snapshot.fields());
                if (inspection.state() != PhysicalTableState.MATCHED) {
                    issue(issues, "TARGET_TABLE_NOT_MATCHED", "目标物理表未就绪：" + inspection.message(), ALL_DATA, null, null);
                } else {
                    List<DataModelField> businessKeys = snapshot.fields().stream()
                            .filter(DataModelField::isPrimaryKey).toList();
                    if (!businessKeys.isEmpty()) {
                        try {
                            if (physicalMutationPort.hasDuplicateBusinessKey(dataSource, model, businessKeys)) {
                                issue(issues, "TARGET_BUSINESS_KEY_DUPLICATE", "目标表存在重复业务主键，请先修复目标数据",
                                        WRITE_OPERATIONS, null, null);
                            }
                        } catch (RuntimeException exception) {
                            issue(issues, "TARGET_BUSINESS_KEY_CHECK_FAILED", "无法检查目标表业务主键唯一性",
                                    WRITE_OPERATIONS, null, null);
                        }
                    }
                }
            } catch (RuntimeException exception) {
                issue(issues, "TARGET_TABLE_NOT_MATCHED", "目标物理表检查失败", ALL_DATA, null, null);
            }
        }
        return result(snapshot.form(), issues);
    }

    private void validateDictionaries(List<DataModelField> fields, List<DataEntryHealthIssueResponse> issues) {
        Set<UUID> ids = fields.stream().map(DataModelField::getStandardDictionaryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, StandardDictionary> dictionaries = dictionaryRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StandardDictionary::getId, Function.identity()));
        for (DataModelField field : fields) {
            UUID id = field.getStandardDictionaryId();
            if (id == null) continue;
            StandardDictionary dictionary = dictionaries.get(id);
            if (dictionary == null || !dictionary.isEnabled() || !dictionaryTypeCompatible(dictionary, field)) {
                issue(issues, "DICTIONARY_INVALID", "字段“" + field.getName() + "”绑定的码表不存在、已停用或类型不兼容",
                        PUBLISH_SUBMIT, field.getId(), null);
            }
        }
    }

    private void validateLookups(
            DataEntryMetadataSnapshot snapshot,
            List<DataEntryHealthIssueResponse> issues,
            boolean livePhysicalCheck
    ) {
        Map<UUID, DataModelField> targets = snapshot.fields().stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        Set<UUID> sourceIds = snapshot.lookups().stream().map(DataEntryModelLookup::getSourceModelId).collect(Collectors.toSet());
        Map<UUID, DataModel> sources = modelRepository.findAllById(sourceIds).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        Map<UUID, List<DataModelField>> sourceFields = fieldRepository.findAllByModelIdInOrderByModelAndSort(sourceIds).stream()
                .collect(Collectors.groupingBy(DataModelField::getModelId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, DataSource> sourceDataSources = dataSourceRepository.findAllById(
                        sources.values().stream().map(DataModel::getStorageDataSourceId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(DataSource::getId, Function.identity()));

        for (DataEntryModelLookup lookup : snapshot.lookups()) {
            DataModelField target = targets.get(lookup.getTargetFieldId());
            if (target == null || target.getStandardDictionaryId() != null) {
                issue(issues, "LOOKUP_TARGET_FIELD_INVALID", "关联下拉的目标字段不存在或已经绑定码表", PUBLISH_SUBMIT,
                        lookup.getTargetFieldId(), lookup.getSourceModelId());
                continue;
            }
            DataModel source = sources.get(lookup.getSourceModelId());
            DataSource sourceDataSource = source == null ? null : sourceDataSources.get(source.getStorageDataSourceId());
            if (!validSourceModel(source, sourceDataSource)) {
                issue(issues, "LOOKUP_SOURCE_MODEL_INVALID", "关联下拉的来源模型不可用", PUBLISH_SUBMIT,
                        target.getId(), lookup.getSourceModelId());
                continue;
            }
            List<DataModelField> fields = sourceFields.getOrDefault(source.getId(), List.of());
            List<DataModelField> primaryKeys = fields.stream().filter(DataModelField::isPrimaryKey).toList();
            if (primaryKeys.size() != 1) {
                issue(issues, "LOOKUP_SOURCE_PRIMARY_KEY_INVALID", "关联下拉来源模型必须且只能有一个业务主键字段", PUBLISH_SUBMIT,
                        target.getId(), source.getId());
                continue;
            }
            DataModelField label = fields.stream().filter(field -> field.getId().equals(lookup.getSourceLabelFieldId())).findFirst().orElse(null);
            if (label == null || label.getFieldType() != PlatformDataType.STRING || label.isNullable()) {
                issue(issues, "LOOKUP_LABEL_FIELD_INVALID", "关联下拉标签字段必须是非空 STRING 字段", PUBLISH_SUBMIT,
                        target.getId(), source.getId());
            }
            if (!valueCompatible(target, primaryKeys.getFirst())) {
                issue(issues, "LOOKUP_VALUE_TYPE_INCOMPATIBLE", "目标字段无法安全保存来源模型业务主键值", PUBLISH_SUBMIT,
                        target.getId(), source.getId());
            }
            if (livePhysicalCheck && !fields.isEmpty()) {
                try {
                    if (physicalTablePort.inspect(sourceDataSource, source, fields).state() != PhysicalTableState.MATCHED) {
                        issue(issues, "LOOKUP_SOURCE_MODEL_INVALID", "关联下拉来源模型物理表未就绪", PUBLISH_SUBMIT,
                                target.getId(), source.getId());
                    } else if (physicalMutationPort.hasDuplicateBusinessKey(sourceDataSource, source, primaryKeys)) {
                        issue(issues, "LOOKUP_SOURCE_BUSINESS_KEY_DUPLICATE", "关联下拉来源模型存在重复业务主键",
                                PUBLISH_SUBMIT, target.getId(), source.getId());
                    }
                } catch (RuntimeException exception) {
                    issue(issues, "LOOKUP_SOURCE_MODEL_INVALID", "关联下拉来源模型物理表检查失败", PUBLISH_SUBMIT,
                            target.getId(), source.getId());
                }
            }
        }
    }

    private static boolean validSourceModel(DataModel model, DataSource dataSource) {
        return model != null && model.getStatus() == DataModelStatus.PUBLISHED
                && (dataSource != null && dataSource.isEnabled())
                && supportedDatabase(dataSource);
    }

    private static boolean dictionaryTypeCompatible(StandardDictionary dictionary, DataModelField field) {
        PlatformDataType dictionaryType = dictionary.getValueType();
        PlatformDataType fieldType = field.getFieldType();
        return dictionaryType == fieldType || (Set.of(PlatformDataType.INTEGER, PlatformDataType.LONG, PlatformDataType.DECIMAL).contains(dictionaryType)
                && Set.of(PlatformDataType.BYTE, PlatformDataType.SHORT, PlatformDataType.INTEGER, PlatformDataType.LONG, PlatformDataType.DECIMAL).contains(fieldType));
    }

    static boolean valueCompatible(DataModelField target, DataModelField source) {
        if (target.getFieldType() != source.getFieldType()) return false;
        if (target.getFieldType() == PlatformDataType.STRING) {
            return target.getLength() == null || (source.getLength() != null && target.getLength() >= source.getLength());
        }
        if (target.getFieldType() == PlatformDataType.DECIMAL) {
            if (target.getPrecision() == null || target.getScale() == null || source.getPrecision() == null || source.getScale() == null) return false;
            return target.getScale() >= source.getScale()
                    && target.getPrecision() - target.getScale() >= source.getPrecision() - source.getScale();
        }
        return target.getFieldType() != PlatformDataType.BINARY && target.getFieldType() != PlatformDataType.GEOMETRY;
    }

    private static boolean physicalPrerequisitesMet(DataModel model, DataSource dataSource, List<DataModelField> fields) {
        return model != null && dataSource != null && dataSource.isEnabled() && dataSource.isStorageEnabled()
                && supportedDatabase(dataSource)
                && !fields.isEmpty();
    }

    private static boolean supportedDatabase(DataSource dataSource) {
        return dataSource.getType() == DataSourceType.POSTGRESQL
                || dataSource.getType() == DataSourceType.MYSQL
                || dataSource.getType() == DataSourceType.CLICKHOUSE;
    }

    private static DataEntryHealthResponse result(DataEntryForm form, List<DataEntryHealthIssueResponse> issues) {
        boolean canPublish = form.getStatus() != DataEntryFormStatus.PUBLISHED
                && issues.stream().noneMatch(issue -> issue.affectedOperations().contains("PUBLISH"));
        boolean canSubmit = form.getStatus() == DataEntryFormStatus.PUBLISHED
                && issues.stream().noneMatch(issue -> issue.affectedOperations().contains("SUBMIT"));
        boolean canDelete = form.getStatus() == DataEntryFormStatus.PUBLISHED
                && issues.stream().noneMatch(issue -> issue.affectedOperations().contains("DELETE"));
        boolean canQuery = issues.stream().noneMatch(issue -> issue.affectedOperations().contains("QUERY"));
        return new DataEntryHealthResponse(canPublish, canSubmit, canDelete, canQuery, List.copyOf(issues));
    }

    private static void issue(
            List<DataEntryHealthIssueResponse> issues,
            String code,
            String message,
            List<String> operations,
            UUID fieldId,
            UUID sourceModelId
    ) {
        boolean duplicate = issues.stream().anyMatch(issue -> issue.code().equals(code)
                && Objects.equals(issue.fieldId(), fieldId) && Objects.equals(issue.sourceModelId(), sourceModelId));
        if (!duplicate) issues.add(new DataEntryHealthIssueResponse(code, message, operations, fieldId, sourceModelId));
    }
}
