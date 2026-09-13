package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

@JsonClassDescription("单个字段的原位平台类型转换规则。普通转换按 failureStrategy 使用 cast 或 try_cast；Epoch、字符串日期时间解析、日期时间字符串格式化三类特殊选项互斥，并且只适用于各自声明的来源与目标类型。Compiler 只分析计划，不读取真实值或保证运行时可转换。")
public record ColumnTypeCast(
        @JsonPropertyDescription("要原位转换的来源字段名；必须存在且在同一 TypeCastOperation 中唯一。Geometry 和流式事件时间字段不允许转换。")
        String columnName,
        @JsonPropertyDescription("必填的目标平台类型。STRING 可带正整数 length；DECIMAL 要求 precision 1 到 38、scale 0 到 precision；其他标量不携带参数。GEOMETRY 不支持。")
        PlatformTypeDefinition targetType,
        @JsonPropertyDescription("必填的真实值转换失败策略：FAIL 使用 ANSI 普通 cast 或严格日期时间函数并使节点失败；SET_NULL 使用 try_cast/try_to_date/try_to_timestamp 或溢出安全运算，把失败值输出为 SQL NULL。不会跳过记录。")
        CastFailureStrategy failureStrategy,
        @JsonPropertyDescription("可选 Epoch 单位，只允许 LONG 转 TIMESTAMP，或 DATE/TIMESTAMP 转 LONG；TIMESTAMP_NTZ 不支持。单位不会根据数值位数推断；与两类字符串日期时间选项互斥。")
        EpochTimestampUnit epochTimestampUnit,
        @JsonPropertyDescription("可选的 STRING 转 DATE/TIMESTAMP 显式解析设置；与 epochTimestampUnit、temporalStringFormatOptions 互斥。TIMESTAMP_NTZ 仍使用普通 Spark cast。")
        StringTemporalParseOptions stringTemporalParseOptions,
        @JsonPropertyDescription("可选的 DATE/TIMESTAMP/TIMESTAMP_NTZ 转 STRING 显式格式化设置；与 epochTimestampUnit、stringTemporalParseOptions 互斥。NULL 输入始终输出 NULL。")
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
