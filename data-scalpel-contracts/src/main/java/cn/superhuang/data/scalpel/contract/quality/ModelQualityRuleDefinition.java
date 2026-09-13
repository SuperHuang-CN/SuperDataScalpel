package cn.superhuang.data.scalpel.contract.quality;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.NotNullDefinition.class, name = "NOT_NULL"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.UniqueDefinition.class, name = "UNIQUE"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.ValueRangeDefinition.class, name = "VALUE_RANGE"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.StringLengthDefinition.class, name = "STRING_LENGTH"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.DictionaryMembershipDefinition.class, name = "DICTIONARY_MEMBERSHIP"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.RowCountDefinition.class, name = "ROW_COUNT"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.FreshnessDefinition.class, name = "FRESHNESS"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.GeometryValidDefinition.class, name = "GEOMETRY_VALID"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.GeometryNonEmptyDefinition.class, name = "GEOMETRY_NON_EMPTY"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.FormatPatternDefinition.class, name = "FORMAT_PATTERN"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.ConditionalNotNullDefinition.class, name = "CONDITIONAL_NOT_NULL"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.FieldComparisonDefinition.class, name = "FIELD_COMPARISON"),
        @JsonSubTypes.Type(value = ModelQualityRuleDefinition.ReferenceExistsDefinition.class, name = "REFERENCE_EXISTS")
})
@JsonClassDescription("模型质量规则的可执行定义；type 决定字段引用、阈值及空值、唯一性、范围、格式、时效、空间或引用完整性检查结构。")
public sealed interface ModelQualityRuleDefinition permits
        ModelQualityRuleDefinition.NotNullDefinition,
        ModelQualityRuleDefinition.UniqueDefinition,
        ModelQualityRuleDefinition.ValueRangeDefinition,
        ModelQualityRuleDefinition.StringLengthDefinition,
        ModelQualityRuleDefinition.DictionaryMembershipDefinition,
        ModelQualityRuleDefinition.RowCountDefinition,
        ModelQualityRuleDefinition.FreshnessDefinition,
        ModelQualityRuleDefinition.GeometryValidDefinition,
        ModelQualityRuleDefinition.GeometryNonEmptyDefinition,
        ModelQualityRuleDefinition.FormatPatternDefinition,
        ModelQualityRuleDefinition.ConditionalNotNullDefinition,
        ModelQualityRuleDefinition.FieldComparisonDefinition,
        ModelQualityRuleDefinition.ReferenceExistsDefinition {

    ModelQualityRuleType ruleType();

    @JsonClassDescription("检查指定模型字段必须非空，并按 tolerance 判断允许的 NULL 违规量。")
    record NotNullDefinition(
            @JsonPropertyDescription("必须具有非 NULL 值的当前模型字段 UUID。")
            @NotNull UUID fieldId,
            @JsonPropertyDescription("允许出现的 NULL 行数或占目标模型全部行数的百分比。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.NOT_NULL; }
    }

    @JsonClassDescription("检查一个或多个模型字段的非空组合值必须唯一，并按 tolerance 判断允许的重复行量。")
    record UniqueDefinition(
            @JsonPropertyDescription("用于判断组合唯一性的 1 到 16 个当前模型字段 UUID；不能重复，服务端按模型字段顺序规范化。任一字段为 NULL 的行不参与重复检查。")
            @NotNull @NotEmpty @Size(max = 16) List<@NotNull UUID> fieldIds,
            @JsonPropertyDescription("允许处于重复组中的行数或占目标模型全部行数的百分比；重复组中的每一行都计为违规。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.UNIQUE; }
    }

    @JsonClassDescription("检查数值或日期时间字段是否位于声明的开闭区间内，并按 tolerance 判断越界量。")
    record ValueRangeDefinition(
            @JsonPropertyDescription("要检查的数值、DATE、TIMESTAMP 或 TIMESTAMP_NTZ 模型字段 UUID；NULL 值不计为违规。")
            @NotNull UUID fieldId,
            @JsonPropertyDescription("可选下界。数值使用十进制文本，DATE 使用 ISO 日期，TIMESTAMP 使用带时区 Instant，TIMESTAMP_NTZ 使用本地日期时间；与 maximum 至少提供一个。")
            String minimum,
            @JsonPropertyDescription("可选上界，格式与 minimum 相同；同时提供上下界时下界不能大于上界。")
            String maximum,
            @JsonPropertyDescription("是否允许值等于 minimum；minimum 为空时该字段不影响判断。")
            boolean minimumInclusive,
            @JsonPropertyDescription("是否允许值等于 maximum；maximum 为空时该字段不影响判断。上下界相等时两端都必须包含。")
            boolean maximumInclusive,
            @JsonPropertyDescription("允许超出范围的行数或占目标模型全部行数的百分比。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.VALUE_RANGE; }
    }

    @JsonClassDescription("检查字符串字段字符数是否位于声明范围内，并按 tolerance 判断长度越界量。")
    record StringLengthDefinition(
            @JsonPropertyDescription("要检查的 STRING 模型字段 UUID；NULL 值不计为违规。")
            @NotNull UUID fieldId,
            @JsonPropertyDescription("允许的最小字符串字符数，必须大于或等于 0；与 maximumLength 至少提供一个。")
            Integer minimumLength,
            @JsonPropertyDescription("允许的最大字符串字符数，必须大于或等于 0，且不能小于 minimumLength。")
            Integer maximumLength,
            @JsonPropertyDescription("允许长度越界的行数或占目标模型全部行数的百分比。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.STRING_LENGTH; }
    }

    @JsonClassDescription("检查字段的非空值是否属于其绑定标准字典的有效值快照，并按 tolerance 判断违规量。")
    record DictionaryMembershipDefinition(
            @JsonPropertyDescription("已绑定标准字典的当前模型字段 UUID；运行时按字典有效值快照检查，NULL 值不计为违规。")
            @NotNull UUID fieldId,
            @JsonPropertyDescription("允许不属于有效字典值的行数或占目标模型全部行数的百分比。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.DICTIONARY_MEMBERSHIP; }
    }

    @JsonClassDescription("检查目标模型全量数据是否达到声明的最小行数。")
    record RowCountDefinition(
            @JsonPropertyDescription("目标模型全量数据必须达到的最小行数，至少为 1；实际行数小于该值时规则失败。")
            long minimumRowCount
    ) implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.ROW_COUNT; }
    }

    @JsonClassDescription("按日期时间字段的最大非空值检查目标模型相对 Runner 启动时间的数据新鲜度。")
    record FreshnessDefinition(
            @JsonPropertyDescription("用于判断新鲜度的 DATE、TIMESTAMP 或 TIMESTAMP_NTZ 模型字段 UUID。")
            @NotNull UUID fieldId,
            @JsonPropertyDescription("字段最大非空时间值相对 Runner 启动时间允许落后的分钟数，至少为 1；字段全为 NULL 时规则失败。")
            long maximumDelayMinutes
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.FRESHNESS; }
    }

    @JsonClassDescription("使用空间有效性判断检查非空 Geometry 字段，并按 tolerance 判断无效几何量。")
    record GeometryValidDefinition(
            @JsonPropertyDescription("使用 ST_IsValid 检查的 GEOMETRY 模型字段 UUID；NULL 值不计为违规。")
            @NotNull UUID fieldId,
            @JsonPropertyDescription("允许无效 Geometry 的行数或占目标模型全部行数的百分比。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.GEOMETRY_VALID; }
    }

    @JsonClassDescription("检查非空 Geometry 字段是否为空几何，并按 tolerance 判断空几何量。")
    record GeometryNonEmptyDefinition(
            @JsonPropertyDescription("使用 ST_IsEmpty 检查的 GEOMETRY 模型字段 UUID；NULL 值不计为违规，空 Geometry 计为违规。")
            @NotNull UUID fieldId,
            @JsonPropertyDescription("允许空 Geometry 的行数或占目标模型全部行数的百分比。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.GEOMETRY_NON_EMPTY; }
    }

    @JsonClassDescription("按平台预置格式或自定义正则检查字符串字段的整串格式，并按 tolerance 判断违规量。")
    record FormatPatternDefinition(
            @JsonPropertyDescription("要执行整串格式匹配的 STRING 模型字段 UUID；NULL 值不计为违规。")
            @NotNull UUID fieldId,
            @JsonPropertyDescription("匹配方式：PRESET 使用平台预置格式，REGEX 使用自定义 Java/Spark 正则。")
            @NotNull FormatPatternKind patternKind,
            @JsonPropertyDescription("PRESET 时必填，可选居民身份证、统一社会信用代码、中国大陆手机号、邮箱或行政区划代码；REGEX 时必须为空。")
            FormatPatternPreset preset,
            @JsonPropertyDescription("REGEX 时必填的有效正则表达式，最长 500 字符，并按整串匹配；PRESET 时必须为空。")
            @Size(max = 500) String regex,
            @JsonPropertyDescription("允许格式不匹配的行数或占目标模型全部行数的百分比。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.FORMAT_PATTERN; }
    }

    @JsonClassDescription("条件非空规则使用的单字段条件；由 operator 决定 values 的数量、类型和比较语义。")
    record QualityCondition(
            @JsonPropertyDescription("用于判断条件是否成立的当前模型字段 UUID；不能与 ConditionalNotNullDefinition.targetFieldId 相同，也不能是 BINARY 或 GEOMETRY。")
            @NotNull UUID fieldId,
            @JsonPropertyDescription("条件操作符：EQ/NE 各需一个值，IN/NOT_IN 需至少一个值，IS_NULL/IS_NOT_NULL/IS_EMPTY/IS_NOT_EMPTY 不接收值；空串操作只支持 STRING。")
            @NotNull QualityConditionOperator operator,
            @JsonPropertyDescription("按条件字段类型解析且不能重复的常量列表，最多 100 个；DATE、TIMESTAMP、TIMESTAMP_NTZ 分别使用 ISO 日期、Instant 和本地日期时间格式。")
            @NotNull @Size(max = 100) List<@NotNull String> values
    ) {
    }

    @JsonClassDescription("只对满足 condition 的记录检查目标字段非空，并按 tolerance 判断条件内的 NULL 违规量。")
    record ConditionalNotNullDefinition(
            @JsonPropertyDescription("condition 成立时必须具有非 NULL 值的当前模型字段 UUID。")
            @NotNull UUID targetFieldId,
            @JsonPropertyDescription("决定哪些行需要检查目标字段的单字段条件。")
            @NotNull @Valid QualityCondition condition,
            @JsonPropertyDescription("允许“条件成立且目标字段为 NULL”的行数或占目标模型全部行数的百分比。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.CONDITIONAL_NOT_NULL; }
    }

    @JsonClassDescription("逐行比较两个兼容类型字段，并按 tolerance 判断比较不成立的记录量。")
    record FieldComparisonDefinition(
            @JsonPropertyDescription("比较表达式左侧的当前模型字段 UUID；不能与 rightFieldId 相同。任一字段为 NULL 的行不计为违规。")
            @NotNull UUID leftFieldId,
            @JsonPropertyDescription("字段比较符：EQ、NE、LT、LE、GT 或 GE；STRING/BOOLEAN 只支持 EQ/NE，数值类型之间可跨具体数值类型比较，日期时间要求类型相同。")
            @NotNull QualityFieldComparisonOperator operator,
            @JsonPropertyDescription("比较表达式右侧的当前模型字段 UUID。服务端可能按模型字段顺序交换左右字段，并同时反转有方向的比较符。")
            @NotNull UUID rightFieldId,
            @JsonPropertyDescription("允许比较不成立的行数或占目标模型全部行数的百分比。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.FIELD_COMPARISON; }
    }

    @JsonClassDescription("引用存在性规则中的一组源字段与目标模型字段映射。")
    record ReferenceFieldMapping(
            @JsonPropertyDescription("当前被检查模型中的源字段 UUID；同一规则内不能重复使用。")
            @NotNull UUID sourceFieldId,
            @JsonPropertyDescription("引用目标模型中的字段 UUID；同一规则内不能重复使用，类型必须与源字段相同或同为数值类型。")
            @NotNull UUID targetFieldId
    ) {
    }

    @JsonClassDescription("检查源字段非空组合是否存在于目标模型字段组合中，并按 tolerance 判断缺失引用量。")
    record ReferenceExistsDefinition(
            @JsonPropertyDescription("要检查组合键是否存在的引用目标模型 UUID；可以引用当前模型。")
            @NotNull UUID targetModelId,
            @JsonPropertyDescription("1 到 16 组源字段到目标字段映射；源字段组任一值为 NULL 的行不参与检查，非空组合在目标模型中不存在时计为违规。")
            @NotNull @NotEmpty @Size(max = 16) List<@NotNull @Valid ReferenceFieldMapping> mappings,
            @JsonPropertyDescription("允许引用组合不存在的行数或占被检查模型全部行数的百分比。")
            @NotNull @Valid ViolationTolerance tolerance
    )
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.REFERENCE_EXISTS; }
    }
}
