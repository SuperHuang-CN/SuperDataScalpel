package cn.superhuang.data.scalpel.contract.quality;

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

    record NotNullDefinition(@NotNull UUID fieldId, @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.NOT_NULL; }
    }

    record UniqueDefinition(@NotNull @NotEmpty @Size(max = 16) List<@NotNull UUID> fieldIds,
                            @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.UNIQUE; }
    }

    record ValueRangeDefinition(@NotNull UUID fieldId, String minimum, String maximum,
                                boolean minimumInclusive, boolean maximumInclusive,
                                @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.VALUE_RANGE; }
    }

    record StringLengthDefinition(@NotNull UUID fieldId, Integer minimumLength, Integer maximumLength,
                                  @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.STRING_LENGTH; }
    }

    record DictionaryMembershipDefinition(@NotNull UUID fieldId,
                                           @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.DICTIONARY_MEMBERSHIP; }
    }

    record RowCountDefinition(long minimumRowCount) implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.ROW_COUNT; }
    }

    record FreshnessDefinition(@NotNull UUID fieldId, long maximumDelayMinutes)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.FRESHNESS; }
    }

    record GeometryValidDefinition(@NotNull UUID fieldId, @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.GEOMETRY_VALID; }
    }

    record GeometryNonEmptyDefinition(@NotNull UUID fieldId, @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.GEOMETRY_NON_EMPTY; }
    }

    record FormatPatternDefinition(@NotNull UUID fieldId, @NotNull FormatPatternKind patternKind,
                                   FormatPatternPreset preset, @Size(max = 500) String regex,
                                   @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.FORMAT_PATTERN; }
    }

    record QualityCondition(@NotNull UUID fieldId, @NotNull QualityConditionOperator operator,
                            @NotNull @Size(max = 100) List<@NotNull String> values) {
    }

    record ConditionalNotNullDefinition(@NotNull UUID targetFieldId, @NotNull @Valid QualityCondition condition,
                                        @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.CONDITIONAL_NOT_NULL; }
    }

    record FieldComparisonDefinition(@NotNull UUID leftFieldId, @NotNull QualityFieldComparisonOperator operator,
                                     @NotNull UUID rightFieldId, @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.FIELD_COMPARISON; }
    }

    record ReferenceFieldMapping(@NotNull UUID sourceFieldId, @NotNull UUID targetFieldId) {
    }

    record ReferenceExistsDefinition(@NotNull UUID targetModelId,
                                     @NotNull @NotEmpty @Size(max = 16) List<@NotNull @Valid ReferenceFieldMapping> mappings,
                                     @NotNull @Valid ViolationTolerance tolerance)
            implements ModelQualityRuleDefinition {
        @Override public ModelQualityRuleType ruleType() { return ModelQualityRuleType.REFERENCE_EXISTS; }
    }
}
