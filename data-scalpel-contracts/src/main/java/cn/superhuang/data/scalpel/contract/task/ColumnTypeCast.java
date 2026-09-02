package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

public record ColumnTypeCast(
        String columnName,
        PlatformTypeDefinition targetType,
        CastFailureStrategy failureStrategy,
        EpochTimestampUnit epochTimestampUnit,
        StringTemporalParseOptions stringTemporalParseOptions,
        TemporalStringFormatOptions temporalStringFormatOptions
) {
    /** Compatibility constructor for Canvas definitions written before epoch-unit support. */
    public ColumnTypeCast(
            String columnName,
            PlatformTypeDefinition targetType,
            CastFailureStrategy failureStrategy
    ) {
        this(columnName, targetType, failureStrategy, null, null, null);
    }

    /** Compatibility constructor for Canvas definitions written before string temporal parsing support. */
    public ColumnTypeCast(
            String columnName,
            PlatformTypeDefinition targetType,
            CastFailureStrategy failureStrategy,
            EpochTimestampUnit epochTimestampUnit
    ) {
        this(columnName, targetType, failureStrategy, epochTimestampUnit, null, null);
    }

    /** Compatibility constructor for Canvas definitions written before temporal-to-string formatting support. */
    public ColumnTypeCast(
            String columnName,
            PlatformTypeDefinition targetType,
            CastFailureStrategy failureStrategy,
            EpochTimestampUnit epochTimestampUnit,
            StringTemporalParseOptions stringTemporalParseOptions
    ) {
        this(columnName, targetType, failureStrategy, epochTimestampUnit, stringTemporalParseOptions, null);
    }
}
