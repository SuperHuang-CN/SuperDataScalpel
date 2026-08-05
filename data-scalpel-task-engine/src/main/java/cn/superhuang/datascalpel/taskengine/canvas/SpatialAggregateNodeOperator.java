package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregation;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.RelationalGroupedDataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_aggregates;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpatialAggregateNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_AGGREGATE;
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
        if (!(definition instanceof SpatialAggregateNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "SPATIAL_AGGREGATE operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialAggregateConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        } else if (source != null
                && source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error(
                    "SPATIAL_AGGREGATE_REQUIRES_BOUNDED_INPUT",
                    "空间聚合只支持有界来源表",
                    "configuration.sourceTableName"
            );
        }
        if (configuration.groupByColumns() == null) {
            issues.error("REQUIRED_CONFIGURATION", "分组字段必须是数组",
                    "configuration.groupByColumns");
        }
        if (configuration.aggregations() == null) {
            issues.error("REQUIRED_CONFIGURATION", "空间聚合项必须是数组",
                    "configuration.aggregations");
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.aggregations().isEmpty()) {
            issues.error("EMPTY_SPATIAL_AGGREGATIONS", "至少配置一个空间聚合项",
                    "configuration.aggregations");
        } else if (configuration.aggregations().size()
                > SpatialAggregateConfiguration.MAX_AGGREGATIONS) {
            issues.error(
                    "SPATIAL_AGGREGATION_LIMIT_EXCEEDED",
                    "空间聚合项不能超过 "
                            + SpatialAggregateConfiguration.MAX_AGGREGATIONS + " 个",
                    "configuration.aggregations"
            );
        }

        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        List<String> groupByColumns = configuration.groupByColumns() == null
                ? List.of() : configuration.groupByColumns();
        Set<String> groupByNames = new HashSet<>();
        for (int index = 0; index < groupByColumns.size(); index++) {
            String columnName = groupByColumns.get(index);
            String path = "configuration.groupByColumns[" + index + "]";
            CanvasNodeSupport.required(columnName, "请选择分组字段", path, issues);
            if (!CanvasNodeSupport.blank(columnName) && !groupByNames.add(columnName)) {
                issues.error("DUPLICATE_GROUP_BY_COLUMN", "分组字段重复：" + columnName, path);
            }
            CanvasColumnSchema column = sourceColumns.get(columnName);
            if (source != null && !CanvasNodeSupport.blank(columnName) && column == null) {
                issues.error("COLUMN_NOT_FOUND", "分组字段不存在：" + columnName, path);
            } else if (column != null && column.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "Geometry 字段不能作为分组字段", path);
            }
        }

        Set<String> outputNames = new HashSet<>();
        for (int index = 0; index < configuration.aggregations().size(); index++) {
            SpatialAggregation item = configuration.aggregations().get(index);
            String path = "configuration.aggregations[" + index + "]";
            if (item == null) {
                issues.error("REQUIRED_CONFIGURATION", "空间聚合项不能为空", path);
                continue;
            }
            if (item.kind() == null) {
                issues.error("INVALID_SPATIAL_AGGREGATION_KIND", "请选择空间聚合类型",
                        path + ".kind");
            }
            CanvasNodeSupport.required(item.geometryColumnName(), "请选择 Geometry 字段",
                    path + ".geometryColumnName", issues);
            CanvasNodeSupport.required(item.outputColumnName(), "请输入聚合输出字段名",
                    path + ".outputColumnName", issues);
            if (!CanvasNodeSupport.blank(item.outputColumnName())
                    && !outputNames.add(item.outputColumnName())) {
                issues.error(
                        "DUPLICATE_SPATIAL_AGGREGATE_OUTPUT_COLUMN",
                        "空间聚合输出字段名重复：" + item.outputColumnName(),
                        path + ".outputColumnName"
                );
            }
            if (!CanvasNodeSupport.blank(item.outputColumnName())
                    && groupByNames.contains(item.outputColumnName())) {
                issues.error(
                        "SPATIAL_AGGREGATE_OUTPUT_COLUMN_CONFLICT",
                        "空间聚合输出字段与分组字段同名：" + item.outputColumnName(),
                        path + ".outputColumnName"
                );
            }
            if (source == null || CanvasNodeSupport.blank(item.geometryColumnName())) {
                continue;
            }
            CanvasColumnSchema geometryColumn = sourceColumns.get(item.geometryColumnName());
            if (geometryColumn == null) {
                issues.error("COLUMN_NOT_FOUND",
                        "Geometry 字段不存在：" + item.geometryColumnName(),
                        path + ".geometryColumnName");
            } else if (geometryColumn.fieldType() != PlatformDataType.GEOMETRY) {
                issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "所选字段不是 Geometry：" + item.geometryColumnName(),
                        path + ".geometryColumnName");
            } else {
                CanvasNodeSupport.validateSupportedGeometry(
                        List.of(geometryColumn), path + ".geometryColumnName", issues);
            }
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Column[] groupExpressions = groupByColumns.stream()
                .map(columnName -> sourceDataset.col(CanvasNodeSupport.quoteIdentifier(columnName)))
                .toArray(Column[]::new);
        Column[] aggregateExpressions = configuration.aggregations().stream()
                .map(item -> aggregateExpression(sourceDataset, sourceColumns, item))
                .toArray(Column[]::new);
        Dataset<Row> aggregateDataset;
        if (groupExpressions.length == 0) {
            aggregateDataset = sourceDataset.agg(
                    aggregateExpressions[0], trailing(aggregateExpressions));
        } else {
            RelationalGroupedDataset grouped = sourceDataset.groupBy(groupExpressions);
            aggregateDataset = grouped.agg(
                    aggregateExpressions[0], trailing(aggregateExpressions));
        }

        List<CanvasColumnSchema> outputColumns = new ArrayList<>(
                groupByColumns.size() + configuration.aggregations().size());
        for (String columnName : groupByColumns) {
            outputColumns.add(groupByColumn(sourceColumns.get(columnName)));
        }
        for (SpatialAggregation item : configuration.aggregations()) {
            GeometryTypeDefinition sourceGeometry = sourceColumns
                    .get(item.geometryColumnName()).geometry();
            outputColumns.add(new CanvasColumnSchema(
                    item.outputColumnName(), PlatformDataType.GEOMETRY,
                    null, null, null, true,
                    null, false, false, null,
                    new GeometryTypeDefinition(
                            GeometryKind.GEOMETRY,
                            sourceGeometry.crs(),
                            sourceGeometry.dimension()
                    )
            ));
        }
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, aggregateDataset));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static Column aggregateExpression(
            Dataset<Row> source,
            Map<String, CanvasColumnSchema> sourceColumns,
            SpatialAggregation item
    ) {
        Column geometry = source.col(CanvasNodeSupport.quoteIdentifier(item.geometryColumnName()));
        Column aggregate = switch (item.kind()) {
            case UNION -> st_aggregates.ST_Union_Agg(geometry);
            case INTERSECTION -> st_aggregates.ST_Intersection_Agg(geometry);
            case COLLECT -> st_aggregates.ST_Collect_Agg(geometry);
            case ENVELOPE -> st_aggregates.ST_Envelope_Agg(geometry);
        };
        int srid = sourceColumns.get(item.geometryColumnName()).geometry().crs().code();
        return st_functions.ST_SetSRID(aggregate, functions.lit(srid))
                .alias(item.outputColumnName());
    }

    private static Column[] trailing(Column[] columns) {
        if (columns.length <= 1) {
            return new Column[0];
        }
        Column[] trailing = new Column[columns.length - 1];
        System.arraycopy(columns, 1, trailing, 0, trailing.length);
        return trailing;
    }

    private static CanvasColumnSchema groupByColumn(CanvasColumnSchema source) {
        return new CanvasColumnSchema(
                source.name(),
                source.fieldType(),
                source.length(),
                source.precision(),
                source.scale(),
                source.nullable(),
                null,
                false,
                false,
                source.comment()
        );
    }
}
