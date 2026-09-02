package cn.superhuang.data.scalpel.business.quality.service;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelPhysicalColumnRole;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.quality.domain.ModelQualityRule;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleDefinition;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.FormatPatternKind;
import cn.superhuang.data.scalpel.contract.quality.QualityConditionOperator;
import cn.superhuang.data.scalpel.contract.quality.QualityFieldComparisonOperator;
import cn.superhuang.data.scalpel.contract.quality.ViolationMetric;
import cn.superhuang.data.scalpel.contract.quality.ViolationTolerance;
import cn.superhuang.data.scalpel.business.quality.repository.ModelQualityRuleRepository;
import cn.superhuang.data.scalpel.business.quality.web.request.AcceptModelQualityRuleSuggestionsRequest;
import cn.superhuang.data.scalpel.business.quality.web.request.CreateModelQualityRuleRequest;
import cn.superhuang.data.scalpel.business.quality.web.request.UpdateModelQualityRuleRequest;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityRuleFieldResponse;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityRuleResponse;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityRuleReferenceTargetResponse;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityRuleSuggestionResponse;
import cn.superhuang.data.scalpel.business.standard.service.StandardDictionaryValueSupport;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

@Service
public class ModelQualityRuleService {

    private static final ViolationTolerance ZERO_VIOLATIONS =
            new ViolationTolerance(ViolationMetric.COUNT, BigDecimal.ZERO);

    private final ModelQualityRuleRepository repository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final StandardDictionaryValueSupport dictionaryValueSupport;
    private final ObjectMapper objectMapper;

