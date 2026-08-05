package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.AggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.AggregateFunction;
import cn.superhuang.data.scalpel.contract.task.AggregateItem;
import cn.superhuang.data.scalpel.contract.task.AggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RelationalGroupedDataset;
import org.apache.spark.sql.functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AggregateNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.AGGREGATE;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof AggregateNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "AGGREGATE operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        AggregateConfiguration configuration = node.configuration();
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
        if (configuration.groupByColumns() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "分组字段必须是数组",
                    "configuration.groupByColumns"
            );
        }
        if (configuration.aggregations() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "聚合项必须是数组",
                    "configuration.aggregations"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.aggregations().isEmpty()) {
            issues.error(
                    "EMPTY_AGGREGATIONS",
                    "至少配置一个聚合项",
                    "configuration.aggregations"
            );
        }

        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of()
                : CanvasNodeSupport.columns(source.schema());
        List<String> groupByColumns = configuration.groupByColumns() == null
                ? List.of()
                : configuration.groupByColumns();
        Set<String> groupByNames = new HashSet<>();
        for (int index = 0; index < groupByColumns.size(); index++) {
            String columnName = groupByColumns.get(index);
            String path = "configuration.groupByColumns[" + index + "]";
            CanvasNodeSupport.required(columnName, "请选择分组字段", path, issues);
            if (!CanvasNodeSupport.blank(columnName) && !groupByNames.add(columnName)) {
                issues.error(
                        "DUPLICATE_GROUP_BY_COLUMN",
                        "分组字段重复：" + columnName,
                        path
                );
            }
            if (source != null
                    && !CanvasNodeSupport.blank(columnName)
                    && !sourceColumns.containsKey(columnName)) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "分组字段不存在：" + columnName,
                        path
                );
            } else if (sourceColumns.get(columnName) != null
                    && sourceColumns.get(columnName).fieldType()
                    == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "Geometry 字段不能作为分组字段",
                        path
                );
            }
        }

        Set<String> outputNames = new HashSet<>();
        for (int index = 0; index < configuration.aggregations().size(); index++) {
            AggregateItem item = configuration.aggregations().get(index);
            String path = "configuration.aggregations[" + index + "]";
            if (item == null) {
                issues.error("REQUIRED_CONFIGURATION", "聚合项不能为空", path);
                continue;
            }
            if (item.function() == null) {
                issues.error(
                        "INVALID_AGGREGATE_FUNCTION",
                        "请选择聚合函数",
                        path + ".function"
                );
            }
            CanvasNodeSupport.required(
                    item.outputColumnName(),
                    "请输入聚合输出字段名",
                    path + ".outputColumnName",
                    issues
            );
            if (!CanvasNodeSupport.blank(item.outputColumnName())
                    && !outputNames.add(item.outputColumnName())) {
                issues.error(
                        "DUPLICATE_AGGREGATE_OUTPUT_COLUMN",
                        "聚合输出字段名重复：" + item.outputColumnName(),
                        path + ".outputColumnName"
                );
            }
            if (!CanvasNodeSupport.blank(item.outputColumnName())
                    && groupByNames.contains(item.outputColumnName())) {
                issues.error(
                        "AGGREGATE_OUTPUT_COLUMN_CONFLICT",
                        "聚合输出字段与分组字段同名：" + item.outputColumnName(),
                        path + ".outputColumnName"
                );
            }
            validateAggregateItem(item, path, source, sourceColumns, issues);
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Column[] groupExpressions = groupByColumns.stream()
                .map(columnName -> sourceDataset.col(CanvasNodeSupport.quoteIdentifier(columnName)))
                .toArray(Column[]::new);
        Column[] aggregateExpressions = configuration.aggregations().stream()
                .map(item -> aggregateExpression(sourceDataset, item)
                        .alias(item.outputColumnName()))
                .toArray(Column[]::new);
        Dataset<Row> aggregateDataset;
        if (groupExpressions.length == 0) {
            aggregateDataset = sourceDataset.agg(
                    aggregateExpressions[0],
                    trailing(aggregateExpressions)
            );
        } else {
            RelationalGroupedDataset grouped = sourceDataset.groupBy(groupExpressions);
            aggregateDataset = grouped.agg(
                    aggregateExpressions[0],
                    trailing(aggregateExpressions)
            );
        }

        List<CanvasColumnSchema> analyzedColumns = SparkTypeMapper.fromStructType(
                aggregateDataset.schema(),
                List.of()
        );
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(analyzedColumns.size());
        for (int index = 0; index < analyzedColumns.size(); index++) {
            CanvasColumnSchema analyzed = analyzedColumns.get(index);
            if (index < groupByColumns.size()) {
                CanvasColumnSchema prior = sourceColumns.get(groupByColumns.get(index));
                outputColumns.add(groupByColumn(analyzed, prior));
            } else {
                outputColumns.add(aggregateColumn(analyzed));
            }
        }
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                null,
                outputColumns,
                CanvasDatasetKind.BOUNDED,
                null,
                null
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(
                outputSchema.name(),
                new SparkCanvasTable(outputSchema, aggregateDataset)
        );
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateAggregateItem(
            AggregateItem item,
            String path,
            SparkCanvasTable source,
            Map<String, CanvasColumnSchema> sourceColumns,
            CanvasNodeIssueSink issues
    ) {
        if (item.function() == null) return;
        if (item.sourceColumnName() == null) {
            if (item.function() != AggregateFunction.COUNT || item.distinct()) {
                String code = item.function() == AggregateFunction.COUNT
                        ? "INVALID_COUNT_STAR_CONFIGURATION"
                        : "AGGREGATE_SOURCE_COLUMN_REQUIRED";
                String message = item.function() == AggregateFunction.COUNT
                        ? "COUNT(*) 不支持 DISTINCT"
                        : item.function() + " 必须选择来源字段";
                issues.error(code, message, path + ".sourceColumnName");
            }
            return;
        }
        if (CanvasNodeSupport.blank(item.sourceColumnName())) {
            issues.error(
                    "AGGREGATE_SOURCE_COLUMN_REQUIRED",
                    "请选择聚合来源字段",
                    path + ".sourceColumnName"
            );
        } else if (source != null && !sourceColumns.containsKey(item.sourceColumnName())) {
            issues.error(
                    "COLUMN_NOT_FOUND",
                    "聚合来源字段不存在：" + item.sourceColumnName(),
                    path + ".sourceColumnName"
            );
        } else if (sourceColumns.get(item.sourceColumnName()) != null
                && sourceColumns.get(item.sourceColumnName()).fieldType()
                == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY) {
            issues.error(
                    "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    "Geometry 字段不能参与聚合",
                    path + ".sourceColumnName"
            );
        }
        if (item.distinct()
                && (item.function() == AggregateFunction.MIN
                || item.function() == AggregateFunction.MAX)) {
            issues.error(
                    "AGGREGATE_DISTINCT_NOT_SUPPORTED",
                    item.function() + " 不支持 DISTINCT",
                    path + ".distinct"
            );
        }
    }

    private static Column aggregateExpression(Dataset<Row> source, AggregateItem item) {
        if (item.function() == AggregateFunction.COUNT && item.sourceColumnName() == null) {
            return functions.count(functions.lit(1));
        }
        Column column = source.col(CanvasNodeSupport.quoteIdentifier(item.sourceColumnName()));
        return switch (item.function()) {
            case COUNT -> item.distinct()
                    ? functions.count_distinct(column)
                    : functions.count(column);
            case SUM -> item.distinct()
                    ? functions.sum_distinct(column)
                    : functions.sum(column);
            case AVG -> item.distinct()
                    ? functions.expr(
                            "avg(DISTINCT "
                                    + CanvasNodeSupport.quoteIdentifier(item.sourceColumnName())
                                    + ")"
                    )
                    : functions.avg(column);
            case MIN -> functions.min(column);
            case MAX -> functions.max(column);
        };
    }

    private static Column[] trailing(Column[] columns) {
        if (columns.length <= 1) return new Column[0];
        Column[] trailing = new Column[columns.length - 1];
        System.arraycopy(columns, 1, trailing, 0, trailing.length);
        return trailing;
    }

    private static CanvasColumnSchema groupByColumn(
            CanvasColumnSchema analyzed,
            CanvasColumnSchema source
    ) {
        return new CanvasColumnSchema(
                analyzed.name(),
                analyzed.fieldType(),
                analyzed.length(),
                analyzed.precision(),
                analyzed.scale(),
                analyzed.nullable(),
                null,
                false,
                false,
                source == null ? null : source.comment()
        );
    }

    private static CanvasColumnSchema aggregateColumn(CanvasColumnSchema analyzed) {
        return new CanvasColumnSchema(
                analyzed.name(),
                analyzed.fieldType(),
                analyzed.length(),
                analyzed.precision(),
                analyzed.scale(),
                analyzed.nullable(),
                null,
                false,
                false,
                null
        );
    }
}
