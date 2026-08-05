package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.DeduplicateConfiguration;
import cn.superhuang.data.scalpel.contract.task.DeduplicateKeepStrategy;
import cn.superhuang.data.scalpel.contract.task.DeduplicateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.NullOrdering;
import cn.superhuang.data.scalpel.contract.task.SortDirection;
import cn.superhuang.data.scalpel.contract.task.SortField;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
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

public final class DeduplicateNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.DEDUPLICATE;
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
        if (!(definition instanceof DeduplicateNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "DEDUPLICATE operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        DeduplicateConfiguration configuration = node.configuration();
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
        if (configuration.keepStrategy() == null) {
            issues.error(
                    "INVALID_DEDUPLICATE_KEEP_STRATEGY",
                    "请选择保留策略",
                    "configuration.keepStrategy"
            );
        }
        if (configuration.keyColumns() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "去重键必须是数组",
                    "configuration.keyColumns"
            );
        }
        if (configuration.orderBy() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "排序规则必须是数组",
                    "configuration.orderBy"
            );
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of()
                : CanvasNodeSupport.columns(source.schema());
        List<String> keyColumns = configuration.keyColumns() == null
                ? List.of()
                : configuration.keyColumns();
        Set<String> keyNames = new HashSet<>();
        for (int index = 0; index < keyColumns.size(); index++) {
            String columnName = keyColumns.get(index);
            String path = "configuration.keyColumns[" + index + "]";
            CanvasNodeSupport.required(columnName, "请选择去重键字段", path, issues);
            if (!CanvasNodeSupport.blank(columnName) && !keyNames.add(columnName)) {
                issues.error(
                        "DUPLICATE_DEDUPLICATE_KEY_COLUMN",
                        "去重键字段重复：" + columnName,
                        path
                );
            }
            if (source != null
                    && !CanvasNodeSupport.blank(columnName)
                    && !sourceColumns.containsKey(columnName)) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "去重键字段不存在：" + columnName,
                        path
                );
            } else if (sourceColumns.get(columnName) != null
                    && sourceColumns.get(columnName).fieldType()
                    == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "Geometry 字段不能作为去重键",
                        path
                );
            }
        }

        Set<String> sortNames = new HashSet<>();
        for (int index = 0; index < configuration.orderBy().size(); index++) {
            SortField sortField = configuration.orderBy().get(index);
            String path = "configuration.orderBy[" + index + "]";
            if (sortField == null) {
                issues.error("REQUIRED_CONFIGURATION", "排序项不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(
                    sortField.columnName(),
                    "请选择排序字段",
                    path + ".columnName",
                    issues
            );
            if (!CanvasNodeSupport.blank(sortField.columnName())
                    && !sortNames.add(sortField.columnName())) {
                issues.error(
                        "DUPLICATE_DEDUPLICATE_SORT_COLUMN",
                        "排序字段重复：" + sortField.columnName(),
                        path + ".columnName"
                );
            }
            if (source != null
                    && !CanvasNodeSupport.blank(sortField.columnName())
                    && !sourceColumns.containsKey(sortField.columnName())) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "排序字段不存在：" + sortField.columnName(),
                        path + ".columnName"
                );
            } else if (sourceColumns.get(sortField.columnName()) != null
                    && sourceColumns.get(sortField.columnName()).fieldType()
                    == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "Geometry 字段不能参与排序",
                        path + ".columnName"
                );
            }
            if (sortField.direction() == null) {
                issues.error(
                        "INVALID_SORT_DIRECTION",
                        "请选择排序方向",
                        path + ".direction"
                );
            }
            if (sortField.nullOrdering() == null) {
                issues.error(
                        "INVALID_NULL_ORDERING",
                        "请选择 NULL 排序位置",
                        path + ".nullOrdering"
                );
            }
        }
        validateStrategy(configuration, keyColumns, issues);
        if (keyColumns.isEmpty() && source != null
                && source.schema().columns().stream().anyMatch(column -> column.fieldType()
                == cn.superhuang.data.scalpel.contract.type.PlatformDataType.GEOMETRY)) {
            issues.error(
                    "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    "包含 Geometry 字段时不能按全行去重",
                    "configuration.keyColumns"
            );
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Dataset<Row> deduplicated;
        if (configuration.keepStrategy() == DeduplicateKeepStrategy.ANY) {
            deduplicated = keyColumns.isEmpty()
                    ? sourceDataset.dropDuplicates()
                    : sourceDataset.dropDuplicates(keyColumns.toArray(String[]::new));
        } else {
            Column[] partitionBy = keyColumns.stream()
                    .map(columnName -> sourceDataset.col(
                            CanvasNodeSupport.quoteIdentifier(columnName)))
                    .toArray(Column[]::new);
            Column[] orderBy = configuration.orderBy().stream()
                    .map(sortField -> sortExpression(
                            sourceDataset,
                            sortField,
                            configuration.keepStrategy()))
                    .toArray(Column[]::new);
            WindowSpec window = Window.partitionBy(partitionBy).orderBy(orderBy);
            String rowNumberColumn = temporaryColumnName(sourceColumns.keySet());
            Dataset<Row> ranked = sourceDataset.withColumn(
                    rowNumberColumn,
                    functions.row_number().over(window)
            );
            Column[] projection = source.schema().columns().stream()
                    .map(column -> ranked.col(CanvasNodeSupport.quoteIdentifier(column.name())))
                    .toArray(Column[]::new);
            deduplicated = ranked
                    .filter(ranked.col(CanvasNodeSupport.quoteIdentifier(rowNumberColumn)).equalTo(1))
                    .select(projection);
        }
        deduplicated.schema();

        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                source.schema().origin(),
                source.schema().columns(),
                CanvasDatasetKind.BOUNDED,
                null,
                null
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(
                outputSchema.name(),
                new SparkCanvasTable(outputSchema, deduplicated)
        );
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateStrategy(
            DeduplicateConfiguration configuration,
            List<String> keyColumns,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.keepStrategy() == null) return;
        if (configuration.keepStrategy() == DeduplicateKeepStrategy.ANY) {
            if (!configuration.orderBy().isEmpty()) {
                issues.error(
                        "DEDUPLICATE_ORDER_NOT_ALLOWED",
                        "任意保留策略不能配置排序规则",
                        "configuration.orderBy"
                );
            }
            return;
        }
        if (keyColumns.isEmpty()) {
            issues.error(
                    "DEDUPLICATE_KEYS_REQUIRED",
                    "保留第一条或最后一条时必须配置去重键",
                    "configuration.keyColumns"
            );
            issues.error(
                    "FULL_ROW_DEDUPLICATE_REQUIRES_ANY",
                    "按全部字段去重只支持任意保留策略",
                    "configuration.keepStrategy"
            );
        }
        if (configuration.orderBy().isEmpty()) {
            issues.error(
                    "DEDUPLICATE_ORDER_REQUIRED",
                    "保留第一条或最后一条时至少配置一个排序字段",
                    "configuration.orderBy"
            );
        }
    }

    private static Column sortExpression(
            Dataset<Row> source,
            SortField sortField,
            DeduplicateKeepStrategy keepStrategy
    ) {
        boolean reverse = keepStrategy == DeduplicateKeepStrategy.LAST;
        SortDirection direction = reverse
                ? reverse(sortField.direction())
                : sortField.direction();
        NullOrdering nullOrdering = reverse
                ? reverse(sortField.nullOrdering())
                : sortField.nullOrdering();
        Column column = source.col(CanvasNodeSupport.quoteIdentifier(sortField.columnName()));
        if (direction == SortDirection.ASC) {
            return nullOrdering == NullOrdering.FIRST
                    ? column.asc_nulls_first()
                    : column.asc_nulls_last();
        }
        return nullOrdering == NullOrdering.FIRST
                ? column.desc_nulls_first()
                : column.desc_nulls_last();
    }

    private static SortDirection reverse(SortDirection direction) {
        return direction == SortDirection.ASC ? SortDirection.DESC : SortDirection.ASC;
    }

    private static NullOrdering reverse(NullOrdering ordering) {
        return ordering == NullOrdering.FIRST ? NullOrdering.LAST : NullOrdering.FIRST;
    }

    private static String temporaryColumnName(Set<String> sourceColumns) {
        String candidate = "__datascalpel_deduplicate_row_number";
        while (sourceColumns.contains(candidate)) {
            candidate += "_";
        }
        return candidate;
    }
}
