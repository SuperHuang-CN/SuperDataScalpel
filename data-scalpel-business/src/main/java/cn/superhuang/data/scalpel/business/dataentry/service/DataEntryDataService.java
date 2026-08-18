package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryForm;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryModelLookup;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationLog;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationType;
import cn.superhuang.data.scalpel.business.dataentry.web.request.CreateDataEntryRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.DataEntryOptionQueryRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.request.DeleteDataEntryBatchRequest;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryHealthResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryMutationResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryOptionResponse;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.DataModelService;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryRequest;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDataQueryResponse;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionaryItem;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryItemRepository;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryRepository;
import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryValueSupport;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.query.PlatformQueryValueConverter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataEntryDataService {

    private final DataEntryFormService formService;
    private final DataEntryHealthService healthService;
    private final DataEntryOperationLogService logService;
    private final DataEntryPhysicalMutationPort physicalMutationPort;
    private final DataModelService modelService;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final StandardDictionaryRepository dictionaryRepository;
    private final StandardDictionaryItemRepository dictionaryItemRepository;
    private final StandardDictionaryValueSupport dictionaryValueSupport;
    private final ObjectMapper objectMapper;

    public DataEntryDataService(
            DataEntryFormService formService,
            DataEntryHealthService healthService,
            DataEntryOperationLogService logService,
            DataEntryPhysicalMutationPort physicalMutationPort,
            DataModelService modelService,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            StandardDictionaryRepository dictionaryRepository,
            StandardDictionaryItemRepository dictionaryItemRepository,
            StandardDictionaryValueSupport dictionaryValueSupport,
            ObjectMapper objectMapper
    ) {
        this.formService = formService;
        this.healthService = healthService;
        this.logService = logService;
        this.physicalMutationPort = physicalMutationPort;
        this.modelService = modelService;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.dictionaryItemRepository = dictionaryItemRepository;
        this.dictionaryValueSupport = dictionaryValueSupport;
        this.objectMapper = objectMapper;
    }

    public DataModelDataQueryResponse queryData(UUID formId, DataModelDataQueryRequest request) {
        DataEntryForm form = formService.requireForm(formId);
        requireAllowed(healthService.inspect(healthService.snapshot(form), true).canQueryEntries(), "当前目标物理表不可查询");
        try {
            return modelService.queryPhysicalTableData(form.getModelId(), request);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "目标模型或物理表已经不可用于填报查询", exception);
            }
            throw exception;
        }
    }

    public DataEntryMutationResponse insert(UUID formId, CreateDataEntryRequest request, String username) {
        DataEntryForm form = formService.requireForm(formId);
        DataModel currentModel = modelRepository.findById(form.getModelId()).orElse(null);
        DataEntryOperationLog log = logService.start(
                form,
                currentModel == null ? null : currentModel.getSchemaVersion(),
                DataEntryOperationType.INSERT,
                username,
                1,
                json(Map.of("values", request.values()))
        );
        try {
            DataEntryMetadataSnapshot snapshot = healthService.snapshot(form);
            requireAllowed(healthService.inspect(snapshot, true).canSubmit(), "当前填报表单不可新增数据");
            Map<String, Object> normalized = normalizeRow(request.values(), snapshot.fields());
            validateDictionaries(normalized, snapshot.fields());
            validateLookups(normalized, snapshot);
            List<DataModelField> businessKeys = businessKeys(snapshot.fields());
            requireNoExistingBusinessKeys(snapshot, businessKeys, List.of(normalized));
            DataEntryPhysicalMutationResult result = physicalMutationPort.insert(
                    snapshot.dataSource(), snapshot.model(), snapshot.fields(), normalized
            );
            String payload = json(Map.of("values", normalized));
            if (!result.completed()) {
                requireMutationMayHaveChangedTarget(result);
                return partial(log, 1, result, payload);
            }
            return recordSuccess(log, 1, result.affectedCount(), payload);
        } catch (RuntimeException exception) {
            failLog(log.getId(), exception);
            throw publicException(exception);
        }
    }

    public DataEntryMutationResponse deleteBatch(
            UUID formId,
            DeleteDataEntryBatchRequest request,
            String username
    ) {
        DataEntryForm form = formService.requireForm(formId);
        DataModel currentModel = modelRepository.findById(form.getModelId()).orElse(null);
        DataEntryOperationLog log = logService.start(
                form,
                currentModel == null ? null : currentModel.getSchemaVersion(),
                DataEntryOperationType.DELETE,
                username,
                request.keys().size(),
                json(Map.of("keys", request.keys()))
        );
        try {
            DataEntryMetadataSnapshot snapshot = healthService.snapshot(form);
            requireAllowed(healthService.inspect(snapshot, true).canDeleteEntries(), "当前填报表单不可删除数据");
            List<DataModelField> primaryKeys = snapshot.fields().stream().filter(DataModelField::isPrimaryKey).toList();
            List<Map<String, Object>> normalizedKeys = new ArrayList<>();
            Set<List<String>> canonicalKeys = new HashSet<>();
            for (Map<String, Object> key : request.keys()) {
                Map<String, Object> normalized = normalizeKey(key, primaryKeys);
                if (!canonicalKeys.add(canonicalKey(normalized, primaryKeys))) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "批量删除包含重复业务主键");
                }
                normalizedKeys.add(normalized);
            }
            requireExactBusinessKeyMatches(snapshot, primaryKeys, normalizedKeys);
            DataEntryPhysicalMutationResult result = physicalMutationPort.deleteBatch(
                    snapshot.dataSource(), snapshot.model(), primaryKeys, normalizedKeys
            );
            String payload = json(Map.of("keys", normalizedKeys));
            if (!result.completed()) {
                requireMutationMayHaveChangedTarget(result);
                return partial(log, normalizedKeys.size(), result, payload);
            }
            return recordSuccess(log, normalizedKeys.size(), result.affectedCount(), payload);
        } catch (RuntimeException exception) {
            failLog(log.getId(), exception);
            throw publicException(exception);
        }
    }

    public DataEntryOptionResponse queryOptions(
            UUID formId,
            UUID fieldId,
            DataEntryOptionQueryRequest request
    ) {
        DataEntryForm form = formService.requireForm(formId);
        DataEntryMetadataSnapshot snapshot = healthService.snapshot(form);
        DataModelField target = snapshot.fields().stream().filter(field -> field.getId().equals(fieldId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "填报字段不存在"));
        int pageNo = request.pageNo() == null ? 1 : request.pageNo();
        int pageSize = !request.values().isEmpty()
                ? Math.min(100, request.values().size())
                : request.pageSize() == null ? 20 : request.pageSize();
        if (target.getStandardDictionaryId() != null) {
            return dictionaryOptions(target, request, pageNo, pageSize);
        }
        DataEntryModelLookup lookup = snapshot.lookups().stream()
                .filter(item -> item.getTargetFieldId().equals(fieldId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "该字段没有可查询的下拉选项来源"));
        boolean lookupUnavailable = healthService.inspect(snapshot, true).issues().stream()
                .anyMatch(issue -> issue.code().startsWith("LOOKUP_")
                        && (Objects.equals(issue.fieldId(), fieldId)
                        || Objects.equals(issue.sourceModelId(), lookup.getSourceModelId())));
        if (lookupUnavailable) return unavailableOptions(request.values());
        return lookupOptions(target, lookup, request, pageNo, pageSize);
    }

    private DataEntryOptionResponse dictionaryOptions(
            DataModelField target,
            DataEntryOptionQueryRequest request,
            int pageNo,
            int pageSize
    ) {
        StandardDictionary dictionary = dictionaryRepository.findById(target.getStandardDictionaryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "字段绑定的码表不存在"));
        List<StandardDictionaryItem> all = dictionaryItemRepository
                .findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(dictionary.getId());
        Map<UUID, StandardDictionaryItem> byId = all.stream()
                .collect(Collectors.toMap(StandardDictionaryItem::getId, Function.identity()));
        Map<UUID, Boolean> effective = effectiveStates(dictionary.isEnabled(), all, byId);
        Map<String, StandardDictionaryItem> byCode = all.stream()
                .collect(Collectors.toMap(StandardDictionaryItem::getCode, Function.identity()));
        if (!request.values().isEmpty()) {
            List<DataEntryOptionResponse.Option> options = request.values().stream().map(raw -> {
                String code = normalizeDictionaryValue(dictionary, raw);
                StandardDictionaryItem item = byCode.get(code);
                if (item == null) return new DataEntryOptionResponse.Option(raw, String.valueOf(raw), String.valueOf(raw), "MISSING");
                String path = dictionaryPath(item, byId);
                boolean active = effective.getOrDefault(item.getId(), false);
                return new DataEntryOptionResponse.Option(convert(raw, target), path, path, active ? "ACTIVE" : "DISABLED");
            }).toList();
            return new DataEntryOptionResponse(options, 1, options.size(), false);
        }
        String keyword = request.keyword() == null ? "" : request.keyword().trim().toLowerCase(Locale.ROOT);
        List<StandardDictionaryItem> matched = all.stream()
                .filter(item -> keyword.isEmpty() || item.getCode().toLowerCase(Locale.ROOT).contains(keyword)
                        || dictionaryPath(item, byId).toLowerCase(Locale.ROOT).contains(keyword))
                .toList();
        int from = Math.min((pageNo - 1) * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());
        List<DataEntryOptionResponse.Option> options = matched.subList(from, to).stream().map(item -> {
            String path = dictionaryPath(item, byId);
            Object value = convert(item.getCode(), target);
            boolean active = effective.getOrDefault(item.getId(), false);
            return new DataEntryOptionResponse.Option(value, path, path, active ? "ACTIVE" : "DISABLED");
        }).toList();
        return new DataEntryOptionResponse(options, pageNo, pageSize, to < matched.size());
    }

    private DataEntryOptionResponse lookupOptions(
            DataModelField target,
            DataEntryModelLookup lookup,
            DataEntryOptionQueryRequest request,
            int pageNo,
            int pageSize
    ) {
        DataModel source = modelRepository.findById(lookup.getSourceModelId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "关联下拉来源模型不存在"));
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(source.getId());
        List<DataModelField> primaryKeys = fields.stream().filter(DataModelField::isPrimaryKey).toList();
        DataModelField labelField = fields.stream().filter(field -> field.getId().equals(lookup.getSourceLabelFieldId())).findFirst().orElse(null);
        if (primaryKeys.size() != 1 || labelField == null || !DataEntryHealthService.valueCompatible(target, primaryKeys.getFirst())) {
            return unavailableOptions(request.values());
        }
        DataSource dataSource = dataSourceRepository.findById(source.getStorageDataSourceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "关联下拉来源数据源不存在"));
        List<Object> convertedValues;
        try {
            convertedValues = request.values().stream()
                    .map(value -> convertReferencedValue(value, primaryKeys.getFirst())).toList();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已有值与关联模型业务主键类型不兼容", exception);
        }
        List<Map<String, Object>> rows;
        try {
            rows = physicalMutationPort.queryLookupOptions(
                    dataSource, source, primaryKeys.getFirst(), labelField, request.keyword(), pageNo, pageSize,
                    convertedValues
            );
        } catch (DataEntryPhysicalAccessException exception) {
            return unavailableOptions(request.values());
        }
        if (containsDuplicateOptionValues(rows, primaryKeys.getFirst())) return unavailableOptions(request.values());
        boolean hasNext = request.values().isEmpty() && rows.size() > pageSize;
        List<Map<String, Object>> page = hasNext ? rows.subList(0, pageSize) : rows;
        Map<String, Map<String, Object>> byCanonical = page.stream().collect(Collectors.toMap(
                row -> DataEntryValueCanonicalizer.canonical(row.get("value"), primaryKeys.getFirst()),
                Function.identity(), (first, ignored) -> first
        ));
        List<DataEntryOptionResponse.Option> options;
        if (!request.values().isEmpty()) {
            options = new ArrayList<>();
            for (int index = 0; index < request.values().size(); index++) {
                Object requested = request.values().get(index);
                Map<String, Object> row = byCanonical.get(
                        DataEntryValueCanonicalizer.canonical(convertedValues.get(index), primaryKeys.getFirst())
                );
                if (row == null) options.add(new DataEntryOptionResponse.Option(requested, String.valueOf(requested), String.valueOf(requested), "MISSING"));
                else options.add(option(row, requested));
            }
        } else {
            options = page.stream().map(DataEntryDataService::option).toList();
        }
        return new DataEntryOptionResponse(options, pageNo, pageSize, hasNext);
    }

    private Map<String, Object> normalizeRow(Map<String, Object> values, List<DataModelField> fields) {
        Set<String> expected = fields.stream().map(DataModelField::getCode).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        if (!expected.equals(values.keySet())) {
            Set<String> missing = new java.util.LinkedHashSet<>(expected);
            missing.removeAll(values.keySet());
            Set<String> unknown = new java.util.LinkedHashSet<>(values.keySet());
            unknown.removeAll(expected);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "提交字段必须与模型全部字段完全一致；缺少：" + missing + "，未知：" + unknown);
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (DataModelField field : fields) {
            Object value = values.get(field.getCode());
            if (value == null && (!field.isNullable() || field.isPrimaryKey())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段不能为空：" + field.getName());
            }
            normalized.put(field.getCode(), convertSafely(value, field));
        }
        return Collections.unmodifiableMap(normalized);
    }

    private Map<String, Object> normalizeKey(Map<String, Object> values, List<DataModelField> fields) {
        Set<String> expected = fields.stream().map(DataModelField::getCode).collect(Collectors.toSet());
        if (!expected.equals(values.keySet())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每个删除键必须恰好包含当前模型全部业务主键字段");
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (DataModelField field : fields) {
            if (values.get(field.getCode()) == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "业务主键字段不能为空：" + field.getName());
            }
            normalized.put(field.getCode(), convertReferencedValueSafely(values.get(field.getCode()), field));
        }
        return Collections.unmodifiableMap(normalized);
    }

    private void validateDictionaries(Map<String, Object> values, List<DataModelField> fields) {
        for (DataModelField field : fields) {
            if (field.getStandardDictionaryId() == null || values.get(field.getCode()) == null) continue;
            StandardDictionary dictionary = dictionaryRepository.findById(field.getStandardDictionaryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "字段绑定的码表不存在：" + field.getName()));
            if (!dictionary.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "字段绑定的码表已停用：" + field.getName());
            String code = normalizeDictionaryValue(dictionary, values.get(field.getCode()));
            StandardDictionaryItem item = dictionaryItemRepository.findByDictionaryIdAndCode(dictionary.getId(), code)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "码表值不存在：" + field.getName()));
            List<StandardDictionaryItem> all = dictionaryItemRepository.findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(dictionary.getId());
            Map<UUID, StandardDictionaryItem> byId = all.stream().collect(Collectors.toMap(StandardDictionaryItem::getId, Function.identity()));
            if (!effective(dictionary.isEnabled(), item, byId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "码表值已停用：" + field.getName());
            }
        }
    }

    private void validateLookups(Map<String, Object> values, DataEntryMetadataSnapshot snapshot) {
        Map<UUID, DataModelField> targets = snapshot.fields().stream().collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        for (DataEntryModelLookup lookup : snapshot.lookups()) {
            DataModelField target = targets.get(lookup.getTargetFieldId());
            if (target == null || values.get(target.getCode()) == null) continue;
            DataModel source = modelRepository.findById(lookup.getSourceModelId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "关联下拉来源模型不存在"));
            List<DataModelField> sourceFields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(source.getId());
            DataModelField sourceKey = sourceFields.stream().filter(DataModelField::isPrimaryKey).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "关联下拉来源模型业务主键无效"));
            DataModelField label = sourceFields.stream().filter(field -> field.getId().equals(lookup.getSourceLabelFieldId())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "关联下拉标签字段无效"));
            DataSource sourceDataSource = dataSourceRepository.findById(source.getStorageDataSourceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "关联下拉来源数据源不存在"));
            Object sourceValue = convertSafely(values.get(target.getCode()), sourceKey);
            List<Map<String, Object>> found = physicalMutationPort.queryLookupOptions(
                    sourceDataSource, source, sourceKey, label, null, 1, 2, List.of(sourceValue)
            );
            if (found.size() != 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联模型中不存在所选值：" + target.getName());
            }
        }
    }

    private void requireNoExistingBusinessKeys(
            DataEntryMetadataSnapshot snapshot,
            List<DataModelField> businessKeys,
            List<Map<String, Object>> rows
    ) {
        List<Map<String, Object>> keys = rows.stream().<Map<String, Object>>map(row -> businessKeys.stream().collect(
                Collectors.toMap(DataModelField::getCode, field -> row.get(field.getCode()),
                        (left, ignored) -> left, LinkedHashMap::new)
        )).toList();
        if (!physicalMutationPort.findBusinessKeyMatches(
                snapshot.dataSource(), snapshot.model(), businessKeys, keys
        ).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标表已存在相同业务主键的数据");
        }
    }

    private void requireExactBusinessKeyMatches(
            DataEntryMetadataSnapshot snapshot,
            List<DataModelField> businessKeys,
            List<Map<String, Object>> keys
    ) {
        List<DataEntryBusinessKeyMatch> matches = physicalMutationPort.findBusinessKeyMatches(
                snapshot.dataSource(), snapshot.model(), businessKeys, keys
        );
        if (matches.size() != keys.size() || matches.stream().anyMatch(match -> match.count() != 1)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "至少一个业务主键不存在或未唯一命中，未执行删除"
            );
        }
    }

    private DataEntryMutationResponse partial(
            DataEntryOperationLog log,
            int requestedCount,
            DataEntryPhysicalMutationResult result,
            String payload
    ) {
        String message = result.errorMessage() == null ? "操作部分完成，请人工核对" : result.errorMessage();
        try {
            logService.partiallySucceed(
                    log.getId(), result.affectedCount(), payload,
                    result.errorCode() == null ? "PARTIAL_MUTATION" : result.errorCode(), message
            );
        } catch (RuntimeException ignored) {
            // Keep PROCESSING when the management database cannot record the target result.
        }
        return DataEntryMutationResponse.partiallySucceeded(
                log.getId(), requestedCount, result.affectedCount(),
                true, message
        );
    }

    private DataEntryMutationResponse recordSuccess(
            DataEntryOperationLog log,
            int requestedCount,
            int affectedCount,
            String payload
    ) {
        try {
            logService.succeed(log.getId(), affectedCount, payload);
            return DataEntryMutationResponse.succeeded(log.getId(), requestedCount, affectedCount);
        } catch (RuntimeException ignored) {
            return DataEntryMutationResponse.partiallySucceeded(
                    log.getId(), requestedCount, affectedCount, true,
                    "目标数据库操作已完成，但操作日志状态保存失败，请人工核对；系统不会自动重试"
            );
        }
    }

    private static void requireMutationMayHaveChangedTarget(DataEntryPhysicalMutationResult result) {
        if (result.affectedCount() == 0 && !result.manualVerificationRequired()) {
            throw new DataEntryPhysicalAccessException(
                    result.errorCode() == null ? "MUTATION_FAILED" : result.errorCode(),
                    result.errorMessage() == null ? "目标数据库操作失败" : result.errorMessage(),
                    null
            );
        }
    }

    private static List<DataModelField> businessKeys(List<DataModelField> fields) {
        return fields.stream().filter(DataModelField::isPrimaryKey).toList();
    }

    private static boolean containsDuplicateOptionValues(
            List<Map<String, Object>> rows,
            DataModelField valueField
    ) {
        Set<String> seen = new HashSet<>();
        return rows.stream().anyMatch(row -> !seen.add(
                DataEntryValueCanonicalizer.canonical(row.get("value"), valueField)
        ));
    }

    private static Map<UUID, Boolean> effectiveStates(
            boolean dictionaryEnabled,
            List<StandardDictionaryItem> all,
            Map<UUID, StandardDictionaryItem> byId
    ) {
        Map<UUID, Boolean> result = new HashMap<>();
        all.forEach(item -> result.put(item.getId(), effective(dictionaryEnabled, item, byId)));
        return result;
    }

    private static boolean effective(boolean dictionaryEnabled, StandardDictionaryItem item, Map<UUID, StandardDictionaryItem> byId) {
        if (!dictionaryEnabled || !item.isEnabled()) return false;
        Set<UUID> visited = new HashSet<>();
        StandardDictionaryItem current = item;
        while (current.getParentId() != null) {
            if (!visited.add(current.getId())) return false;
            current = byId.get(current.getParentId());
            if (current == null || !current.isEnabled()) return false;
        }
        return true;
    }

    private static String dictionaryPath(StandardDictionaryItem item, Map<UUID, StandardDictionaryItem> byId) {
        List<String> names = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        StandardDictionaryItem current = item;
        while (current != null && visited.add(current.getId())) {
            names.add(current.getName());
            current = current.getParentId() == null ? null : byId.get(current.getParentId());
        }
        Collections.reverse(names);
        return String.join(" / ", names);
    }

    private String normalizeDictionaryValue(StandardDictionary dictionary, Object raw) {
        return dictionaryValueSupport.normalizeItemValue(dictionary.getValueType(), String.valueOf(raw));
    }

    private static Object convertSafely(Object raw, DataModelField field) {
        try {
            return convert(raw, field);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段值格式无效：" + field.getName(), exception);
        }
    }

    private static Object convertReferencedValueSafely(Object raw, DataModelField field) {
        try {
            return convertReferencedValue(raw, field);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段值格式无效：" + field.getName(), exception);
        }
    }

    private static Object convertReferencedValue(Object raw, DataModelField field) {
        try {
            return convert(raw, field);
        } catch (IllegalArgumentException exception) {
            if (field.getFieldType() == PlatformDataType.TIMESTAMP && raw instanceof String value) {
                try {
                    // Physical data queries expose MySQL TIMESTAMP and some ClickHouse values without an offset.
                    return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
                } catch (java.time.format.DateTimeParseException ignored) {
                    // Preserve the platform converter's original validation failure below.
                }
            }
            throw exception;
        }
    }

    private static Object convert(Object raw, DataModelField field) {
        return PlatformQueryValueConverter.convert(raw, JdbcDataEntryPhysicalMutationPort.type(field));
    }

    private static List<String> canonicalKey(Map<String, Object> key, List<DataModelField> fields) {
        return fields.stream().map(field -> DataEntryValueCanonicalizer.canonical(
                key.get(field.getCode()), field
        )).toList();
    }

    private static DataEntryOptionResponse.Option option(Map<String, Object> row) {
        Object value = row.get("value");
        String label = String.valueOf(row.get("label"));
        return new DataEntryOptionResponse.Option(value, label, label + "（" + value + "）", "ACTIVE");
    }

    private static DataEntryOptionResponse.Option option(Map<String, Object> row, Object responseValue) {
        String label = String.valueOf(row.get("label"));
        return new DataEntryOptionResponse.Option(
                responseValue, label, label + "（" + responseValue + "）", "ACTIVE"
        );
    }

    private static DataEntryOptionResponse unavailableOptions(List<Object> values) {
        return new DataEntryOptionResponse(values.stream()
                .map(value -> new DataEntryOptionResponse.Option(value, String.valueOf(value), String.valueOf(value), "SOURCE_UNAVAILABLE"))
                .toList(), 1, Math.max(values.size(), 1), false);
    }

    private void failLog(UUID logId, RuntimeException exception) {
        try {
            logService.fail(logId, errorCode(exception), safeMessage(exception));
        } catch (RuntimeException ignored) {
            // The PROCESSING record intentionally remains for manual verification.
        }
    }

    private static RuntimeException publicException(RuntimeException exception) {
        if (exception instanceof ResponseStatusException responseStatusException) return responseStatusException;
        if (exception instanceof DataEntryPhysicalAccessException accessException) {
            HttpStatus status = switch (accessException.code()) {
                case "DATA_CONFLICT", "ENTRY_NOT_FOUND", "AFFECTED_COUNT_MISMATCH" -> HttpStatus.CONFLICT;
                case "QUERY_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
                default -> HttpStatus.BAD_GATEWAY;
            };
            return new ResponseStatusException(status, accessException.getMessage(), accessException);
        }
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "数据填报操作失败", exception);
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof DataEntryPhysicalAccessException accessException) return accessException.code();
        if (exception instanceof ResponseStatusException responseStatusException) return "HTTP_" + responseStatusException.getStatusCode().value();
        return "DATA_ENTRY_ERROR";
    }

    private static String safeMessage(RuntimeException exception) {
        if (exception instanceof DataEntryPhysicalAccessException || exception instanceof ResponseStatusException) {
            return exception.getMessage() == null ? "数据填报操作失败" : exception.getMessage();
        }
        return "数据填报操作失败";
    }

    private static void requireAllowed(boolean allowed, String message) {
        if (!allowed) throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求内容无法生成安全操作快照", exception);
        }
    }
}
