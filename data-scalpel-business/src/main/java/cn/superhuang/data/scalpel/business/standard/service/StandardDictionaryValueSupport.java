package cn.superhuang.data.scalpel.business.standard.service;

import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.ModelFieldTemplateItem;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.ModelFieldTemplateItemRepository;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionary;
import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionaryItem;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryItemRepository;
import cn.superhuang.data.scalpel.business.standard.repository.StandardDictionaryRepository;
import cn.superhuang.data.scalpel.business.standard.web.response.StandardDictionarySummaryResponse;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StandardDictionaryValueSupport {

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)");

    private final StandardDictionaryRepository dictionaryRepository;
    private final StandardDictionaryItemRepository itemRepository;
    private final DataModelFieldRepository fieldRepository;
    private final ModelFieldTemplateItemRepository templateItemRepository;

    public StandardDictionaryValueSupport(
            StandardDictionaryRepository dictionaryRepository,
            StandardDictionaryItemRepository itemRepository,
            DataModelFieldRepository fieldRepository,
            ModelFieldTemplateItemRepository templateItemRepository
    ) {
        this.dictionaryRepository = dictionaryRepository;
        this.itemRepository = itemRepository;
        this.fieldRepository = fieldRepository;
        this.templateItemRepository = templateItemRepository;
    }

    @Transactional(readOnly = true)
    public StandardDictionary requireDictionary(UUID id) {
        return dictionaryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "码表不存在"));
    }

    @Transactional(readOnly = true)
    public Map<UUID, StandardDictionarySummaryResponse> summaries(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return dictionaryRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(
                        StandardDictionary::getId,
                        StandardDictionarySummaryResponse::from,
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));
    }

    @Transactional(readOnly = true)
    public void validateAssignment(
            UUID requestedDictionaryId,
            UUID currentDictionaryId,
            PlatformDataType fieldType,
            Integer length,
            Integer precision,
            Integer scale
    ) {
        if (requestedDictionaryId == null) {
            return;
        }
        StandardDictionary dictionary = requireDictionary(requestedDictionaryId);
        if (!Objects.equals(requestedDictionaryId, currentDictionaryId) && !dictionary.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能新绑定已停用的码表：" + dictionary.getName());
        }
        validateTypeFamily(dictionary.getValueType(), fieldType, dictionary.getName());
        List<StandardDictionaryItem> items =
                itemRepository.findAllByDictionaryIdOrderBySortOrderAscNameAscCodeAsc(requestedDictionaryId);
        for (StandardDictionaryItem item : items) {
            if (!representable(item.getCode(), fieldType, length, precision, scale)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "码表值“" + item.getCode() + "”无法由字段类型安全表达：" + dictionary.getName()
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public void validateValueAgainstReferences(StandardDictionary dictionary, String normalizedValue) {
        List<DataModelField> fields = fieldRepository.findAllByStandardDictionaryId(dictionary.getId());
        List<String> conflicts = new java.util.ArrayList<>(fields.stream()
                .filter(field -> !representable(
                        normalizedValue,
                        field.getFieldType(),
                        field.getLength(),
                        field.getPrecision(),
                        field.getScale()
                ))
                .limit(8)
                .map(field -> field.getCode() + "(" + field.getFieldType() + ")")
                .toList());
        if (conflicts.size() < 8) {
            List<ModelFieldTemplateItem> templateFields =
                    templateItemRepository.findAllByStandardDictionaryId(dictionary.getId());
            templateFields.stream()
                    .filter(field -> !representable(
                            normalizedValue,
                            field.getFieldType(),
                            field.getLength(),
                            field.getPrecision(),
                            field.getScale()
                    ))
                    .limit(8L - conflicts.size())
                    .map(field -> "模板字段 " + field.getCode() + "(" + field.getFieldType() + ")")
                    .forEach(conflicts::add);
        }
        if (!conflicts.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "码表值“" + normalizedValue + "”无法由已绑定模型字段或模板字段安全表达："
                            + String.join("、", conflicts)
            );
        }
    }

    public String normalizeItemValue(PlatformDataType valueType, String value) {
        StandardDictionary.requireSupportedType(valueType);
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点编码不能为空");
        }
        try {
            return switch (valueType) {
                case STRING -> normalized;
                case INTEGER -> Integer.toString(Integer.parseInt(normalized));
                case LONG -> Long.toString(Long.parseLong(normalized));
                case DECIMAL -> normalizeDecimal(normalized);
                case BOOLEAN -> normalizeBoolean(normalized);
                default -> throw new IllegalArgumentException("不支持的码表取值类型：" + valueType);
            };
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "节点编码不是合法的 " + valueType + " 值：" + normalized,
                    exception
            );
        }
    }

    private static String normalizeDecimal(String value) {
        if (!PLAIN_DECIMAL.matcher(value).matches()) {
            throw new NumberFormatException("不允许科学计数法");
        }
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.signum() == 0) {
            return "0";
        }
        return decimal.stripTrailingZeros().toPlainString();
    }

    private static String normalizeBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return "true";
        }
        if ("false".equalsIgnoreCase(value)) {
            return "false";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "BOOLEAN 节点编码只能填写 true 或 false");
    }

    private static void validateTypeFamily(
            PlatformDataType dictionaryType,
            PlatformDataType fieldType,
            String dictionaryName
    ) {
        boolean compatible = switch (dictionaryType) {
            case STRING -> fieldType == PlatformDataType.STRING;
            case BOOLEAN -> fieldType == PlatformDataType.BOOLEAN;
            case INTEGER, LONG, DECIMAL -> fieldType == PlatformDataType.BYTE
                    || fieldType == PlatformDataType.SHORT
                    || fieldType == PlatformDataType.INTEGER
                    || fieldType == PlatformDataType.LONG
                    || fieldType == PlatformDataType.DECIMAL;
            default -> false;
        };
        if (!compatible) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "字段类型 " + fieldType + " 与码表取值类型 " + dictionaryType + " 不兼容：" + dictionaryName
            );
        }
    }

    private static boolean representable(
            String value,
            PlatformDataType fieldType,
            Integer length,
            Integer precision,
            Integer scale
    ) {
        try {
            return switch (fieldType) {
                case STRING -> length == null || value.length() <= length;
                case BOOLEAN -> "true".equals(value) || "false".equals(value);
                case BYTE -> integral(value).compareTo(BigInteger.valueOf(Byte.MIN_VALUE)) >= 0
                        && integral(value).compareTo(BigInteger.valueOf(Byte.MAX_VALUE)) <= 0;
                case SHORT -> integral(value).compareTo(BigInteger.valueOf(Short.MIN_VALUE)) >= 0
                        && integral(value).compareTo(BigInteger.valueOf(Short.MAX_VALUE)) <= 0;
                case INTEGER -> integral(value).compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0
                        && integral(value).compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0;
                case LONG -> integral(value).compareTo(BigInteger.valueOf(Long.MIN_VALUE)) >= 0
                        && integral(value).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0;
                case DECIMAL -> decimalFits(value, precision, scale);
                default -> false;
            };
        } catch (ArithmeticException | NumberFormatException exception) {
            return false;
        }
    }

    private static BigInteger integral(String value) {
        return new BigDecimal(value).toBigIntegerExact();
    }

    private static boolean decimalFits(String value, Integer precision, Integer scale) {
        if (precision == null || scale == null) {
            return false;
        }
        BigDecimal scaled = new BigDecimal(value).setScale(scale, RoundingMode.UNNECESSARY);
        return scaled.precision() <= precision;
    }
}