    public ModelQualityRuleService(
            ModelQualityRuleRepository repository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            StandardDictionaryValueSupport dictionaryValueSupport,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dictionaryValueSupport = dictionaryValueSupport;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ModelQualityRuleResponse> list(UUID modelId) {
        requireModel(modelId);
        List<DataModelField> fields = fields(modelId);
        return repository.findAllByModelIdOrderByCreatedAtAsc(modelId).stream()
                .map(rule -> response(rule, fields))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ModelQualityRuleSuggestionResponse> suggestions(UUID modelId) {
        requireModel(modelId);
        List<DataModelField> fields = fields(modelId);
        List<ModelQualityRule> rules = repository.findAllByModelIdOrderByCreatedAtAsc(modelId);
        Set<String> existing = rules.stream()
                .map(this::readDefinition)
                .map(definition -> safeNormalizeDefinition(definition, fields))
                .map(ModelQualityRuleService::semanticKey)
                .collect(Collectors.toSet());
        Set<String> existingNames = rules.stream()
                .map(rule -> rule.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<Suggestion> candidates = suggestionCandidates(fields);
        return candidates.stream()
                .filter(candidate -> !existing.contains(semanticKey(candidate.definition())))
                .filter(candidate -> !existingNames.contains(candidate.name().toLowerCase(Locale.ROOT)))
                .map(candidate -> new ModelQualityRuleSuggestionResponse(
                        semanticKey(candidate.definition()), candidate.name(), candidate.reason(),
                        candidate.definition().ruleType(), candidate.severity(), candidate.definition(),
                        fieldResponses(referencedFieldIds(candidate.definition()), fields)
                ))
                .toList();
    }

    private ModelQualityRuleDefinition safeNormalizeDefinition(
            ModelQualityRuleDefinition definition,
            List<DataModelField> fields
    ) {
        try {
            return normalizeDefinition(definition, fields);
        } catch (ResponseStatusException exception) {
            return definition;
        }
    }

    @Transactional
    public ModelQualityRuleResponse create(UUID modelId, CreateModelQualityRuleRequest request) {
        requireModel(modelId);
        List<DataModelField> fields = fields(modelId);
        ModelQualityRuleDefinition definition = validateAndNormalize(request.definition(), fields);
        requireUnique(modelId, null, request.name(), definition);
        ModelQualityRule saved = repository.saveAndFlush(ModelQualityRule.create(
                modelId, request.name(), request.description(), definition.ruleType(), request.severity(),
                request.enabled(), writeDefinition(definition), referenceModelId(definition)
        ));
        return response(saved, fields);
    }

    @Transactional
    public List<ModelQualityRuleResponse> acceptSuggestions(
            UUID modelId,
            AcceptModelQualityRuleSuggestionsRequest request
    ) {
        requireModel(modelId);
        List<DataModelField> fields = fields(modelId);
        Map<String, Suggestion> candidates = suggestionCandidates(fields).stream()
                .collect(Collectors.toMap(
                        candidate -> semanticKey(candidate.definition()),
                        candidate -> candidate,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
        if (new LinkedHashSet<>(request.suggestionKeys()).size() != request.suggestionKeys().size()) {
            throw badRequest("不能重复采纳同一条质量规则建议");
        }
        List<ModelQualityRule> saved = new ArrayList<>();
        for (String suggestionKey : request.suggestionKeys()) {
            Suggestion suggestion = candidates.get(suggestionKey);
            if (suggestion == null) {
                throw badRequest("只能采纳当前模型生成的质量规则建议");
            }
            ModelQualityRuleDefinition definition = validateAndNormalize(suggestion.definition(), fields);
            requireUniqueIncludingPending(modelId, saved, suggestion.name(), definition);
            saved.add(ModelQualityRule.create(
                    modelId, suggestion.name(), suggestion.reason(), definition.ruleType(), suggestion.severity(),
                    false, writeDefinition(definition), referenceModelId(definition)
            ));
        }
        return repository.saveAllAndFlush(saved).stream().map(rule -> response(rule, fields)).toList();
    }

    @Transactional
    public ModelQualityRuleResponse update(UUID id, UpdateModelQualityRuleRequest request) {
        ModelQualityRule rule = requireRule(id);
        List<DataModelField> fields = fields(rule.getModelId());
        ModelQualityRuleDefinition definition = validateAndNormalize(request.definition(), fields);
        if (definition.ruleType() != rule.getRuleType()) {
            throw badRequest("规则类型不能修改，请删除后重新创建");
        }
        requireUnique(rule.getModelId(), id, request.name(), definition);
        rule.update(
                request.name(), request.description(), request.severity(), writeDefinition(definition),
                referenceModelId(definition)
        );
        return response(repository.saveAndFlush(rule), fields);
    }

    @Transactional
    public ModelQualityRuleResponse enable(UUID id) {
        ModelQualityRule rule = requireRule(id);
        List<DataModelField> fields = fields(rule.getModelId());
        ModelQualityRuleDefinition definition = validateAndNormalize(readDefinition(rule), fields);
        requireUnique(rule.getModelId(), id, rule.getName(), definition);
        rule.clearInvalid();
        rule.enable();
        return response(repository.saveAndFlush(rule), fields);
    }

    @Transactional
    public ModelQualityRuleResponse disable(UUID id) {
        ModelQualityRule rule = requireRule(id);
        rule.disable();
        return response(repository.saveAndFlush(rule), fields(rule.getModelId()));
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(requireRule(id));
    }

    @Transactional
    public void deleteByModelId(UUID modelId) {
        repository.deleteAllByModelId(modelId);
    }

    @Transactional
    public void reconcileModelFields(UUID modelId) {
        Map<UUID, ModelQualityRule> rules = new LinkedHashMap<>();
        repository.findAllByModelIdOrderByCreatedAtAsc(modelId)
                .forEach(rule -> rules.put(rule.getId(), rule));
        repository.findAllByReferenceModelIdOrderByCreatedAtAsc(modelId)
                .forEach(rule -> rules.put(rule.getId(), rule));
        for (ModelQualityRule rule : rules.values()) {
            Map<UUID, DataModelField> fieldsById = fields(rule.getModelId()).stream()
                    .collect(Collectors.toMap(DataModelField::getId, field -> field));
            Compatibility compatibility = compatibility(readDefinition(rule), fieldsById);
            if (compatibility.valid()) {
                rule.clearInvalid();
            } else {
                rule.invalidate(compatibility.code(), compatibility.reason());
            }
        }
        repository.saveAll(rules.values());
    }

    @Transactional
    public void invalidateReferencesToDeletedModel(UUID modelId) {
        List<ModelQualityRule> rules = repository.findAllByReferenceModelIdOrderByCreatedAtAsc(modelId);
        rules.forEach(rule -> rule.invalidate("REFERENCE_MODEL_MISSING", "引用目标模型已删除"));
        repository.saveAll(rules);
    }

    private ModelQualityRuleDefinition validateAndNormalize(
            ModelQualityRuleDefinition definition,
            List<DataModelField> fields
    ) {
        Map<UUID, DataModelField> fieldsById = fields.stream()
                .collect(Collectors.toMap(DataModelField::getId, field -> field));
        ModelQualityRuleDefinition normalized = normalizeDefinition(definition, fields);
        Compatibility compatibility = compatibility(normalized, fieldsById);
        if (!compatibility.valid()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, compatibility.reason());
        }
        validateParameters(normalized, fieldsById);
        return normalized;
    }

    private ModelQualityRuleDefinition normalizeDefinition(
            ModelQualityRuleDefinition definition,
            List<DataModelField> fields
    ) {
        if (definition == null) {
            throw badRequest("规则定义不能为空");
        }
        if (definition instanceof ModelQualityRuleDefinition.UniqueDefinition unique) {
            Set<UUID> requested = new LinkedHashSet<>(unique.fieldIds());
            if (requested.size() != unique.fieldIds().size()) {
                throw badRequest("唯一性规则不能重复选择字段");
            }
            List<UUID> ordered = fields.stream()
                    .filter(field -> requested.contains(field.getId()))
                    .sorted(Comparator.comparingInt(DataModelField::getSortOrder).thenComparing(DataModelField::getCode))
                    .map(DataModelField::getId)
                    .toList();
            if (ordered.size() != requested.size()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "唯一性规则包含不存在的模型字段");
            }
            return new ModelQualityRuleDefinition.UniqueDefinition(ordered, unique.tolerance());
        }
        if (definition instanceof ModelQualityRuleDefinition.ValueRangeDefinition range) {
            return new ModelQualityRuleDefinition.ValueRangeDefinition(
                    range.fieldId(), normalizeBound(range.minimum()), normalizeBound(range.maximum()),
                    range.minimumInclusive(), range.maximumInclusive(), range.tolerance()
            );
        }
        if (definition instanceof ModelQualityRuleDefinition.FormatPatternDefinition format) {
            return new ModelQualityRuleDefinition.FormatPatternDefinition(
                    format.fieldId(), format.patternKind(), format.preset(), normalizeRegex(format.regex()),
                    format.tolerance()
            );
        }
        if (definition instanceof ModelQualityRuleDefinition.ConditionalNotNullDefinition conditional) {
            ModelQualityRuleDefinition.QualityCondition condition = conditional.condition();
            if (condition == null) {
                return definition;
            }
            PlatformDataType conditionFieldType = fields.stream()
                    .filter(field -> Objects.equals(field.getId(), condition.fieldId()))
                    .map(DataModelField::getFieldType)
                    .findFirst()
                    .orElse(null);
            List<String> normalizedValues = condition.values() == null
                    ? List.of()
                    : condition.values().stream()
                    .map(value -> value == null || conditionFieldType == PlatformDataType.STRING
                            ? value
                            : value.trim())
                    .toList();
            if (condition.operator() == QualityConditionOperator.IN
                    || condition.operator() == QualityConditionOperator.NOT_IN) {
                normalizedValues = normalizedValues.stream().sorted().toList();
            }
            return new ModelQualityRuleDefinition.ConditionalNotNullDefinition(
                    conditional.targetFieldId(),
                    new ModelQualityRuleDefinition.QualityCondition(
                            condition.fieldId(), condition.operator(), normalizedValues
                    ),
                    conditional.tolerance()
            );
        }
        if (definition instanceof ModelQualityRuleDefinition.FieldComparisonDefinition comparison) {
            int leftOrder = fieldOrder(fields, comparison.leftFieldId());
            int rightOrder = fieldOrder(fields, comparison.rightFieldId());
            if (leftOrder > rightOrder) {
                return new ModelQualityRuleDefinition.FieldComparisonDefinition(
                        comparison.rightFieldId(), comparison.operator().reverse(), comparison.leftFieldId(),
                        comparison.tolerance()
                );
            }
        }
        if (definition instanceof ModelQualityRuleDefinition.ReferenceExistsDefinition reference) {
            if (reference.mappings() == null) {
                return definition;
            }
            Map<UUID, Integer> orderById = new LinkedHashMap<>();
            for (int index = 0; index < fields.size(); index++) {
                orderById.put(fields.get(index).getId(), index);
            }
            List<ModelQualityRuleDefinition.ReferenceFieldMapping> ordered = reference.mappings().stream()
                    .sorted(Comparator.comparingInt(mapping -> orderById.getOrDefault(
                            mapping.sourceFieldId(), Integer.MAX_VALUE
                    )))
                    .toList();
            return new ModelQualityRuleDefinition.ReferenceExistsDefinition(
                    reference.targetModelId(), ordered, reference.tolerance()
            );
        }
        return definition;
    }

    private static int fieldOrder(List<DataModelField> fields, UUID fieldId) {
        for (int index = 0; index < fields.size(); index++) {
            if (Objects.equals(fields.get(index).getId(), fieldId)) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private Compatibility compatibility(
            ModelQualityRuleDefinition definition,
            Map<UUID, DataModelField> fields
    ) {
        List<UUID> references = referencedFieldIds(definition);
        for (UUID fieldId : references) {
            if (!fields.containsKey(fieldId)) {
                return Compatibility.invalid("FIELD_MISSING", "规则引用的模型字段已删除");
            }
        }
        if (definition instanceof ModelQualityRuleDefinition.UniqueDefinition unique) {
            if (unique.fieldIds().stream().map(fields::get).anyMatch(field -> !supportsUnique(field.getFieldType()))) {
                return Compatibility.invalid("FIELD_TYPE_INCOMPATIBLE", "唯一性规则只支持非二进制、非空间字段");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.ValueRangeDefinition range) {
            if (!supportsRange(fields.get(range.fieldId()).getFieldType())) {
                return Compatibility.invalid("FIELD_TYPE_INCOMPATIBLE", "取值范围规则只支持数值、日期或时间字段");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.StringLengthDefinition length) {
            if (fields.get(length.fieldId()).getFieldType() != PlatformDataType.STRING) {
                return Compatibility.invalid("FIELD_TYPE_INCOMPATIBLE", "字符串长度规则只支持字符串字段");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.DictionaryMembershipDefinition dictionary) {
            if (fields.get(dictionary.fieldId()).getStandardDictionaryId() == null) {
                return Compatibility.invalid("DICTIONARY_UNBOUND", "模型字段已解除码表绑定");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.FreshnessDefinition freshness) {
            if (!supportsFreshness(fields.get(freshness.fieldId()).getFieldType())) {
                return Compatibility.invalid("FIELD_TYPE_INCOMPATIBLE", "新鲜度规则只支持日期或时间字段");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.GeometryValidDefinition geometry) {
            if (fields.get(geometry.fieldId()).getFieldType() != PlatformDataType.GEOMETRY) {
                return Compatibility.invalid("FIELD_TYPE_INCOMPATIBLE", "空间有效性规则只支持 Geometry 字段");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.GeometryNonEmptyDefinition geometry) {
            if (fields.get(geometry.fieldId()).getFieldType() != PlatformDataType.GEOMETRY) {
                return Compatibility.invalid("FIELD_TYPE_INCOMPATIBLE", "空间非空规则只支持 Geometry 字段");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.FormatPatternDefinition format) {
            if (fields.get(format.fieldId()).getFieldType() != PlatformDataType.STRING) {
                return Compatibility.invalid("FIELD_TYPE_INCOMPATIBLE", "格式校验规则只支持字符串字段");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.ConditionalNotNullDefinition conditional) {
            DataModelField conditionField = fields.get(conditional.condition().fieldId());
            if (!supportsCondition(conditionField.getFieldType())) {
                return Compatibility.invalid(
                        "FIELD_TYPE_INCOMPATIBLE", "条件必填规则的条件字段不支持二进制或空间类型"
                );
            }
            if ((conditional.condition().operator() == QualityConditionOperator.IS_EMPTY
                    || conditional.condition().operator() == QualityConditionOperator.IS_NOT_EMPTY)
                    && conditionField.getFieldType() != PlatformDataType.STRING) {
                return Compatibility.invalid(
                        "FIELD_TYPE_INCOMPATIBLE", "为空串和非空串条件只支持字符串字段"
                );
            }
            for (String value : conditional.condition().values()) {
                try {
                    parseConditionValue(value, conditionField.getFieldType());
                } catch (RuntimeException exception) {
                    return Compatibility.invalid(
                            "CONDITION_VALUE_INCOMPATIBLE", "条件值与条件字段类型不兼容"
                    );
                }
            }
        } else if (definition instanceof ModelQualityRuleDefinition.FieldComparisonDefinition comparison) {
            if (!supportsComparison(
                    fields.get(comparison.leftFieldId()).getFieldType(),
                    comparison.operator(),
                    fields.get(comparison.rightFieldId()).getFieldType()
            )) {
                return Compatibility.invalid("FIELD_TYPE_INCOMPATIBLE", "字段类型或比较符不支持当前字段比较");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.ReferenceExistsDefinition reference) {
            DataModel targetModel = modelRepository.findById(reference.targetModelId()).orElse(null);
            if (targetModel == null) {
                return Compatibility.invalid("REFERENCE_MODEL_MISSING", "引用目标模型已删除");
            }
            Map<UUID, DataModelField> targetFields = fields(reference.targetModelId()).stream()
                    .collect(Collectors.toMap(DataModelField::getId, field -> field));
            for (ModelQualityRuleDefinition.ReferenceFieldMapping mapping : reference.mappings()) {
                DataModelField targetField = targetFields.get(mapping.targetFieldId());
                if (targetField == null) {
                    return Compatibility.invalid("REFERENCE_TARGET_FIELD_MISSING", "引用目标模型字段已删除");
                }
                if (!supportsReference(
                        fields.get(mapping.sourceFieldId()).getFieldType(), targetField.getFieldType()
                )) {
                    return Compatibility.invalid(
                            "REFERENCE_FIELD_TYPE_INCOMPATIBLE", "引用源字段与目标字段类型不兼容"
                    );
                }
            }
        }
        return Compatibility.validResult();
    }

    private void validateParameters(ModelQualityRuleDefinition definition, Map<UUID, DataModelField> fields) {
        if (definition instanceof ModelQualityRuleDefinition.NotNullDefinition item) {
            validateTolerance(item.tolerance());
        } else if (definition instanceof ModelQualityRuleDefinition.UniqueDefinition item) {
            validateTolerance(item.tolerance());
        } else if (definition instanceof ModelQualityRuleDefinition.ValueRangeDefinition item) {
            validateTolerance(item.tolerance());
            validateRange(item, fields.get(item.fieldId()).getFieldType());
        } else if (definition instanceof ModelQualityRuleDefinition.StringLengthDefinition item) {
            validateTolerance(item.tolerance());
            if (item.minimumLength() == null && item.maximumLength() == null) {
                throw badRequest("字符串长度至少需要填写最小值或最大值");
            }
            if ((item.minimumLength() != null && item.minimumLength() < 0)
                    || (item.maximumLength() != null && item.maximumLength() < 0)) {
                throw badRequest("字符串长度不能小于 0");
            }
            if (item.minimumLength() != null && item.maximumLength() != null
                    && item.minimumLength() > item.maximumLength()) {
                throw badRequest("字符串最小长度不能大于最大长度");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.DictionaryMembershipDefinition item) {
            validateTolerance(item.tolerance());
        } else if (definition instanceof ModelQualityRuleDefinition.RowCountDefinition item) {
            if (item.minimumRowCount() < 1) {
                throw badRequest("最小行数必须大于等于 1");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.FreshnessDefinition item) {
            if (item.maximumDelayMinutes() < 1) {
                throw badRequest("最大延迟必须大于 0 分钟");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.GeometryValidDefinition item) {
            validateTolerance(item.tolerance());
        } else if (definition instanceof ModelQualityRuleDefinition.GeometryNonEmptyDefinition item) {
            validateTolerance(item.tolerance());
        } else if (definition instanceof ModelQualityRuleDefinition.FormatPatternDefinition item) {
            validateTolerance(item.tolerance());
            if (item.patternKind() == FormatPatternKind.PRESET) {
                if (item.preset() == null || item.regex() != null) {
                    throw badRequest("预置格式必须选择一个格式，且不能同时填写正则表达式");
                }
            } else if (item.patternKind() == FormatPatternKind.REGEX) {
                if (item.preset() != null || item.regex() == null) {
                    throw badRequest("自定义正则必须填写表达式，且不能同时选择预置格式");
                }
                try {
                    Pattern.compile(item.regex());
                } catch (PatternSyntaxException exception) {
                    throw badRequest("自定义正则表达式无效：" + exception.getDescription());
                }
            } else {
                throw badRequest("不支持的格式校验模式");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.ConditionalNotNullDefinition item) {
            validateTolerance(item.tolerance());
            validateCondition(item, fields);
        } else if (definition instanceof ModelQualityRuleDefinition.FieldComparisonDefinition item) {
            validateTolerance(item.tolerance());
            if (item.leftFieldId().equals(item.rightFieldId())) {
                throw badRequest("字段比较规则的左右字段不能相同");
            }
        } else if (definition instanceof ModelQualityRuleDefinition.ReferenceExistsDefinition item) {
            validateTolerance(item.tolerance());
            validateReference(item, fields);
        }
    }

    private static void validateCondition(
            ModelQualityRuleDefinition.ConditionalNotNullDefinition definition,
            Map<UUID, DataModelField> fields
    ) {
        ModelQualityRuleDefinition.QualityCondition condition = definition.condition();
        if (definition.targetFieldId().equals(condition.fieldId())) {
            throw badRequest("条件必填规则的目标字段和条件字段不能相同");
        }
        List<String> values = condition.values();
        int expectedSize = switch (condition.operator()) {
            case EQ, NE -> 1;
            case IN, NOT_IN -> -1;
            case IS_NULL, IS_NOT_NULL, IS_EMPTY, IS_NOT_EMPTY -> 0;
        };
        if ((expectedSize >= 0 && values.size() != expectedSize)
                || (expectedSize == -1 && values.isEmpty())) {
            throw badRequest("条件操作符和值数量不匹配");
        }
        if (new LinkedHashSet<>(values).size() != values.size()) {
            throw badRequest("条件值不能重复");
        }
        PlatformDataType fieldType = fields.get(condition.fieldId()).getFieldType();
        for (String value : values) {
            try {
                parseConditionValue(value, fieldType);
            } catch (RuntimeException exception) {
                if (exception instanceof ResponseStatusException responseStatusException) {
                    throw responseStatusException;
                }
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "条件值与字段类型不兼容：" + value,
                        exception
                );
            }
        }
    }

    private static Object parseConditionValue(String value, PlatformDataType type) {
        if (value == null) {
            throw badRequest("条件值不能为空");
        }
        return switch (type) {
            case STRING -> value;
            case BOOLEAN -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("invalid boolean");
                }
                yield Boolean.valueOf(value);
            }
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL -> new BigDecimal(value);
            case DATE -> LocalDate.parse(value);
            case TIMESTAMP -> Instant.parse(value);
            case TIMESTAMP_NTZ -> LocalDateTime.parse(value);
            case BINARY, GEOMETRY -> throw badRequest("当前字段类型不能作为条件字段");
        };
    }

    private static void validateReference(
            ModelQualityRuleDefinition.ReferenceExistsDefinition definition,
            Map<UUID, DataModelField> fields
    ) {
        if (definition.mappings() == null || definition.mappings().isEmpty()
                || definition.mappings().size() > 16) {
            throw badRequest("引用存在规则必须配置 1～16 组字段映射");
        }
        Set<UUID> sourceIds = new LinkedHashSet<>();
        Set<UUID> targetIds = new LinkedHashSet<>();
        for (ModelQualityRuleDefinition.ReferenceFieldMapping mapping : definition.mappings()) {
            if (!sourceIds.add(mapping.sourceFieldId())) {
                throw badRequest("引用存在规则不能重复使用同一源字段");
            }
            if (!targetIds.add(mapping.targetFieldId())) {
                throw badRequest("引用存在规则不能重复使用同一目标字段");
            }
            if (!fields.containsKey(mapping.sourceFieldId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "引用源字段不存在或不属于当前模型");
            }
        }
    }

    private static void validateTolerance(ViolationTolerance tolerance) {
        if (tolerance == null || tolerance.metric() == null || tolerance.value() == null) {
            throw badRequest("异常容忍配置不能为空");
        }
        if (tolerance.value().signum() < 0) {
            throw badRequest("异常容忍值不能小于 0");
        }
        if (tolerance.metric() == ViolationMetric.COUNT
                && tolerance.value().stripTrailingZeros().scale() > 0) {
            throw badRequest("按条数计算时容忍值必须是整数");
        }
        if (tolerance.metric() == ViolationMetric.PERCENT
                && tolerance.value().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw badRequest("异常比例不能大于 100%");
        }
    }

    private static void validateRange(
            ModelQualityRuleDefinition.ValueRangeDefinition range,
            PlatformDataType type
    ) {
        String minimum = normalizeBound(range.minimum());
        String maximum = normalizeBound(range.maximum());
        if (minimum == null && maximum == null) {
            throw badRequest("取值范围至少需要填写下界或上界");
        }
        Comparable<?> min = minimum == null ? null : parseBound(minimum, type);
        Comparable<?> max = maximum == null ? null : parseBound(maximum, type);
        if (min != null && max != null) {
            @SuppressWarnings("unchecked")
            int comparison = ((Comparable<Object>) min).compareTo(max);
            if (comparison > 0 || (comparison == 0 && (!range.minimumInclusive() || !range.maximumInclusive()))) {
                throw badRequest("取值范围下界不能大于上界，且相等时两端必须包含边界");
            }
        }
    }

    private static Comparable<?> parseBound(String value, PlatformDataType type) {
        try {
            return switch (type) {
                case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL -> new BigDecimal(value);
                case DATE -> LocalDate.parse(value);
                case TIMESTAMP -> Instant.parse(value);
                case TIMESTAMP_NTZ -> LocalDateTime.parse(value);
                default -> throw badRequest("当前字段类型不支持取值范围");
            };
        } catch (RuntimeException exception) {
            if (exception instanceof ResponseStatusException responseStatusException) {
                throw responseStatusException;
            }
            throw badRequest("取值范围边界格式与字段类型不匹配：" + value);
        }
    }

    private static String normalizeBound(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String normalizeRegex(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private void requireUnique(UUID modelId, UUID ignoredId, String name, ModelQualityRuleDefinition definition) {
        String normalizedName = name.trim();
        String key = semanticKey(definition);
        List<DataModelField> currentFields = fields(modelId);
        for (ModelQualityRule existing : repository.findAllByModelIdOrderByCreatedAtAsc(modelId)) {
            if (Objects.equals(existing.getId(), ignoredId)) {
                continue;
            }
            if (existing.getName().equalsIgnoreCase(normalizedName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "模型内已存在同名质量规则");
            }
            if (semanticKey(safeNormalizeDefinition(readDefinition(existing), currentFields)).equals(key)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "模型内已存在语义相同的质量规则");
            }
        }
    }

    private void requireUniqueIncludingPending(
            UUID modelId,
            List<ModelQualityRule> pending,
            String name,
            ModelQualityRuleDefinition definition
    ) {
        requireUnique(modelId, null, name, definition);
        String normalizedName = name.trim();
        String key = semanticKey(definition);
        if (pending.stream().anyMatch(rule -> rule.getName().equalsIgnoreCase(normalizedName))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "本次采纳包含重复的规则名称");
        }
        List<DataModelField> currentFields = fields(modelId);
        if (pending.stream().map(this::readDefinition)
                .map(item -> safeNormalizeDefinition(item, currentFields))
                .map(ModelQualityRuleService::semanticKey).anyMatch(key::equals)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "本次采纳包含语义重复的规则");
        }
    }

    private List<Suggestion> suggestionCandidates(List<DataModelField> fields) {
        List<Suggestion> suggestions = new ArrayList<>();
        for (DataModelField field : fields) {
            if (!field.isNullable() || field.isPrimaryKey()) {
                suggestions.add(new Suggestion(
                        suggestionName(field, "不能为空"),
                        field.isPrimaryKey() ? "主键字段必须存在值" : "字段定义为不允许为空",
                        field.isPrimaryKey() ? ModelQualityRuleSeverity.CRITICAL : ModelQualityRuleSeverity.MAJOR,
                        new ModelQualityRuleDefinition.NotNullDefinition(field.getId(), ZERO_VIOLATIONS)
                ));
            }
            if (field.getStandardDictionaryId() != null) {
                suggestions.add(new Suggestion(
                        suggestionName(field, "符合码表"),
                        "字段已绑定标准码表",
                        ModelQualityRuleSeverity.MAJOR,
                        new ModelQualityRuleDefinition.DictionaryMembershipDefinition(field.getId(), ZERO_VIOLATIONS)
                ));
            }
            if (field.getPhysicalColumnRole() == DataModelPhysicalColumnRole.TIME_KEY
                    && supportsFreshness(field.getFieldType())) {
                suggestions.add(new Suggestion(
                        suggestionName(field, "新鲜度"),
                        "字段角色为时间键，默认建议最大延迟 24 小时",
                        ModelQualityRuleSeverity.MAJOR,
                        new ModelQualityRuleDefinition.FreshnessDefinition(field.getId(), 1_440)
                ));
            }
            if (field.getFieldType() == PlatformDataType.GEOMETRY) {
                suggestions.add(new Suggestion(
                        suggestionName(field, "空间有效"),
                        "字段类型为 Geometry",
                        ModelQualityRuleSeverity.MAJOR,
                        new ModelQualityRuleDefinition.GeometryValidDefinition(field.getId(), ZERO_VIOLATIONS)
                ));
                suggestions.add(new Suggestion(
                        suggestionName(field, "空间非空"),
                        "字段类型为 Geometry",
                        ModelQualityRuleSeverity.MINOR,
                        new ModelQualityRuleDefinition.GeometryNonEmptyDefinition(field.getId(), ZERO_VIOLATIONS)
                ));
            }
        }
        List<UUID> primaryKeyFields = fields.stream().filter(DataModelField::isPrimaryKey)
                .sorted(Comparator.comparingInt(DataModelField::getSortOrder).thenComparing(DataModelField::getCode))
                .map(DataModelField::getId)
                .toList();
        if (!primaryKeyFields.isEmpty()) {
            suggestions.add(new Suggestion(
                    "主键组合唯一", "模型已定义主键字段", ModelQualityRuleSeverity.CRITICAL,
                    new ModelQualityRuleDefinition.UniqueDefinition(primaryKeyFields, ZERO_VIOLATIONS)
            ));
        }
        return suggestions;
    }

    private static String suggestionName(DataModelField field, String suffix) {
        return field.getName() + "（" + field.getCode() + "）" + suffix;
    }

    private ModelQualityRuleResponse response(ModelQualityRule rule, List<DataModelField> fields) {
        ModelQualityRuleDefinition definition = readDefinition(rule);
        return ModelQualityRuleResponse.from(
                rule,
                definition,
                fieldResponses(referencedFieldIds(definition), fields),
                referenceTarget(definition)
        );
    }

    private ModelQualityRuleReferenceTargetResponse referenceTarget(ModelQualityRuleDefinition definition) {
        if (!(definition instanceof ModelQualityRuleDefinition.ReferenceExistsDefinition reference)) {
            return null;
        }
        return modelRepository.findById(reference.targetModelId())
                .map(model -> ModelQualityRuleReferenceTargetResponse.from(
                        model,
                        fieldResponses(
                                reference.mappings().stream()
                                        .map(ModelQualityRuleDefinition.ReferenceFieldMapping::targetFieldId)
                                        .toList(),
                                fields(model.getId())
                        )
                ))
                .orElse(null);
    }

    private List<ModelQualityRuleFieldResponse> fieldResponses(
            List<UUID> ids,
            List<DataModelField> fields
    ) {
        Map<UUID, DataModelField> fieldsById = fields.stream()
                .collect(Collectors.toMap(DataModelField::getId, field -> field));
        List<UUID> dictionaryIds = ids.stream().map(fieldsById::get).filter(Objects::nonNull)
                .map(DataModelField::getStandardDictionaryId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, StandardDictionarySummaryResponse> dictionaries = dictionaryValueSupport.summaries(dictionaryIds);
        return ids.stream().map(fieldsById::get).filter(Objects::nonNull)
                .map(field -> ModelQualityRuleFieldResponse.from(
                        field, field.getStandardDictionaryId() == null
                                ? null
                                : dictionaries.get(field.getStandardDictionaryId())
                ))
                .toList();
    }

    private static List<UUID> referencedFieldIds(ModelQualityRuleDefinition definition) {
        if (definition instanceof ModelQualityRuleDefinition.NotNullDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.UniqueDefinition item) return item.fieldIds();
        if (definition instanceof ModelQualityRuleDefinition.ValueRangeDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.StringLengthDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.DictionaryMembershipDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.FreshnessDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.GeometryValidDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.GeometryNonEmptyDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.FormatPatternDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.ConditionalNotNullDefinition item) {
            return List.of(item.targetFieldId(), item.condition().fieldId());
        }
        if (definition instanceof ModelQualityRuleDefinition.FieldComparisonDefinition item) {
            return List.of(item.leftFieldId(), item.rightFieldId());
        }
        if (definition instanceof ModelQualityRuleDefinition.ReferenceExistsDefinition item) {
            return item.mappings().stream()
                    .map(ModelQualityRuleDefinition.ReferenceFieldMapping::sourceFieldId)
                    .toList();
        }
        return List.of();
    }

    private static String semanticKey(ModelQualityRuleDefinition definition) {
        if (definition instanceof ModelQualityRuleDefinition.UniqueDefinition item) {
            return definition.ruleType() + ":" + item.fieldIds().stream().map(UUID::toString).collect(Collectors.joining(","));
        }
        if (definition instanceof ModelQualityRuleDefinition.ConditionalNotNullDefinition item) {
            return definition.ruleType() + ":" + item.targetFieldId() + ":" + item.condition().fieldId()
                    + ":" + item.condition().operator() + ":" + String.join(",", item.condition().values());
        }
        if (definition instanceof ModelQualityRuleDefinition.FieldComparisonDefinition item) {
            return definition.ruleType() + ":" + item.leftFieldId() + ":" + item.operator()
                    + ":" + item.rightFieldId();
        }
        if (definition instanceof ModelQualityRuleDefinition.ReferenceExistsDefinition item) {
            return definition.ruleType() + ":" + item.targetModelId() + ":"
                    + item.mappings().stream()
                    .map(mapping -> mapping.sourceFieldId() + "=" + mapping.targetFieldId())
                    .collect(Collectors.joining(","));
        }
        List<UUID> ids = referencedFieldIds(definition);
        return definition.ruleType() + ":" + ids.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    private static UUID referenceModelId(ModelQualityRuleDefinition definition) {
        return definition instanceof ModelQualityRuleDefinition.ReferenceExistsDefinition reference
                ? reference.targetModelId()
                : null;
    }

    private ModelQualityRuleDefinition readDefinition(ModelQualityRule rule) {
        try {
            ModelQualityRuleDefinition definition = objectMapper.readValue(
                    rule.getDefinitionJson(), ModelQualityRuleDefinition.class
            );
            if (definition.ruleType() != rule.getRuleType()) {
                throw new IllegalStateException("质量规则类型与定义不一致");
            }
            return definition;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("已保存的质量规则定义无效：" + rule.getId(), exception);
        }
    }

    private String writeDefinition(ModelQualityRuleDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("质量规则定义序列化失败", exception);
        }
    }

    private List<DataModelField> fields(UUID modelId) {
        return fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(modelId);
    }

    private void requireModel(UUID modelId) {
        if (!modelRepository.existsById(modelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在");
        }
    }

    private ModelQualityRule requireRule(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "质量规则不存在"));
    }

    private static boolean supportsUnique(PlatformDataType type) {
        return type != PlatformDataType.BINARY && type != PlatformDataType.GEOMETRY;
    }

    private static boolean supportsRange(PlatformDataType type) {
        return switch (type) {
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL, DATE, TIMESTAMP, TIMESTAMP_NTZ -> true;
            default -> false;
        };
    }

    private static boolean supportsFreshness(PlatformDataType type) {
        return type == PlatformDataType.DATE || type == PlatformDataType.TIMESTAMP
                || type == PlatformDataType.TIMESTAMP_NTZ;
    }

    private static boolean supportsCondition(PlatformDataType type) {
        return type != PlatformDataType.BINARY && type != PlatformDataType.GEOMETRY;
    }

    private static boolean supportsComparison(
            PlatformDataType left,
            QualityFieldComparisonOperator operator,
            PlatformDataType right
    ) {
        if (!supportsCondition(left) || !supportsCondition(right)) {
            return false;
        }
        if (isNumeric(left) && isNumeric(right)) {
            return true;
        }
        if (left != right) {
            return false;
        }
        if (left == PlatformDataType.STRING || left == PlatformDataType.BOOLEAN) {
            return operator == QualityFieldComparisonOperator.EQ
                    || operator == QualityFieldComparisonOperator.NE;
        }
        return left == PlatformDataType.DATE || left == PlatformDataType.TIMESTAMP
                || left == PlatformDataType.TIMESTAMP_NTZ;
    }

    private static boolean supportsReference(PlatformDataType source, PlatformDataType target) {
        if (!supportsCondition(source) || !supportsCondition(target)) {
            return false;
        }
        return source == target || (isNumeric(source) && isNumeric(target));
    }

    private static boolean isNumeric(PlatformDataType type) {
        return switch (type) {
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL -> true;
            default -> false;
        };
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record Suggestion(
            String name,
            String reason,
            ModelQualityRuleSeverity severity,
            ModelQualityRuleDefinition definition
    ) {
    }

    private record Compatibility(boolean valid, String code, String reason) {
        static Compatibility validResult() { return new Compatibility(true, null, null); }
        static Compatibility invalid(String code, String reason) { return new Compatibility(false, code, reason); }
    }
}
