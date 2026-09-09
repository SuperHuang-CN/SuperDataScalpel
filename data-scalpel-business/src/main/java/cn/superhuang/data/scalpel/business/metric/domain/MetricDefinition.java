package cn.superhuang.data.scalpel.business.metric.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/** Business documentation and materialized-result metadata; never an executable expression. */
public record MetricDefinition(
        @Size(max=10000) String businessMeaning, @Size(max=20000) String calculation,
        @Size(max=10000) String statisticalScope, @Size(max=10000) String timeDescription,
        @Size(max=2000) String sourceGrain, @Size(max=2000) String grainDescription,
        @Size(max=32) String unit, Period statisticalPeriod, @Size(max=100) String periodFormat,
        @Size(max=10000) String nullHandling, @Size(max=10000) String aggregationDescription,
        @Size(max=2000) String updateDescription, @Min(0) @Max(10) Integer decimalPlaces,
        ValueFormat valueFormat, @Valid Binding binding,
        @Size(max=50) List<@NotNull @Valid Reference> references
) {
    public MetricDefinition {
        statisticalPeriod = statisticalPeriod == null ? Period.NONE : statisticalPeriod;
        valueFormat = valueFormat == null ? ValueFormat.NUMBER : valueFormat;
        decimalPlaces = decimalPlaces == null ? 2 : decimalPlaces;
        references = references == null ? List.of() : List.copyOf(references);
    }
    public static MetricDefinition empty() {
        return new MetricDefinition(null,null,null,null,null,null,null,Period.NONE,null,null,null,null,2,ValueFormat.NUMBER,null,List.of());
    }
    public enum Period { NONE, DAY, WEEK, MONTH, QUARTER, YEAR }
    public enum ValueFormat { NUMBER, RATIO, PERCENT_VALUE }
    public enum Operator { EQ, NE, GT, GE, LT, LE, IS_NULL, IS_NOT_NULL }
    public enum ResourceKind { MODEL, MODEL_FIELD, METRIC, DATA_SERVICE }
    public record Binding(UUID modelId, UUID valueFieldId, UUID periodFieldId,
            @Size(max=20) List<@NotNull @Valid Dimension> dimensions,
            @Size(max=4) List<@NotNull UUID> supportingFieldIds,
            @Size(max=8) List<@NotNull @Valid FixedFilter> fixedFilters) {
        public Binding {
            dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
            supportingFieldIds = supportingFieldIds == null ? List.of() : List.copyOf(supportingFieldIds);
            fixedFilters = fixedFilters == null ? List.of() : List.copyOf(fixedFilters);
        }
    }
    public record Dimension(@Size(max=64) String key, @Size(max=100) String name,
            @Size(max=1000) String description, UUID fieldId) {}
    public record FixedFilter(UUID fieldId, Operator operator, @Size(max=2000) String value) {}
    public record Reference(@NotNull ResourceKind resourceKind, @NotNull UUID resourceId,
            UUID parentModelId, @Min(1) Integer targetVersion, @Size(max=100) String role,
            @Size(max=2000) String note) {}
}
