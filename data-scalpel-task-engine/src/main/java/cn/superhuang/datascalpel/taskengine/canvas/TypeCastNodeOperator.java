package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CastFailureStrategy;
import cn.superhuang.data.scalpel.contract.task.ColumnTypeCast;
import cn.superhuang.data.scalpel.contract.task.EpochTimestampUnit;
import cn.superhuang.data.scalpel.contract.task.StringTemporalParseOptions;
import cn.superhuang.data.scalpel.contract.task.StringTimestampZoneMode;
import cn.superhuang.data.scalpel.contract.task.TemporalStringFormatOptions;
import cn.superhuang.data.scalpel.contract.task.TypeCastConfiguration;
import cn.superhuang.data.scalpel.contract.task.TypeCastOperation;
import cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class TypeCastNodeOperator implements CanvasNodeOperator {
    private static final long SECONDS_TO_MICROS = 1_000_000L;
    private static final long MILLISECONDS_TO_MICROS = 1_000L;

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TYPE_CAST;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH, CanvasExecutionMode.STREAMING);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof TypeCastNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "TYPE_CAST operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        TypeCastConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (!ProcessorOperationSupport.isInternalSingle(configuration.operations())) {
            return ProcessorOperationSupport.apply(configuration.operations(), inputs, context, false,
                    (operation, scopedContext) -> {
                        TypeCastOperation sourceOperation = (TypeCastOperation) operation.operation();
                        TypeCastConfiguration single = new TypeCastConfiguration(List.of(new TypeCastOperation(
                                ProcessorOperationSupport.INTERNAL_OPERATION_ID, operation.temporarySourceTableName(),
                                new ProcessorOutput.CreateNewTable(operation.outputTableName()), sourceOperation.casts()
                        )));
                        return apply(new TypeCastNodeDefinition(node.id(), node.name(), node.layout(), single),
                                Map.of(operation.temporarySourceTableName(), operation.source()), scopedContext);
                    });
        }

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.sourceTableName(),
                "请选择来源表",
                "configuration.sourceTableName",
                issues
        );
        CanvasNodeSupport.required(
                configuration.outputTableName(),
                "请输入输出表名",
                "configuration.outputTableName",
                issues
        );
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error(
                    "DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName"
            );
        }

        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null
                : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        if (configuration.casts() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "类型转换项必须是数组",
                    "configuration.casts"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.casts().isEmpty()) {
            issues.error(
                    "EMPTY_TYPE_CASTS",
                    "至少配置一个字段类型转换",
                    "configuration.casts"
            );
        }

        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of()
                : CanvasNodeSupport.columns(source.schema());
        Set<String> configuredColumns = new HashSet<>();
        for (int index = 0; index < configuration.casts().size(); index++) {
            ColumnTypeCast cast = configuration.casts().get(index);
            String path = "configuration.casts[" + index + "]";
            if (cast == null) {
                issues.error("REQUIRED_CONFIGURATION", "类型转换项不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(
                    cast.columnName(),
                    "请选择转换字段",
                    path + ".columnName",
                    issues
            );
            if (!CanvasNodeSupport.blank(cast.columnName())
                    && !configuredColumns.add(cast.columnName())) {
                issues.error(
                        "DUPLICATE_CAST_COLUMN",
                        "字段重复配置转换：" + cast.columnName(),
                        path + ".columnName"
                );
            }
            if (source != null
                    && !CanvasNodeSupport.blank(cast.columnName())
                    && !sourceColumns.containsKey(cast.columnName())) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "来源字段不存在：" + cast.columnName(),
                        path + ".columnName"
                );
            } else if (sourceColumns.get(cast.columnName()) != null
                    && sourceColumns.get(cast.columnName()).fieldType() == PlatformDataType.GEOMETRY) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "Geometry 字段不能转换为标量类型",
                        path + ".columnName"
                );
            }
            validateTargetType(cast.targetType(), path + ".targetType", issues);
            validateEpochTimestampUnit(
                    cast,
                    sourceColumns.get(cast.columnName()),
                    path + ".epochTimestampUnit",
                    issues
            );
            validateStringTemporalParseOptions(
                    cast,
                    sourceColumns.get(cast.columnName()),
                    path + ".stringTemporalParseOptions",
                    issues
            );
            validateTemporalStringFormatOptions(
                    cast,
                    sourceColumns.get(cast.columnName()),
                    path + ".temporalStringFormatOptions",
                    issues
            );
            if (cast.failureStrategy() == null) {
                issues.error(
                        "INVALID_CAST_FAILURE_STRATEGY",
                        "请选择转换失败策略",
                        path + ".failureStrategy"
                );
            }
            if (context.executionMode() == CanvasExecutionMode.STREAMING
                    && source != null
                    && cast.columnName() != null
                    && cast.columnName().equals(source.schema().eventTimeColumn())) {
                issues.error(
                        "STREAM_EVENT_TIME_COLUMN_IMMUTABLE",
                        "流任务不能转换事件时间字段：" + cast.columnName(),
                        path + ".columnName"
                );
            }
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Map<String, ColumnTypeCast> castsByColumn = new LinkedHashMap<>();
        configuration.casts().forEach(cast -> castsByColumn.put(cast.columnName(), cast));
        Dataset<Row> sourceDataset = source.dataset();
        List<Column> projection = new ArrayList<>(source.schema().columns().size());
        for (CanvasColumnSchema sourceColumn : source.schema().columns()) {
            Column sourceExpression = sourceDataset.col(
                    CanvasNodeSupport.quoteIdentifier(sourceColumn.name())
            );
            ColumnTypeCast cast = castsByColumn.get(sourceColumn.name());
            if (cast == null) {
                projection.add(sourceExpression);
                continue;
            }
            DataType targetDataType = SparkTypeMapper.toDataType(cast.targetType());
            Column converted;
            if (cast.temporalStringFormatOptions() != null) {
                converted = temporalString(
                        sourceExpression,
                        sourceColumn.fieldType(),
                        cast.temporalStringFormatOptions()
                );
            } else if (cast.stringTemporalParseOptions() != null) {
                converted = stringTemporal(
                        sourceExpression,
                        cast.targetType().type(),
                        cast.stringTemporalParseOptions(),
                        cast.failureStrategy()
                );
            } else if (cast.epochTimestampUnit() == null) {
                converted = cast.failureStrategy() == CastFailureStrategy.FAIL
                        ? sourceExpression.cast(targetDataType)
                        : sourceExpression.try_cast(targetDataType);
            } else {
                converted = epochTemporal(
                        sourceExpression,
                        sourceColumn.fieldType(),
                        cast.targetType().type(),
                        cast.epochTimestampUnit(),
                        cast.failureStrategy()
                );
            }
            projection.add(converted.alias(sourceColumn.name()));
        }
        Dataset<Row> castDataset = sourceDataset.select(projection.toArray(Column[]::new));
        List<CanvasColumnSchema> analyzedColumns = SparkTypeMapper.fromStructType(
                castDataset.schema(),
                List.of()
        );
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(analyzedColumns.size());
        for (int index = 0; index < analyzedColumns.size(); index++) {
            CanvasColumnSchema analyzed = analyzedColumns.get(index);
            ColumnTypeCast cast = castsByColumn.get(analyzed.name());
            outputColumns.add(cast == null
                    ? sourceColumns.get(analyzed.name())
                    : castColumn(analyzed, cast));
        }

        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                source.schema().origin(),
                outputColumns,
                source.schema().datasetKind(),
                source.schema().eventTimeColumn(),
                source.schema().watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(
                outputSchema.name(),
                new SparkCanvasTable(outputSchema, castDataset)
        );
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateTargetType(
            PlatformTypeDefinition targetType,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (targetType == null || targetType.type() == null) {
            issues.error(
                    "INVALID_TARGET_PLATFORM_TYPE",
                    "请选择目标平台类型",
                    path
            );
            return;
        }
        if (targetType.type() == PlatformDataType.GEOMETRY) {
            issues.error(
                    "INVALID_TARGET_PLATFORM_TYPE",
                    "Spark Canvas 类型转换暂不支持 GEOMETRY",
                    path + ".type"
            );
            return;
        }
        try {
            SparkTypeMapper.toDataType(targetType);
        } catch (RuntimeException exception) {
            issues.error(
                    "INVALID_TARGET_PLATFORM_TYPE",
                    "目标平台类型参数无效",
                    path
            );
        }
    }

    private static void validateEpochTimestampUnit(
            ColumnTypeCast cast,
            CanvasColumnSchema sourceColumn,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (cast.epochTimestampUnit() == null) return;
        PlatformTypeDefinition targetType = cast.targetType();
        if (targetType == null || (targetType.type() != PlatformDataType.TIMESTAMP
                && targetType.type() != PlatformDataType.LONG)) {
            issues.error(
                    "EPOCH_TIMESTAMP_UNIT_TARGET_TYPE_UNSUPPORTED",
                    "Epoch 单位仅支持 LONG 转 TIMESTAMP，或 DATE/TIMESTAMP 转 LONG",
                    path
            );
            return;
        }
        if (sourceColumn == null) return;
        if (targetType.type() == PlatformDataType.TIMESTAMP
                && sourceColumn.fieldType() != PlatformDataType.LONG) {
            issues.error(
                    "EPOCH_TIMESTAMP_UNIT_SOURCE_TYPE_UNSUPPORTED",
                    "转 TIMESTAMP 的 Epoch 单位仅支持 LONG 字段",
                    path
            );
        } else if (targetType.type() == PlatformDataType.LONG
                && sourceColumn.fieldType() != PlatformDataType.DATE
                && sourceColumn.fieldType() != PlatformDataType.TIMESTAMP) {
            issues.error(
                    "EPOCH_TIMESTAMP_UNIT_SOURCE_TYPE_UNSUPPORTED",
                    "转 LONG 的 Epoch 单位仅支持 DATE 或 TIMESTAMP 字段",
                    path
            );
        }
    }

    private static Column epochTemporal(
            Column source,
            PlatformDataType sourceType,
            PlatformDataType targetType,
            EpochTimestampUnit unit,
            CastFailureStrategy failureStrategy
    ) {
        return targetType == PlatformDataType.TIMESTAMP
                ? epochTimestamp(source, unit, failureStrategy)
                : temporalEpochLong(source, sourceType, unit, failureStrategy);
    }

    private static Column epochTimestamp(
            Column source,
            EpochTimestampUnit unit,
            CastFailureStrategy failureStrategy
    ) {
        if (failureStrategy == CastFailureStrategy.FAIL) {
            return switch (unit) {
                case SECONDS -> functions.timestamp_seconds(source);
                case MILLISECONDS -> functions.timestamp_millis(source);
                case MICROSECONDS -> functions.timestamp_micros(source);
            };
        }
        Column micros = switch (unit) {
            case SECONDS -> functions.try_multiply(source, functions.lit(SECONDS_TO_MICROS));
            case MILLISECONDS -> functions.try_multiply(source, functions.lit(MILLISECONDS_TO_MICROS));
            case MICROSECONDS -> source;
        };
        return functions.timestamp_micros(micros);
    }

    private static Column temporalEpochLong(
            Column source,
            PlatformDataType sourceType,
            EpochTimestampUnit unit,
        CastFailureStrategy failureStrategy
    ) {
        Column timestamp = sourceType == PlatformDataType.DATE
                ? (failureStrategy == CastFailureStrategy.FAIL
                    ? source.cast(DataTypes.TimestampType)
                    : source.try_cast(DataTypes.TimestampType))
                : source;
        return switch (unit) {
            case SECONDS -> functions.unix_seconds(timestamp);
            case MILLISECONDS -> functions.unix_millis(timestamp);
            case MICROSECONDS -> functions.unix_micros(timestamp);
        };
    }

    private static void validateStringTemporalParseOptions(
            ColumnTypeCast cast,
            CanvasColumnSchema sourceColumn,
            String path,
            CanvasNodeIssueSink issues
    ) {
        StringTemporalParseOptions options = cast.stringTemporalParseOptions();
        if (options == null) return;
        if (cast.epochTimestampUnit() != null || cast.temporalStringFormatOptions() != null) {
            issues.error(
                    "TYPE_CAST_PARSE_OPTIONS_MUTUALLY_EXCLUSIVE",
                    "字符串日期时间解析不能与其他特殊时间转换配置同时使用",
                    path
            );
        }
        PlatformTypeDefinition targetType = cast.targetType();
        if (targetType == null || (targetType.type() != PlatformDataType.DATE
                && targetType.type() != PlatformDataType.TIMESTAMP)) {
            issues.error(
                    "STRING_TEMPORAL_PARSE_TARGET_TYPE_UNSUPPORTED",
                    "字符串日期时间解析仅支持 STRING 转换为 DATE 或 TIMESTAMP",
                    path
            );
            return;
        }
        if (sourceColumn != null && sourceColumn.fieldType() != PlatformDataType.STRING) {
            issues.error(
                    "STRING_TEMPORAL_PARSE_SOURCE_TYPE_UNSUPPORTED",
                    "字符串日期时间解析仅支持 STRING 字段",
                    path
            );
        }
        if (CanvasNodeSupport.blank(options.pattern())) {
            issues.error(
                    "STRING_TEMPORAL_PARSE_PATTERN_REQUIRED",
                    "请输入日期时间格式",
                    path + ".pattern"
            );
        } else if (options.pattern().length() > 128) {
            issues.error(
                    "STRING_TEMPORAL_PARSE_PATTERN_TOO_LONG",
                    "日期时间格式不能超过 128 个字符",
                    path + ".pattern"
            );
        }
        if (targetType.type() == PlatformDataType.DATE) {
            if (options.zoneMode() != null) {
                issues.error(
                        "STRING_TEMPORAL_PARSE_DATE_ZONE_UNSUPPORTED",
                        "DATE 解析不能配置时区处理方式",
                        path + ".zoneMode"
                );
            }
            if (!CanvasNodeSupport.blank(options.sourceTimeZone())) {
                issues.error(
                        "STRING_TEMPORAL_PARSE_DATE_ZONE_UNSUPPORTED",
                        "DATE 解析不能配置来源时区",
                        path + ".sourceTimeZone"
                );
            }
            return;
        }
        if (options.zoneMode() == null) {
            issues.error(
                    "STRING_TEMPORAL_PARSE_ZONE_MODE_REQUIRED",
                    "请选择时间戳时区处理方式",
                    path + ".zoneMode"
            );
            return;
        }
        boolean patternContainsZone = containsUnquotedZonePatternSymbol(options.pattern());
        if (options.zoneMode() == StringTimestampZoneMode.SOURCE_TIME_ZONE) {
            if (CanvasNodeSupport.blank(options.sourceTimeZone())) {
                issues.error(
                        "STRING_TEMPORAL_PARSE_SOURCE_ZONE_REQUIRED",
                        "请选择来源时区",
                        path + ".sourceTimeZone"
                );
            } else if (options.sourceTimeZone().length() > 64) {
                issues.error(
                        "STRING_TEMPORAL_PARSE_SOURCE_ZONE_TOO_LONG",
                        "来源时区不能超过 64 个字符",
                        path + ".sourceTimeZone"
                );
            } else {
                try {
                    ZoneId.of(options.sourceTimeZone());
                } catch (DateTimeException exception) {
                    issues.error(
                            "STRING_TEMPORAL_PARSE_SOURCE_ZONE_INVALID",
                            "来源时区必须是有效的 IANA Zone ID",
                            path + ".sourceTimeZone"
                    );
                }
            }
            if (patternContainsZone) {
                issues.error(
                        "STRING_TEMPORAL_PARSE_ZONE_PATTERN_CONFLICT",
                        "指定来源时区时，格式不能包含时区或偏移符号",
                        path + ".pattern"
                );
            }
            return;
        }
        if (!CanvasNodeSupport.blank(options.sourceTimeZone())) {
            issues.error(
                    "STRING_TEMPORAL_PARSE_SOURCE_ZONE_UNSUPPORTED",
                    "字符串自带偏移时不能配置来源时区",
                    path + ".sourceTimeZone"
            );
        }
        if (!patternContainsZone) {
            issues.error(
                    "STRING_TEMPORAL_PARSE_EMBEDDED_OFFSET_PATTERN_REQUIRED",
                    "字符串自带偏移时，格式必须包含时区或偏移符号",
                    path + ".pattern"
            );
        }
    }

    private static boolean containsUnquotedZonePatternSymbol(String pattern) {
        if (pattern == null) return false;
        boolean quoted = false;
        for (int index = 0; index < pattern.length(); index++) {
            char symbol = pattern.charAt(index);
            if (symbol == '\'') {
                if (quoted && index + 1 < pattern.length() && pattern.charAt(index + 1) == '\'') {
                    index++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (!quoted && "XxZOVz".indexOf(symbol) >= 0) return true;
        }
        return false;
    }

    private static Column stringTemporal(
            Column source,
            PlatformDataType targetType,
            StringTemporalParseOptions options,
            CastFailureStrategy failureStrategy
    ) {
        if (targetType == PlatformDataType.DATE) {
            return failureStrategy == CastFailureStrategy.FAIL
                    ? functions.to_date(source, options.pattern())
                    : functions.try_to_date(source, options.pattern());
        }
        Column parsed = failureStrategy == CastFailureStrategy.FAIL
                ? functions.to_timestamp(source, options.pattern())
                : functions.try_to_timestamp(source, functions.lit(options.pattern()));
        return options.zoneMode() == StringTimestampZoneMode.SOURCE_TIME_ZONE
                ? functions.to_utc_timestamp(parsed, options.sourceTimeZone())
                : parsed;
    }

    private static void validateTemporalStringFormatOptions(
            ColumnTypeCast cast,
            CanvasColumnSchema sourceColumn,
            String path,
            CanvasNodeIssueSink issues
    ) {
        TemporalStringFormatOptions options = cast.temporalStringFormatOptions();
        if (options == null) return;
        if (cast.epochTimestampUnit() != null || cast.stringTemporalParseOptions() != null) {
            issues.error(
                    "TYPE_CAST_FORMAT_OPTIONS_MUTUALLY_EXCLUSIVE",
                    "时间类型字符串格式化不能与其他特殊时间转换配置同时使用",
                    path
            );
        }
        PlatformTypeDefinition targetType = cast.targetType();
        if (targetType == null || targetType.type() != PlatformDataType.STRING) {
            issues.error(
                    "TEMPORAL_STRING_FORMAT_TARGET_TYPE_UNSUPPORTED",
                    "时间类型字符串格式化仅支持转换为 STRING",
                    path
            );
        }
        PlatformDataType sourceType = sourceColumn == null ? null : sourceColumn.fieldType();
        if (sourceType != null
                && sourceType != PlatformDataType.DATE
                && sourceType != PlatformDataType.TIMESTAMP
                && sourceType != PlatformDataType.TIMESTAMP_NTZ) {
            issues.error(
                    "TEMPORAL_STRING_FORMAT_SOURCE_TYPE_UNSUPPORTED",
                    "字符串格式化仅支持 DATE、TIMESTAMP 或 TIMESTAMP_NTZ 字段",
                    path
            );
        }
        if (CanvasNodeSupport.blank(options.pattern())) {
            issues.error(
                    "TEMPORAL_STRING_FORMAT_PATTERN_REQUIRED",
                    "请输入输出日期时间格式",
                    path + ".pattern"
            );
        } else if (options.pattern().length() > 128) {
            issues.error(
                    "TEMPORAL_STRING_FORMAT_PATTERN_TOO_LONG",
                    "输出日期时间格式不能超过 128 个字符",
                    path + ".pattern"
            );
        } else {
            try {
                DateTimeFormatter.ofPattern(options.pattern());
            } catch (IllegalArgumentException exception) {
                issues.error(
                        "TEMPORAL_STRING_FORMAT_PATTERN_INVALID",
                        "输出日期时间格式不是有效的日期时间 pattern",
                        path + ".pattern"
                );
            }
            if (containsUnquotedZonePatternSymbol(options.pattern())) {
                issues.error(
                        "TEMPORAL_STRING_FORMAT_ZONE_PATTERN_UNSUPPORTED",
                        "输出格式不能包含时区或偏移符号，请使用目标时区单独控制显示时间",
                        path + ".pattern"
                );
            }
        }
        if (sourceType == PlatformDataType.TIMESTAMP) {
            if (CanvasNodeSupport.blank(options.targetTimeZone())) {
                issues.error(
                        "TEMPORAL_STRING_FORMAT_TARGET_ZONE_REQUIRED",
                        "TIMESTAMP 格式化必须选择目标时区",
                        path + ".targetTimeZone"
                );
            } else if (options.targetTimeZone().length() > 64) {
                issues.error(
                        "TEMPORAL_STRING_FORMAT_TARGET_ZONE_TOO_LONG",
                        "目标时区不能超过 64 个字符",
                        path + ".targetTimeZone"
                );
            } else {
                try {
                    ZoneId.of(options.targetTimeZone());
                } catch (DateTimeException exception) {
                    issues.error(
                            "TEMPORAL_STRING_FORMAT_TARGET_ZONE_INVALID",
                            "目标时区必须是有效的 IANA Zone ID",
                            path + ".targetTimeZone"
                    );
                }
            }
        } else if (sourceType == PlatformDataType.DATE || sourceType == PlatformDataType.TIMESTAMP_NTZ) {
            if (options.targetTimeZone() != null) {
                issues.error(
                        "TEMPORAL_STRING_FORMAT_TARGET_ZONE_UNSUPPORTED",
                        sourceType + " 格式化不能配置目标时区",
                        path + ".targetTimeZone"
                );
            }
        }
    }

    private static Column temporalString(
            Column source,
            PlatformDataType sourceType,
            TemporalStringFormatOptions options
    ) {
        Column displayValue = sourceType == PlatformDataType.TIMESTAMP
                ? functions.from_utc_timestamp(source, options.targetTimeZone())
                : source;
        return functions.date_format(displayValue, options.pattern());
    }

    private static CanvasColumnSchema castColumn(
            CanvasColumnSchema analyzed,
            ColumnTypeCast cast
    ) {
        PlatformTypeDefinition target = cast.targetType();
        return new CanvasColumnSchema(
                analyzed.name(),
                target.type(),
                target.type() == PlatformDataType.STRING ? target.length() : null,
                target.type() == PlatformDataType.DECIMAL ? target.precision() : null,
                target.type() == PlatformDataType.DECIMAL ? target.scale() : null,
                cast.failureStrategy() == CastFailureStrategy.SET_NULL
                        || analyzed.nullable(),
                null,
                false,
                false,
                null
        );
    }
}
