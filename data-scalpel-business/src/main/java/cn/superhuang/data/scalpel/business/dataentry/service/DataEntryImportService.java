package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryForm;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryImportFormat;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryModelLookup;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationLog;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationType;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryImportPreviewResponse;
import cn.superhuang.data.scalpel.business.dataentry.web.response.DataEntryMutationResponse;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionaryItem;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryItemRepository;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryRepository;
import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryValueSupport;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.query.PlatformQueryValueConverter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataEntryImportService {

    private static final String IMPORT_PROTOCOL_VERSION = "DATA_ENTRY_IMPORT_V1";
    private static final int PREVIEW_ROW_LIMIT = 100;
    private static final int ISSUE_LIMIT = 200;
    private static final int LOG_SAMPLE_LIMIT = 20;
    private static final int LOOKUP_BATCH_SIZE = 500;

    private final DataEntryFormService formService;
    private final DataEntryHealthService healthService;
    private final DataEntryImportFileReader fileReader;
    private final DataEntryOperationLogService logService;
    private final DataEntryRecordChangeService changeService;
    private final DataEntryPhysicalMutationPort physicalMutationPort;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final StandardDictionaryRepository dictionaryRepository;
    private final StandardDictionaryItemRepository dictionaryItemRepository;
    private final StandardDictionaryValueSupport dictionaryValueSupport;
    private final ObjectMapper objectMapper;

    public DataEntryImportService(
            DataEntryFormService formService,
            DataEntryHealthService healthService,
            DataEntryImportFileReader fileReader,
            DataEntryOperationLogService logService,
            DataEntryRecordChangeService changeService,
            DataEntryPhysicalMutationPort physicalMutationPort,
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
        this.fileReader = fileReader;
        this.logService = logService;
        this.changeService = changeService;
        this.physicalMutationPort = physicalMutationPort;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dictionaryRepository = dictionaryRepository;
        this.dictionaryItemRepository = dictionaryItemRepository;
        this.dictionaryValueSupport = dictionaryValueSupport;
        this.objectMapper = objectMapper;
    }

    public DataEntryImportPreviewResponse preview(UUID formId, MultipartFile file) {
        DataEntryForm form = formService.requireForm(formId);
        DataEntryMetadataSnapshot snapshot = healthService.snapshot(form);
        requireSubmittable(snapshot);
        return complete(prepare(snapshot, file), file).response();
    }

    public DataEntryMutationResponse importData(
            UUID formId,
            MultipartFile file,
            String previewDigest,
            String username
    ) {
        DataEntryForm form = formService.requireForm(formId);
        PreparedImport prepared = prepare(healthService.snapshot(form), file);
        if (previewDigest == null || !MessageDigest.isEqual(
                prepared.previewDigest().getBytes(StandardCharsets.UTF_8),
                previewDigest.trim().getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "导入文件或模型配置已发生变化，请重新预览");
        }
        DataEntryOperationLog log = logService.start(
                prepared.snapshot().form(), prepared.snapshot().model().getSchemaVersion(), DataEntryOperationType.IMPORT,
                username, prepared.totalRows(), json(prepared.logPayload(List.of()))
        );
        try {
            Analysis analysis = complete(prepared, file);
            if (!analysis.importable()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导入文件仍包含未解决问题，不能写入目标表");
            }
            requireSubmittable(analysis.snapshot());
            java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger(1);
            DataEntryImportBatchListener listener = new DataEntryImportBatchListener() {
                private List<UUID> pendingIds = List.of();

                @Override
                public void beforeBatch(List<Map<String, Object>> rows) {
                    pendingIds = changeService.prepare(analysis.snapshot().form(), log.getId(),
                            DataEntryOperationType.IMPORT, username, sequence.getAndAdd(rows.size()),
                            analysis.snapshot().fields(), rows, null);
                }

                @Override
                public void afterBatch(List<Map<String, Object>> rows, List<Integer> confirmedSuccessIndexes,
                        boolean resultUnknown, String errorCode, String errorMessage) {
                    List<Integer> succeeded = confirmedSuccessIndexes.stream()
                            .filter(index -> index >= 0 && index < rows.size()).toList();
                    List<Map<String, Object>> actual = List.of();
                    if (!succeeded.isEmpty()) {
                        try {
                            List<Map<String, Object>> successfulRows = succeeded.stream().map(rows::get).toList();
                            actual = physicalMutationPort.queryRecords(
                                    analysis.snapshot().dataSource(), analysis.snapshot().model(), analysis.snapshot().fields(),
                                    analysis.validationContext().primaryKeys(), successfulRows.stream()
                                            .map(row -> businessKey(row, analysis.validationContext().primaryKeys())).toList());
                        } catch (RuntimeException readbackException) {
                            changeService.completeAfterReadbackFailure(pendingIds, succeeded, resultUnknown,
                                    "IMPORT_READBACK_FAILED",
                                    "导入批次已写入但实际值回读失败，请人工核对；系统不会自动重试");
                            pendingIds = List.of();
                            throw readbackException;
                        }
                    }
                    changeService.completeByIndexes(
                            pendingIds, succeeded, resultUnknown, errorCode, errorMessage, actual);
                    pendingIds = List.of();
                }
            };
            DataEntryPhysicalMutationResult result = physicalMutationPort.insertBatch(
                    analysis.snapshot().dataSource(), analysis.snapshot().model(), analysis.snapshot().fields(),
                    consumer -> fileReader.read(
                            file, analysis.format(), analysis.snapshot().fields(),
                            raw -> consumer.accept(requireNormalized(raw, analysis.validationContext()))
                    ), listener
            );
            String payload = json(analysis.logPayload());
            if (!result.completed()) {
                if (result.affectedCount() == 0 && !result.manualVerificationRequired()) {
                    throw new DataEntryPhysicalAccessException(
                            result.errorCode() == null ? "IMPORT_FAILED" : result.errorCode(),
                            result.errorMessage() == null ? "批量导入失败" : result.errorMessage(), null
                    );
                }
                String message = result.errorMessage() == null ? "批量导入部分完成，请人工核对" : result.errorMessage();
                try {
                    logService.partiallySucceed(
                            log.getId(), result.affectedCount(), payload,
                            result.errorCode() == null ? "PARTIAL_IMPORT" : result.errorCode(), message
                    );
                } catch (RuntimeException ignored) {
                    // Keep PROCESSING when the management database cannot record the target result.
                }
                return DataEntryMutationResponse.partiallySucceeded(
                        log.getId(), analysis.totalRows(), result.affectedCount(),
                        true, message
                );
            }
            try {
                logService.succeed(log.getId(), result.affectedCount(), payload);
                return DataEntryMutationResponse.succeeded(
                        log.getId(), analysis.totalRows(), result.affectedCount()
                );
            } catch (RuntimeException ignored) {
                return DataEntryMutationResponse.partiallySucceeded(
                        log.getId(), analysis.totalRows(), result.affectedCount(), true,
                        "目标数据库导入已完成，但操作日志状态保存失败，请人工核对；系统不会自动重试"
                );
            }
        } catch (RuntimeException exception) {
            failLog(log.getId(), exception);
            throw publicException(exception);
        }
    }

    private PreparedImport prepare(DataEntryMetadataSnapshot snapshot, MultipartFile file) {
        if (snapshot.model() == null || snapshot.fields().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "目标模型不存在或没有可导入字段");
        }
        DataEntryImportFormat format = fileReader.format(file);
        ValidationContext context = validationContext(snapshot);

        Map<UUID, LinkedHashMap<String, Object>> requestedLookupValues = new LinkedHashMap<>();
        snapshot.lookups().forEach(lookup -> requestedLookupValues.put(lookup.getTargetFieldId(), new LinkedHashMap<>()));
        LinkedHashMap<List<String>, Map<String, Object>> requestedBusinessKeys = new LinkedHashMap<>();
        int[] totalRows = {0};
        List<Map<String, Object>> sampleRows = new ArrayList<>();
        fileReader.read(file, format, snapshot.fields(), raw -> {
            totalRows[0]++;
            RowEvaluation evaluation = normalize(raw, context, false, Map.of(), null, Set.of());
            if (sampleRows.size() < LOG_SAMPLE_LIMIT) sampleRows.add(evaluation.values());
            Map<String, Object> businessKey = businessKey(evaluation.values(), context.primaryKeys());
            if (businessKey != null) {
                requestedBusinessKeys.putIfAbsent(canonicalKey(businessKey, context.primaryKeys()), businessKey);
            }
            for (DataEntryModelLookup lookup : snapshot.lookups()) {
                DataModelField field = context.fieldsById().get(lookup.getTargetFieldId());
                Object value = field == null ? null : evaluation.values().get(field.getCode());
                if (value != null) requestedLookupValues.get(field.getId()).putIfAbsent(
                        DataEntryValueCanonicalizer.canonical(value, field), value
                );
            }
        });
        String fileSha256 = fileSha256(file);
        String previewDigest = previewDigest(fileSha256, snapshot, context);
        return new PreparedImport(
                snapshot, context, fileReader.originalFileName(file), format, file.getSize(), fileSha256,
                totalRows[0], requestedLookupValues, requestedBusinessKeys, List.copyOf(sampleRows), previewDigest
        );
    }

    private Analysis complete(PreparedImport prepared, MultipartFile file) {
        Map<UUID, Map<String, String>> lookupLabels = resolveLookupLabels(
                prepared.snapshot(), prepared.validationContext(), prepared.requestedLookupValues()
        );
        Set<List<String>> existingBusinessKeys = existingBusinessKeys(prepared);
        ScanResult result = scan(
                file, prepared.format(), prepared.snapshot(), prepared.validationContext(), lookupLabels,
                existingBusinessKeys
        );
        return new Analysis(
                prepared.snapshot(), prepared.validationContext(), prepared.fileName(), prepared.format(),
                prepared.fileSize(), prepared.fileSha256(),
                result.totalRows(), result.errorRows(), result.issueCount(), result.issues(), result.previewRows(),
                prepared.previewDigest()
        );
    }

    private ScanResult scan(
            MultipartFile file,
            DataEntryImportFormat format,
            DataEntryMetadataSnapshot snapshot,
            ValidationContext context,
            Map<UUID, Map<String, String>> lookupLabels,
            Set<List<String>> existingBusinessKeys
    ) {
        MutableScan scan = new MutableScan();
        Set<List<String>> primaryKeys = new HashSet<>();
        fileReader.read(file, format, snapshot.fields(), raw -> {
            scan.totalRows++;
            RowEvaluation evaluation = normalize(
                    raw, context, true, lookupLabels, primaryKeys, existingBusinessKeys
            );
            if (!evaluation.issues().isEmpty()) scan.errorRows++;
            for (DataEntryImportPreviewResponse.Issue issue : evaluation.issues()) {
                scan.issueCount++;
                if (scan.issues.size() < ISSUE_LIMIT) scan.issues.add(issue);
            }
            if (scan.previewRows.size() < PREVIEW_ROW_LIMIT) {
                scan.previewRows.add(new DataEntryImportPreviewResponse.Row(
                        raw.rowNumber(), evaluation.values(), evaluation.displayValues()
                ));
            }
        });
        if (scan.totalRows == 0) {
            scan.issueCount++;
            scan.issues.add(new DataEntryImportPreviewResponse.Issue(
                    "IMPORT_DATA_EMPTY", 0, null, "导入文件没有非空数据行"
            ));
        }
        return new ScanResult(
                scan.totalRows, scan.errorRows, scan.issueCount, List.copyOf(scan.issues), List.copyOf(scan.previewRows)
        );
    }

    private RowEvaluation normalize(
            DataEntryImportFileReader.RawRow raw,
            ValidationContext context,
            boolean validateLookupsAndDuplicates,
            Map<UUID, Map<String, String>> lookupLabels,
            Set<List<String>> primaryKeys,
            Set<List<String>> existingBusinessKeys
    ) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, String> displayValues = new LinkedHashMap<>();
        List<DataEntryImportPreviewResponse.Issue> issues = new ArrayList<>();
        Set<String> invalidFields = new HashSet<>();
        for (DataModelField field : context.fields()) {
            DataEntryImportFileReader.RawCell cell = raw.values().getOrDefault(field.getCode(), DataEntryImportFileReader.RawCell.empty());
            Object value = null;
            if (cell.formula()) {
                issue(issues, "IMPORT_EXCEL_FORMULA_UNSUPPORTED", raw.rowNumber(), field, "Excel 公式单元格不支持导入");
                invalidFields.add(field.getCode());
            } else if (cell.error()) {
                issue(issues, "IMPORT_FIELD_VALUE_INVALID", raw.rowNumber(), field, "Excel 错误单元格不支持导入");
                invalidFields.add(field.getCode());
            } else if (cell.value() != null || cell.explicitEmpty()) {
                try {
                    value = rawValue(cell, field);
                    value = PlatformQueryValueConverter.convert(value, JdbcDataEntryPhysicalMutationPort.type(field));
                } catch (IllegalArgumentException exception) {
                    String code = cell.numeric() && (field.getFieldType() == PlatformDataType.LONG
                            || field.getFieldType() == PlatformDataType.DECIMAL)
                            ? "IMPORT_EXCEL_PRECISION_UNSAFE" : "IMPORT_FIELD_VALUE_INVALID";
                    String message = code.equals("IMPORT_EXCEL_PRECISION_UNSAFE")
                            ? "LONG/DECIMAL 必须使用文本单元格，避免 Excel 精度损失"
                            : "字段值格式不符合 " + field.getFieldType() + " 类型";
                    issue(issues, code, raw.rowNumber(), field, message);
                    invalidFields.add(field.getCode());
                }
            }
            if (value == null && (!field.isNullable() || field.isPrimaryKey()) && !invalidFields.contains(field.getCode())) {
                issue(issues, "IMPORT_REQUIRED_VALUE_MISSING", raw.rowNumber(), field, "必填字段不能为空");
                invalidFields.add(field.getCode());
            }
            values.put(field.getCode(), value);
            displayValues.put(field.getCode(), value == null ? "" : String.valueOf(value));
            DictionaryValues dictionary = context.dictionaries().get(field.getId());
            if (dictionary != null && value != null && !invalidFields.contains(field.getCode())) {
                String normalizedCode;
                try {
                    normalizedCode = dictionaryValueSupport.normalizeItemValue(dictionary.dictionary().getValueType(), String.valueOf(value));
                } catch (ResponseStatusException exception) {
                    normalizedCode = null;
                }
                StandardDictionaryItem item = normalizedCode == null ? null : dictionary.itemsByCode().get(normalizedCode);
                if (item == null || !dictionary.effective().getOrDefault(item.getId(), false)) {
                    issue(issues, "IMPORT_DICTIONARY_VALUE_INVALID", raw.rowNumber(), field, "码表值不存在或当前已停用");
                    invalidFields.add(field.getCode());
                } else {
                    displayValues.put(field.getCode(), dictionaryPath(item, dictionary.itemsById()) + "（" + value + "）");
                }
            }
        }
        if (validateLookupsAndDuplicates) {
            for (DataEntryModelLookup lookup : context.lookups()) {
                DataModelField field = context.fieldsById().get(lookup.getTargetFieldId());
                Object value = field == null ? null : values.get(field.getCode());
                if (field == null || value == null || invalidFields.contains(field.getCode())) continue;
                String label = lookupLabels.getOrDefault(field.getId(), Map.of()).get(
                        DataEntryValueCanonicalizer.canonical(value, field)
                );
                if (label == null) {
                    issue(issues, "IMPORT_LOOKUP_VALUE_MISSING", raw.rowNumber(), field, "关联来源模型中不存在该业务主键值");
                } else {
                    displayValues.put(field.getCode(), label + "（" + value + "）");
                }
            }
            List<DataModelField> keys = context.primaryKeys();
            if (keys.stream().allMatch(field -> values.get(field.getCode()) != null && !invalidFields.contains(field.getCode()))) {
                List<String> key = keys.stream().map(field -> DataEntryValueCanonicalizer.canonical(
                        values.get(field.getCode()), field
                )).toList();
                if (!primaryKeys.add(key)) {
                    issue(issues, "IMPORT_PRIMARY_KEY_DUPLICATE", raw.rowNumber(), null, "文件内存在规范化后重复的业务主键");
                } else if (existingBusinessKeys.contains(key)) {
                    issue(issues, "IMPORT_PRIMARY_KEY_EXISTS", raw.rowNumber(), null, "目标表已存在相同业务主键的数据");
                }
            }
        }
        return new RowEvaluation(
                Collections.unmodifiableMap(values), Collections.unmodifiableMap(displayValues), List.copyOf(issues)
        );
    }

    private static Object rawValue(DataEntryImportFileReader.RawCell cell, DataModelField field) {
        boolean explicitEmpty = cell.explicitEmpty() || "\"\"".equals(cell.value());
        if (explicitEmpty) {
            if (field.getFieldType() != PlatformDataType.STRING) {
                throw new IllegalArgumentException("Only STRING supports an explicit empty value");
            }
            return "";
        }
        if (cell.numeric() && !cell.excelDate() && (field.getFieldType() == PlatformDataType.LONG
                || field.getFieldType() == PlatformDataType.DECIMAL)) {
            throw new IllegalArgumentException("Unsafe Excel numeric precision");
        }
        if (cell.excelDate()) {
            if (field.getFieldType() == PlatformDataType.DATE) {
                int separator = cell.value().indexOf('T');
                return separator < 0 ? cell.value() : cell.value().substring(0, separator);
            }
            if (field.getFieldType() == PlatformDataType.TIMESTAMP_NTZ) return cell.value();
        }
        return cell.value();
    }

    private Map<UUID, Map<String, String>> resolveLookupLabels(
            DataEntryMetadataSnapshot snapshot,
            ValidationContext context,
            Map<UUID, LinkedHashMap<String, Object>> requestedValues
    ) {
        Map<UUID, Map<String, String>> result = new LinkedHashMap<>();
        for (DataEntryModelLookup lookup : snapshot.lookups()) {
            LookupSource source = context.lookupSources().get(lookup.getTargetFieldId());
            if (source == null) continue;
            List<Object> values = new ArrayList<>(requestedValues.getOrDefault(lookup.getTargetFieldId(), new LinkedHashMap<>()).values());
            Map<String, String> labels = new LinkedHashMap<>();
            for (int from = 0; from < values.size(); from += LOOKUP_BATCH_SIZE) {
                List<Object> batch = values.subList(from, Math.min(from + LOOKUP_BATCH_SIZE, values.size()));
                List<Map<String, Object>> rows = physicalMutationPort.queryLookupOptions(
                        source.dataSource(), source.model(), source.valueField(), source.labelField(), null, 1,
                        batch.size(), batch
                );
                Map<String, List<Map<String, Object>>> rowsByValue = rows.stream().collect(Collectors.groupingBy(
                        row -> DataEntryValueCanonicalizer.canonical(row.get("value"), source.valueField()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
                rowsByValue.forEach((value, matches) -> {
                    if (matches.size() == 1) labels.put(value, String.valueOf(matches.getFirst().get("label")));
                });
            }
            result.put(lookup.getTargetFieldId(), Collections.unmodifiableMap(labels));
        }
        return Collections.unmodifiableMap(result);
    }

    private Set<List<String>> existingBusinessKeys(PreparedImport prepared) {
        if (prepared.validationContext().primaryKeys().isEmpty()
                || prepared.requestedBusinessKeys().isEmpty()) return Set.of();
        List<DataEntryBusinessKeyMatch> matches = physicalMutationPort.findBusinessKeyMatches(
                prepared.snapshot().dataSource(), prepared.snapshot().model(),
                prepared.validationContext().primaryKeys(),
                new ArrayList<>(prepared.requestedBusinessKeys().values())
        );
        return matches.stream().map(match -> canonicalKey(
                match.key(), prepared.validationContext().primaryKeys()
        )).collect(Collectors.toUnmodifiableSet());
    }

    private static Map<String, Object> businessKey(
            Map<String, Object> row,
            List<DataModelField> fields
    ) {
        Map<String, Object> key = new LinkedHashMap<>();
        for (DataModelField field : fields) {
            Object value = row.get(field.getCode());
            if (value == null) return null;
            key.put(field.getCode(), value);
        }
        return Collections.unmodifiableMap(key);
    }

    private static List<String> canonicalKey(Map<String, Object> key, List<DataModelField> fields) {
        return fields.stream().map(field -> DataEntryValueCanonicalizer.canonical(
                key.get(field.getCode()), field
        )).toList();
    }

    private ValidationContext validationContext(DataEntryMetadataSnapshot snapshot) {
        Map<UUID, DataModelField> fieldsById = snapshot.fields().stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        Map<UUID, DictionaryValues> dictionaries = new LinkedHashMap<>();
        for (DataModelField field : snapshot.fields()) {
            if (field.getStandardDictionaryId() == null) continue;
            StandardDictionary dictionary = dictionaryRepository.findById(field.getStandardDictionaryId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "字段绑定的码表不存在：" + field.getName()));
            List<StandardDictionaryItem> items = dictionaryItemRepository
                    .findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(dictionary.getId());
            Map<UUID, StandardDictionaryItem> byId = items.stream().collect(Collectors.toMap(StandardDictionaryItem::getId, Function.identity()));
            Map<String, StandardDictionaryItem> byCode = items.stream().collect(Collectors.toMap(StandardDictionaryItem::getCode, Function.identity()));
            Map<UUID, Boolean> effective = new HashMap<>();
            items.forEach(item -> effective.put(item.getId(), effective(dictionary.isEnabled(), item, byId)));
            dictionaries.put(field.getId(), new DictionaryValues(dictionary, byId, byCode, effective, items));
        }
        Map<UUID, LookupSource> lookupSources = new LinkedHashMap<>();
        for (DataEntryModelLookup lookup : snapshot.lookups()) {
            DataModel source = modelRepository.findById(lookup.getSourceModelId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "关联下拉来源模型不存在"));
            List<DataModelField> sourceFields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(source.getId());
            DataModelField valueField = sourceFields.stream().filter(DataModelField::isPrimaryKey).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "关联下拉来源模型业务主键无效"));
            DataModelField labelField = sourceFields.stream().filter(field -> field.getId().equals(lookup.getSourceLabelFieldId())).findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "关联下拉标签字段无效"));
            DataSource dataSource = dataSourceRepository.findById(source.getStorageDataSourceId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "关联下拉来源数据源不存在"));
            lookupSources.put(lookup.getTargetFieldId(), new LookupSource(source, dataSource, valueField, labelField, sourceFields));
        }
        return new ValidationContext(
                snapshot.fields(), fieldsById, snapshot.lookups(),
                snapshot.fields().stream().filter(DataModelField::isPrimaryKey).toList(),
                Collections.unmodifiableMap(dictionaries), Collections.unmodifiableMap(lookupSources)
        );
    }

    private String previewDigest(String fileSha256, DataEntryMetadataSnapshot snapshot, ValidationContext context) {
        StringBuilder metadata = new StringBuilder(IMPORT_PROTOCOL_VERSION)
                .append('|').append(snapshot.form().getId())
                .append('|').append(snapshot.form().getStatus())
                .append('|').append(snapshot.form().getPublishedModelSchemaVersion())
                .append('|').append(snapshot.model().getId())
                .append('|').append(snapshot.model().getStatus())
                .append('|').append(snapshot.model().getSchemaVersion());
        for (DataModelField field : snapshot.fields()) {
            metadata.append("|F:").append(field.getId()).append(':').append(field.getCode()).append(':')
                    .append(field.getName()).append(':').append(field.getFieldType()).append(':')
                    .append(field.getLength()).append(':').append(field.getPrecision()).append(':').append(field.getScale())
                    .append(':').append(field.isNullable()).append(':').append(field.isPrimaryKey())
                    .append(':').append(field.getSortOrder()).append(':').append(field.getStandardDictionaryId());
            DictionaryValues dictionary = context.dictionaries().get(field.getId());
            if (dictionary != null) {
                metadata.append("|D:").append(dictionary.dictionary().getId()).append(':')
                        .append(dictionary.dictionary().isEnabled()).append(':').append(dictionary.dictionary().getValueType());
                for (StandardDictionaryItem item : dictionary.items()) {
                    metadata.append("|I:").append(item.getId()).append(':').append(item.getParentId()).append(':')
                            .append(item.getCode()).append(':').append(item.getName()).append(':').append(item.isEnabled());
                }
            }
        }
        for (DataEntryModelLookup lookup : snapshot.lookups()) {
            LookupSource source = context.lookupSources().get(lookup.getTargetFieldId());
            metadata.append("|L:").append(lookup.getTargetFieldId()).append(':').append(lookup.getSourceModelId())
                    .append(':').append(lookup.getSourceLabelFieldId());
            if (source != null) {
                metadata.append(':').append(source.model().getStatus()).append(':').append(source.model().getSchemaVersion());
                for (DataModelField field : source.fields()) {
                    metadata.append("|SF:").append(field.getId()).append(':').append(field.getCode()).append(':')
                            .append(field.getFieldType()).append(':').append(field.getLength()).append(':')
                            .append(field.getPrecision()).append(':').append(field.getScale()).append(':')
                            .append(field.isNullable()).append(':').append(field.isPrimaryKey());
                }
            }
        }
        return sha256((fileSha256 + '|' + metadata).getBytes(StandardCharsets.UTF_8));
    }

    private String fileSha256(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return hex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法计算导入文件摘要", exception);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static String hex(byte[] content) {
        StringBuilder result = new StringBuilder(content.length * 2);
        for (byte value : content) result.append(String.format("%02x", value));
        return result.toString();
    }

    private Map<String, Object> requireNormalized(
            DataEntryImportFileReader.RawRow raw,
            ValidationContext context
    ) {
        RowEvaluation evaluation = normalize(raw, context, false, Map.of(), null, Set.of());
        if (!evaluation.issues().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "导入文件在写入前发生变化，请重新预览");
        }
        return evaluation.values();
    }

    private void requireSubmittable(DataEntryMetadataSnapshot snapshot) {
        if (!healthService.inspect(snapshot, true).canSubmit()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前填报表单不可批量导入数据");
        }
    }

    private static boolean effective(
            boolean dictionaryEnabled,
            StandardDictionaryItem item,
            Map<UUID, StandardDictionaryItem> byId
    ) {
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

    private static void issue(
            List<DataEntryImportPreviewResponse.Issue> issues,
            String code,
            int rowNumber,
            DataModelField field,
            String message
    ) {
        issues.add(new DataEntryImportPreviewResponse.Issue(
                code, rowNumber, field == null ? null : field.getCode(),
                field == null ? message : "字段“" + field.getName() + "”：" + message
        ));
    }

    private void failLog(UUID logId, RuntimeException exception) {
        try {
            logService.fail(logId, errorCode(exception), safeMessage(exception));
        } catch (RuntimeException ignored) {
            // Keep PROCESSING for manual verification.
        }
    }

    private static RuntimeException publicException(RuntimeException exception) {
        if (exception instanceof ResponseStatusException responseStatusException) return responseStatusException;
        if (exception instanceof DataEntryPhysicalAccessException accessException) {
            HttpStatus status = switch (accessException.code()) {
                case "DATA_CONFLICT", "AFFECTED_COUNT_MISMATCH" -> HttpStatus.CONFLICT;
                case "QUERY_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
                default -> HttpStatus.BAD_GATEWAY;
            };
            return new ResponseStatusException(status, accessException.getMessage(), accessException);
        }
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "数据批量导入失败", exception);
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof DataEntryPhysicalAccessException accessException) return accessException.code();
        if (exception instanceof ResponseStatusException responseStatusException) return "HTTP_" + responseStatusException.getStatusCode().value();
        return "DATA_ENTRY_IMPORT_ERROR";
    }

    private static String safeMessage(RuntimeException exception) {
        if (exception instanceof DataEntryPhysicalAccessException || exception instanceof ResponseStatusException) {
            return exception.getMessage() == null ? "数据批量导入失败" : exception.getMessage();
        }
        return "数据批量导入失败";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "导入内容无法生成安全操作快照", exception);
        }
    }

    private record ValidationContext(
            List<DataModelField> fields,
            Map<UUID, DataModelField> fieldsById,
            List<DataEntryModelLookup> lookups,
            List<DataModelField> primaryKeys,
            Map<UUID, DictionaryValues> dictionaries,
            Map<UUID, LookupSource> lookupSources
    ) {
    }

    private record DictionaryValues(
            StandardDictionary dictionary,
            Map<UUID, StandardDictionaryItem> itemsById,
            Map<String, StandardDictionaryItem> itemsByCode,
            Map<UUID, Boolean> effective,
            List<StandardDictionaryItem> items
    ) {
    }

    private record LookupSource(
            DataModel model,
            DataSource dataSource,
            DataModelField valueField,
            DataModelField labelField,
            List<DataModelField> fields
    ) {
    }

    private record RowEvaluation(
            Map<String, Object> values,
            Map<String, String> displayValues,
            List<DataEntryImportPreviewResponse.Issue> issues
    ) {
    }

    private record ScanResult(
            int totalRows,
            int errorRows,
            int issueCount,
            List<DataEntryImportPreviewResponse.Issue> issues,
            List<DataEntryImportPreviewResponse.Row> previewRows
    ) {
    }

    private static final class MutableScan {
        private int totalRows;
        private int errorRows;
        private int issueCount;
        private final List<DataEntryImportPreviewResponse.Issue> issues = new ArrayList<>();
        private final List<DataEntryImportPreviewResponse.Row> previewRows = new ArrayList<>();
    }

    private record Analysis(
            DataEntryMetadataSnapshot snapshot,
            ValidationContext validationContext,
            String fileName,
            DataEntryImportFormat format,
            long fileSize,
            String fileSha256,
            int totalRows,
            int errorRows,
            int issueCount,
            List<DataEntryImportPreviewResponse.Issue> issues,
            List<DataEntryImportPreviewResponse.Row> previewRows,
            String previewDigest
    ) {
        boolean importable() {
            return totalRows > 0 && errorRows == 0;
        }

        DataEntryImportPreviewResponse response() {
            return new DataEntryImportPreviewResponse(
                    fileName, format, fileSize, totalRows, totalRows - errorRows, errorRows, issueCount,
                    importable(), issueCount > issues.size(), previewDigest,
                    snapshot.fields().stream().map(field -> new DataEntryImportPreviewResponse.Field(
                            field.getId(), field.getCode(), field.getName(), field.getFieldType(), field.isNullable(),
                            field.isPrimaryKey(), field.getStandardDictionaryId() != null ? "DICTIONARY"
                            : validationContext.lookups().stream().anyMatch(lookup -> lookup.getTargetFieldId().equals(field.getId()))
                            ? "MODEL_LOOKUP" : "DEFAULT"
                    )).toList(), previewRows, issues
            );
        }

        Map<String, Object> logPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fileName", fileName);
            payload.put("format", format);
            payload.put("fileSha256", fileSha256);
            payload.put("rowCount", totalRows);
            payload.put("fieldCodes", snapshot.fields().stream().map(DataModelField::getCode).toList());
            payload.put("sampleRows", previewRows.stream().limit(LOG_SAMPLE_LIMIT)
                    .map(DataEntryImportPreviewResponse.Row::values).toList());
            return payload;
        }
    }

    private record PreparedImport(
            DataEntryMetadataSnapshot snapshot,
            ValidationContext validationContext,
            String fileName,
            DataEntryImportFormat format,
            long fileSize,
            String fileSha256,
            int totalRows,
            Map<UUID, LinkedHashMap<String, Object>> requestedLookupValues,
            Map<List<String>, Map<String, Object>> requestedBusinessKeys,
            List<Map<String, Object>> sampleRows,
            String previewDigest
    ) {
        Map<String, Object> logPayload(List<DataEntryImportPreviewResponse.Row> ignored) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fileName", fileName);
            payload.put("format", format);
            payload.put("fileSha256", fileSha256);
            payload.put("rowCount", totalRows);
            payload.put("fieldCodes", snapshot.fields().stream().map(DataModelField::getCode).toList());
            payload.put("sampleRows", sampleRows);
            return payload;
        }
    }
}
