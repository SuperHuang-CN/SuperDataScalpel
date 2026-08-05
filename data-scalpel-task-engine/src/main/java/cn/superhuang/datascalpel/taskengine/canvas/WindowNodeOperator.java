package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasLiteral;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasWindowLimits;
import cn.superhuang.data.scalpel.contract.task.RowsFrameBoundary;
import cn.superhuang.data.scalpel.contract.task.RowsWindowFrame;
import cn.superhuang.data.scalpel.contract.task.SortField;
import cn.superhuang.data.scalpel.contract.task.WindowConfiguration;
import cn.superhuang.data.scalpel.contract.task.WindowFrameType;
import cn.superhuang.data.scalpel.contract.task.WindowFunctionItem;
import cn.superhuang.data.scalpel.contract.task.WindowNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WindowNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.WINDOW;
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
        if (!(definition instanceof WindowNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "WINDOW operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        WindowConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);

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
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        if (source != null && source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error(
                    "WINDOW_REQUIRES_BOUNDED_INPUT",
                    "窗口计算只支持有界输入",
                    "configuration.sourceTableName"
            );
        }
        validatePartitionColumns(
                configuration.partitionByColumns(),
                source,
                "DUPLICATE_WINDOW_PARTITION_COLUMN",
                issues,
                "configuration.partitionByColumns"
        );
        CanvasSortSupport.validate(
                configuration.orderBy(),
                source,
                issues,
                "configuration.orderBy",
                "EMPTY_WINDOW_ORDER",
                "DUPLICATE_WINDOW_SORT_COLUMN"
        );
        if (configuration.functions() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "窗口函数必须是数组",
                    "configuration.functions"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.functions().isEmpty()) {
            issues.error(
                    "EMPTY_WINDOW_FUNCTIONS",
                    "至少配置一个窗口函数",
                    "configuration.functions"
            );
        }
        if (configuration.functions().size() > CanvasWindowLimits.MAX_FUNCTIONS) {
            issues.error(
                    "WINDOW_FUNCTION_LIMIT_EXCEEDED",
                    "窗口函数不能超过 " + CanvasWindowLimits.MAX_FUNCTIONS + " 项",
                    "configuration.functions"
            );
        }

        Map<String, CanvasColumnSchema> columns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        Set<String> outputNames = new HashSet<>();
        for (int index = 0; index < configuration.functions().size(); index++) {
            WindowFunctionItem item = configuration.functions().get(index);
            String path = "configuration.functions[" + index + "]";
            if (item == null) {
                issues.error("REQUIRED_CONFIGURATION", "窗口函数不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(
                    item.outputColumnName(),
                    "请输入窗口输出字段名",
                    path + ".outputColumnName",
                    issues
            );
            if (!CanvasNodeSupport.blank(item.outputColumnName())
                    && !outputNames.add(item.outputColumnName())) {
                issues.error(
                        "DUPLICATE_WINDOW_OUTPUT_COLUMN",
                        "窗口输出字段名重复：" + item.outputColumnName(),
                        path + ".outputColumnName"
                );
            }
            if (!CanvasNodeSupport.blank(item.outputColumnName())
                    && columns.containsKey(item.outputColumnName())) {
                issues.error(
                        "WINDOW_OUTPUT_COLUMN_CONFLICT",
                        "窗口输出字段与来源字段同名：" + item.outputColumnName(),
                        path + ".outputColumnName"
                );
            }
            validateFunction(item, source, columns, issues, path);
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Column[] partitions = configuration.partitionByColumns().stream()
                .map(name -> sourceDataset.col(CanvasNodeSupport.quoteIdentifier(name)))
                .toArray(Column[]::new);
        Column[] order = CanvasSortSupport.expressions(sourceDataset, configuration.orderBy());
        WindowSpec base = partitions.length == 0
                ? Window.orderBy(order)
                : Window.partitionBy(partitions).orderBy(order);
        List<Column> projection = new ArrayList<>(
                source.schema().columns().size() + configuration.functions().size()
        );
        source.schema().columns().forEach(column -> projection.add(
                sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name()))
        ));
        for (WindowFunctionItem item : configuration.functions()) {
            projection.add(windowExpression(sourceDataset, base, item)
                    .alias(item.outputColumnName()));
        }
        Dataset<Row> result = sourceDataset.select(projection.toArray(Column[]::new));
        List<CanvasColumnSchema> analyzed = SparkTypeMapper.fromStructType(
                result.schema(),
                source.schema().columns()
        );
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(analyzed.size());
        outputColumns.addAll(source.schema().columns());
        for (int index = source.schema().columns().size(); index < analyzed.size(); index++) {
            CanvasColumnSchema column = analyzed.get(index);
            outputColumns.add(new CanvasColumnSchema(
                    column.name(),
                    column.fieldType(),
                    column.length(),
                    column.precision(),
                    column.scale(),
                    column.nullable(),
                    null,
                    false,
                    false,
                    null
            ));
        }
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                source.schema().origin(),
                outputColumns,
                CanvasDatasetKind.BOUNDED,
                null,
                null
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    static void validatePartitionColumns(
            List<String> partitionBy,
            SparkCanvasTable source,
            String duplicateCode,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (partitionBy == null) {
            issues.error("REQUIRED_CONFIGURATION", "分区字段必须是数组", path);
            return;
        }
        Map<String, CanvasColumnSchema> columns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        Set<String> names = new HashSet<>();
        for (int index = 0; index < partitionBy.size(); index++) {
            String name = partitionBy.get(index);
            String itemPath = path + "[" + index + "]";
            CanvasNodeSupport.required(name, "请选择分区字段", itemPath, issues);
            if (!CanvasNodeSupport.blank(name) && !names.add(name)) {
                issues.error(duplicateCode, "分区字段重复：" + name, itemPath);
            }
            if (source != null
                    && !CanvasNodeSupport.blank(name)
                    && !columns.containsKey(name)) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "分区字段不存在：" + name,
                        itemPath
                );
            } else if (columns.get(name) != null
                    && columns.get(name).fieldType() == PlatformDataType.GEOMETRY) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "Geometry 字段不能作为窗口分区字段",
                        itemPath
                );
            }
        }
    }

    private static void validateFunction(
            WindowFunctionItem item,
            SparkCanvasTable source,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues,
            String path
    ) {
        switch (item) {
            case WindowFunctionItem.RowNumber ignored -> {
            }
            case WindowFunctionItem.Rank ignored -> {
            }
            case WindowFunctionItem.DenseRank ignored -> {
            }
            case WindowFunctionItem.Lag lag -> validateOffsetFunction(
                    lag.sourceColumnName(), lag.offset(), lag.defaultValue(),
                    source, columns, issues, path
            );
            case WindowFunctionItem.Lead lead -> validateOffsetFunction(
                    lead.sourceColumnName(), lead.offset(), lead.defaultValue(),
                    source, columns, issues, path
            );
            case WindowFunctionItem.Count count -> {
                if (count.sourceColumnName() != null
                        && CanvasNodeSupport.blank(count.sourceColumnName())) {
                    issues.error(
                            "INVALID_WINDOW_COUNT_STAR",
                            "COUNT(*) 必须使用 null 表示来源字段",
                            path + ".sourceColumnName"
                    );
                }
                if (count.sourceColumnName() != null) {
                    validateSourceColumn(
                            count.sourceColumnName(), source, columns, issues,
                            path + ".sourceColumnName"
                    );
                }
                validateFrame(count.frame(), issues, path + ".frame");
            }
            case WindowFunctionItem.Sum sum -> {
                validateSourceColumn(
                        sum.sourceColumnName(), source, columns, issues,
                        path + ".sourceColumnName"
                );
                validateFrame(sum.frame(), issues, path + ".frame");
            }
            case WindowFunctionItem.Avg avg -> {
                validateSourceColumn(
                        avg.sourceColumnName(), source, columns, issues,
                        path + ".sourceColumnName"
                );
                validateFrame(avg.frame(), issues, path + ".frame");
            }
            case WindowFunctionItem.Min min -> {
                validateSourceColumn(
                        min.sourceColumnName(), source, columns, issues,
                        path + ".sourceColumnName"
                );
                validateFrame(min.frame(), issues, path + ".frame");
            }
            case WindowFunctionItem.Max max -> {
                validateSourceColumn(
                        max.sourceColumnName(), source, columns, issues,
                        path + ".sourceColumnName"
                );
                validateFrame(max.frame(), issues, path + ".frame");
            }
            case WindowFunctionItem.FirstValue first -> {
                validateSourceColumn(
                        first.sourceColumnName(), source, columns, issues,
                        path + ".sourceColumnName"
                );
                validateFrame(first.frame(), issues, path + ".frame");
            }
            case WindowFunctionItem.LastValue last -> {
                validateSourceColumn(
                        last.sourceColumnName(), source, columns, issues,
                        path + ".sourceColumnName"
                );
                validateFrame(last.frame(), issues, path + ".frame");
            }
        }
    }

    private static void validateOffsetFunction(
            String sourceColumnName,
            int offset,
            CanvasLiteral defaultValue,
            SparkCanvasTable source,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues,
            String path
    ) {
        validateSourceColumn(
                sourceColumnName, source, columns, issues, path + ".sourceColumnName"
        );
        if (offset < CanvasWindowLimits.MIN_OFFSET
                || offset > CanvasWindowLimits.MAX_OFFSET) {
            issues.error(
                    "INVALID_WINDOW_OFFSET",
                    "LAG/LEAD offset 必须在 "
                            + CanvasWindowLimits.MIN_OFFSET + ".."
                            + CanvasWindowLimits.MAX_OFFSET,
                    path + ".offset"
            );
        }
        CanvasColumnSchema column = CanvasNodeSupport.blank(sourceColumnName)
                ? null : columns.get(sourceColumnName);
        if (defaultValue == null) return;
        if (!CanvasPredicateExpressionBuilder.validLiteral(defaultValue)) {
            issues.error(
                    "INVALID_WINDOW_DEFAULT_LITERAL",
                    "窗口默认值格式无效",
                    path + ".defaultValue"
            );
        } else if (column != null && defaultValue.dataType() != column.fieldType()) {
            issues.error(
                    "WINDOW_DEFAULT_LITERAL_TYPE_MISMATCH",
                    "窗口默认值类型必须与来源字段一致",
                    path + ".defaultValue.dataType"
            );
        }
        if (column != null && column.fieldType() == PlatformDataType.GEOMETRY) {
            issues.error(
                    "WINDOW_DEFAULT_LITERAL_TYPE_NOT_SUPPORTED",
                    "GEOMETRY 不支持非 NULL 窗口默认值",
                    path + ".defaultValue"
            );
        }
    }

    private static void validateSourceColumn(
            String name,
            SparkCanvasTable source,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (CanvasNodeSupport.blank(name)) {
            issues.error(
                    "WINDOW_SOURCE_COLUMN_REQUIRED",
                    "请选择窗口函数来源字段",
                    path
            );
        } else if (source != null && !columns.containsKey(name)) {
            issues.error("COLUMN_NOT_FOUND", "窗口来源字段不存在：" + name, path);
        } else if (columns.get(name) != null
                && columns.get(name).fieldType() == PlatformDataType.GEOMETRY) {
            issues.error(
                    "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    "窗口函数不能使用 Geometry 来源字段",
                    path
            );
        }
    }

    private static void validateFrame(
            RowsWindowFrame frame,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (frame == null || frame.type() != WindowFrameType.ROWS
                || frame.start() == null || frame.end() == null) {
            issues.error("INVALID_WINDOW_FRAME", "请配置完整的 ROWS Frame", path);
            return;
        }
        boolean validStart = !(frame.start() instanceof RowsFrameBoundary.UnboundedFollowing);
        boolean validEnd = !(frame.end() instanceof RowsFrameBoundary.UnboundedPreceding);
        if (!validStart || !validEnd) {
            issues.error("INVALID_WINDOW_FRAME", "窗口 Frame 起止边界无效", path);
        }
        validateFrameBoundary(frame.start(), issues, path + ".start");
        validateFrameBoundary(frame.end(), issues, path + ".end");
        if (validStart && validEnd
                && framePosition(frame.start()) > framePosition(frame.end())) {
            issues.error("INVALID_WINDOW_FRAME", "窗口 Frame 起点不能晚于终点", path);
        }
    }

    private static void validateFrameBoundary(
            RowsFrameBoundary boundary,
            CanvasNodeIssueSink issues,
            String path
    ) {
        Long offset = switch (boundary) {
            case RowsFrameBoundary.Preceding preceding -> preceding.offset();
            case RowsFrameBoundary.Following following -> following.offset();
            default -> null;
        };
        if (offset != null
                && (offset < CanvasWindowLimits.MIN_FRAME_OFFSET
                || offset > CanvasWindowLimits.MAX_FRAME_OFFSET)) {
            issues.error(
                    "INVALID_WINDOW_FRAME_OFFSET",
                    "Frame offset 必须在 "
                            + CanvasWindowLimits.MIN_FRAME_OFFSET + ".."
                            + CanvasWindowLimits.MAX_FRAME_OFFSET,
                    path + ".offset"
            );
        }
    }

    private static long framePosition(RowsFrameBoundary boundary) {
        return switch (boundary) {
            case RowsFrameBoundary.UnboundedPreceding ignored -> Long.MIN_VALUE;
            case RowsFrameBoundary.Preceding preceding -> -preceding.offset();
            case RowsFrameBoundary.CurrentRow ignored -> 0L;
            case RowsFrameBoundary.Following following -> following.offset();
            case RowsFrameBoundary.UnboundedFollowing ignored -> Long.MAX_VALUE;
        };
    }

    private static Column windowExpression(
            Dataset<Row> dataset,
            WindowSpec base,
            WindowFunctionItem item
    ) {
        return switch (item) {
            case WindowFunctionItem.RowNumber ignored -> functions.row_number().over(base);
            case WindowFunctionItem.Rank ignored -> functions.rank().over(base);
            case WindowFunctionItem.DenseRank ignored -> functions.dense_rank().over(base);
            case WindowFunctionItem.Lag lag -> {
                Column source = dataset.col(
                        CanvasNodeSupport.quoteIdentifier(lag.sourceColumnName()));
                yield (lag.defaultValue() == null
                        ? functions.lag(source, lag.offset())
                        : functions.lag(
                                source,
                                lag.offset(),
                                CanvasPredicateExpressionBuilder.literalValue(lag.defaultValue())
                        )).over(base);
            }
            case WindowFunctionItem.Lead lead -> {
                Column source = dataset.col(
                        CanvasNodeSupport.quoteIdentifier(lead.sourceColumnName()));
                yield (lead.defaultValue() == null
                        ? functions.lead(source, lead.offset())
                        : functions.lead(
                                source,
                                lead.offset(),
                                CanvasPredicateExpressionBuilder.literalValue(lead.defaultValue())
                        )).over(base);
            }
            case WindowFunctionItem.Count count -> {
                Column aggregate = count.sourceColumnName() == null
                        ? functions.count(functions.lit(1))
                        : functions.count(dataset.col(
                                CanvasNodeSupport.quoteIdentifier(count.sourceColumnName())));
                yield aggregate.over(applyFrame(base, count.frame()));
            }
            case WindowFunctionItem.Sum sum -> functions.sum(dataset.col(
                    CanvasNodeSupport.quoteIdentifier(sum.sourceColumnName())))
                    .over(applyFrame(base, sum.frame()));
            case WindowFunctionItem.Avg avg -> functions.avg(dataset.col(
                    CanvasNodeSupport.quoteIdentifier(avg.sourceColumnName())))
                    .over(applyFrame(base, avg.frame()));
            case WindowFunctionItem.Min min -> functions.min(dataset.col(
                    CanvasNodeSupport.quoteIdentifier(min.sourceColumnName())))
                    .over(applyFrame(base, min.frame()));
            case WindowFunctionItem.Max max -> functions.max(dataset.col(
                    CanvasNodeSupport.quoteIdentifier(max.sourceColumnName())))
                    .over(applyFrame(base, max.frame()));
            case WindowFunctionItem.FirstValue first -> functions.first_value(
                            dataset.col(CanvasNodeSupport.quoteIdentifier(first.sourceColumnName())),
                            functions.lit(first.ignoreNulls()))
                    .over(applyFrame(base, first.frame()));
            case WindowFunctionItem.LastValue last -> functions.last_value(
                            dataset.col(CanvasNodeSupport.quoteIdentifier(last.sourceColumnName())),
                            functions.lit(last.ignoreNulls()))
                    .over(applyFrame(base, last.frame()));
        };
    }

    private static WindowSpec applyFrame(WindowSpec base, RowsWindowFrame frame) {
        return base.rowsBetween(
                sparkBoundary(frame.start()),
                sparkBoundary(frame.end())
        );
    }

    private static long sparkBoundary(RowsFrameBoundary boundary) {
        return switch (boundary) {
            case RowsFrameBoundary.UnboundedPreceding ignored -> Window.unboundedPreceding();
            case RowsFrameBoundary.Preceding preceding -> -preceding.offset();
            case RowsFrameBoundary.CurrentRow ignored -> Window.currentRow();
            case RowsFrameBoundary.Following following -> following.offset();
            case RowsFrameBoundary.UnboundedFollowing ignored -> Window.unboundedFollowing();
        };
    }
}
