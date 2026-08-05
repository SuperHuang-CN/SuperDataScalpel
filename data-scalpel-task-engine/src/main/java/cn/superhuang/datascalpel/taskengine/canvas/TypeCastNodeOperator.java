package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CastFailureStrategy;
import cn.superhuang.data.scalpel.contract.task.ColumnTypeCast;
import cn.superhuang.data.scalpel.contract.task.TypeCastConfiguration;
import cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.types.DataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TypeCastNodeOperator implements CanvasNodeOperator {

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
            Column converted = cast.failureStrategy() == CastFailureStrategy.FAIL
                    ? sourceExpression.cast(targetDataType)
                    : sourceExpression.try_cast(targetDataType);
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
