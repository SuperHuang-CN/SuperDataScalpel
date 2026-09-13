package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateDissolveOptions;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateDissolveGroupingMode;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateStatistic;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregateStatisticKind;
import cn.superhuang.data.scalpel.contract.task.SpatialAggregation;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
        validateDissolve(
                configuration,
                source,
                sourceColumns,
                groupByColumns,
                issues
        );
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        String connectedGroupColumn = null;
        if (connectedDissolve(configuration) && !context.runtimeValues().preview()) {
            if (!SpatialDissolveConnectedSupport.ensureCheckpointDirectory(sourceDataset, issues)) {
                return CanvasNodeOperationResult.invalid(inputSchemas);
            }
            SpatialAggregation union = configuration.aggregations().getFirst();
            SpatialDissolveConnectedSupport.Prepared prepared =
                    SpatialDissolveConnectedSupport.prepare(
                            sourceDataset,
                            union.geometryColumnName()
                    );
            sourceDataset = prepared.source();
            connectedGroupColumn = prepared.groupingColumnName();
        }
        Dataset<Row> aggregateSource = sourceDataset;
        Column[] groupExpressions = connectedGroupColumn == null
                ? groupByColumns.stream()
                .map(columnName -> aggregateSource.col(CanvasNodeSupport.quoteIdentifier(columnName)))
                .toArray(Column[]::new)
                : new Column[]{aggregateSource.col(
                        CanvasNodeSupport.quoteIdentifier(connectedGroupColumn))};
        List<Column> aggregateExpressionList = new ArrayList<>();
        configuration.aggregations().stream()
                .map(item -> aggregateExpression(aggregateSource, sourceColumns, item))
                .forEach(aggregateExpressionList::add);
        SpatialAggregateDissolveOptions dissolve = configuration.dissolve();
        if (dissolve != null && dissolve.enabled()) {
            aggregateExpressionList.add(functions.count(functions.lit(1L))
                    .alias(dissolve.countOutputColumnName()));
            dissolve.summaryStatistics().stream()
                    .map(item -> statisticExpression(aggregateSource, item)
                            .alias(item.outputColumnName()))
                    .forEach(aggregateExpressionList::add);
        }
        Column[] aggregateExpressions = aggregateExpressionList.toArray(Column[]::new);
        Dataset<Row> aggregateDataset;
        if (groupExpressions.length == 0) {
            aggregateDataset = aggregateSource.agg(
                    aggregateExpressions[0], trailing(aggregateExpressions));
        } else {
            RelationalGroupedDataset grouped = aggregateSource.groupBy(groupExpressions);
            aggregateDataset = grouped.agg(
                    aggregateExpressions[0], trailing(aggregateExpressions));
        }
        if (connectedGroupColumn != null) {
            aggregateDataset = aggregateDataset.drop(connectedGroupColumn);
        }
        aggregateDataset = applyDissolvePartMode(
                aggregateDataset,
                configuration,
                sourceColumns
        );

        List<CanvasColumnSchema> fallbackColumns = outputFallbackColumns(
                configuration,
                sourceColumns
        );
        List<CanvasColumnSchema> outputColumns = SparkTypeMapper.fromStructType(
                aggregateDataset.schema(),
                fallbackColumns
        );
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

    private static void validateDissolve(
            SpatialAggregateConfiguration configuration,
            SparkCanvasTable source,
            Map<String, CanvasColumnSchema> sourceColumns,
            List<String> groupByColumns,
            CanvasNodeIssueSink issues
    ) {
        SpatialAggregateDissolveOptions dissolve = configuration.dissolve();
        if (dissolve == null || !dissolve.enabled()) return;
        if (configuration.aggregations().size() != 1
                || configuration.aggregations().getFirst() == null
                || configuration.aggregations().getFirst().kind()
                != cn.superhuang.data.scalpel.contract.task.SpatialAggregationKind.UNION) {
            issues.error(
                    "SPATIAL_DISSOLVE_REQUIRES_SINGLE_UNION",
                    "Dissolve 输出要求恰好配置一个 UNION 空间聚合",
                    "configuration.aggregations"
            );
        }
        if (dissolve.effectiveGroupingMode()
                == SpatialAggregateDissolveGroupingMode.CONNECTED_COMPONENTS) {
            if (!groupByColumns.isEmpty()) {
                issues.error(
                        "SPATIAL_DISSOLVE_CONNECTED_GROUP_FIELDS_NOT_ALLOWED",
                        "按空间连通组 Dissolve 时不能再配置分组字段",
                        "configuration.groupByColumns"
                );
            }
            if (configuration.aggregations().size() == 1
                    && configuration.aggregations().getFirst() != null) {
                CanvasColumnSchema geometry = sourceColumns.get(
                        configuration.aggregations().getFirst().geometryColumnName());
                if (geometry != null && geometry.geometry() != null
                        && geometry.geometry().kind() != GeometryKind.POLYGON
                        && geometry.geometry().kind() != GeometryKind.MULTIPOLYGON) {
                    issues.error(
                            "SPATIAL_DISSOLVE_CONNECTED_REQUIRES_POLYGON",
                            "按空间连通组 Dissolve 只支持 Polygon 或 MultiPolygon",
                            "configuration.aggregations[0].geometryColumnName"
                    );
                }
            }
        }
        if (dissolve.summaryStatistics() == null) {
            issues.error(
                    "REQUIRED_CONFIGURATION",
                    "Dissolve 标量统计必须是数组",
                    "configuration.dissolve.summaryStatistics"
            );
            return;
        }
        if (dissolve.summaryStatistics().size()
                > SpatialAggregateDissolveOptions.MAX_SUMMARY_STATISTICS) {
            issues.error(
                    "SPATIAL_DISSOLVE_STATISTIC_LIMIT_EXCEEDED",
                    "Dissolve 标量统计不能超过 "
                            + SpatialAggregateDissolveOptions.MAX_SUMMARY_STATISTICS + " 项",
                    "configuration.dissolve.summaryStatistics"
            );
        }

        Set<String> resultNames = new HashSet<>();
        for (int index = 0; index < groupByColumns.size(); index++) {
            addDissolveOutputName(
                    groupByColumns.get(index),
                    "configuration.groupByColumns[" + index + "]",
                    resultNames,
                    issues
            );
        }
        for (int index = 0; index < configuration.aggregations().size(); index++) {
            SpatialAggregation aggregation = configuration.aggregations().get(index);
            if (aggregation != null) {
                addDissolveOutputName(
                        aggregation.outputColumnName(),
                        "configuration.aggregations[" + index + "].outputColumnName",
                        resultNames,
                        issues
                );
            }
        }
        CanvasNodeSupport.required(
                dissolve.countOutputColumnName(),
                "请输入来源要素计数字段名",
                "configuration.dissolve.countOutputColumnName",
                issues
        );
        addDissolveOutputName(
                dissolve.countOutputColumnName(),
                "configuration.dissolve.countOutputColumnName",
                resultNames,
                issues
        );

        Set<String> statisticIds = new HashSet<>();
        for (int index = 0; index < dissolve.summaryStatistics().size(); index++) {
            SpatialAggregateStatistic statistic = dissolve.summaryStatistics().get(index);
            String path = "configuration.dissolve.summaryStatistics[" + index + "]";
            if (statistic == null) {
                issues.error("REQUIRED_CONFIGURATION", "Dissolve 标量统计不能为空", path);
                continue;
            }
            if (!uuid(statistic.statisticId())) {
                issues.error(
                        "INVALID_SPATIAL_DISSOLVE_STATISTIC_ID",
                        "Dissolve 标量统计 ID 必须是 UUID",
                        path + ".statisticId"
                );
            } else if (!statisticIds.add(statistic.statisticId())) {
                issues.error(
                        "DUPLICATE_SPATIAL_DISSOLVE_STATISTIC_ID",
                        "Dissolve 标量统计 ID 重复",
                        path + ".statisticId"
                );
            }
            if (statistic.kind() == null) {
                issues.error(
                        "INVALID_SPATIAL_DISSOLVE_STATISTIC_KIND",
                        "请选择 Dissolve 标量统计类型",
                        path + ".kind"
                );
            }
            CanvasNodeSupport.required(
                    statistic.sourceColumnName(),
                    "请选择统计来源字段",
                    path + ".sourceColumnName",
                    issues
            );
            CanvasNodeSupport.required(
                    statistic.outputColumnName(),
                    "请输入统计输出字段名",
                    path + ".outputColumnName",
                    issues
            );
            addDissolveOutputName(
                    statistic.outputColumnName(),
                    path + ".outputColumnName",
                    resultNames,
                    issues
            );
            if (source == null || CanvasNodeSupport.blank(statistic.sourceColumnName())) {
                continue;
            }
            CanvasColumnSchema column = sourceColumns.get(statistic.sourceColumnName());
            if (column == null) {
                issues.error(
                        "COLUMN_NOT_FOUND",
                        "统计来源字段不存在：" + statistic.sourceColumnName(),
                        path + ".sourceColumnName"
                );
            } else if (column.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error(
                        "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "Geometry 字段不能参与 Dissolve 标量统计",
                        path + ".sourceColumnName"
                );
            } else if (statistic.kind() == SpatialAggregateStatisticKind.ANY
                    && column.fieldType() != PlatformDataType.STRING) {
                issues.error(
                        "STRING_COLUMN_REQUIRED",
                        "ANY 统计要求字符串字段",
                        path + ".sourceColumnName"
                );
            } else if (statistic.kind() != null
                    && statistic.kind() != SpatialAggregateStatisticKind.ANY
                    && statistic.kind() != SpatialAggregateStatisticKind.COUNT_FIELD
                    && !numeric(column.fieldType())) {
                issues.error(
                        "NUMERIC_COLUMN_REQUIRED",
                        statistic.kind() + " 统计要求数值字段",
                        path + ".sourceColumnName"
                );
            }
        }
    }

    private static void addDissolveOutputName(
            String name,
            String path,
            Set<String> names,
            CanvasNodeIssueSink issues
    ) {
        if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
            issues.error(
                    "DUPLICATE_COLUMN_NAME",
                    "Dissolve 输出字段名重复：" + name,
                    path
            );
        }
    }

    private static Column statisticExpression(
            Dataset<Row> source,
            SpatialAggregateStatistic statistic
    ) {
        Column column = source.col(CanvasNodeSupport.quoteIdentifier(statistic.sourceColumnName()));
        return switch (statistic.kind()) {
            case COUNT_FIELD -> functions.count(column);
            case SUM -> functions.sum(column);
            case MEAN -> functions.avg(column);
            case MIN -> functions.min(column);
            case MAX -> functions.max(column);
            case RANGE -> functions.max(column).minus(functions.min(column));
            case STDDEV -> functions.stddev_samp(column);
            case VARIANCE -> functions.var_samp(column);
            case ANY -> functions.first(column, true);
        };
    }

    private static Dataset<Row> applyDissolvePartMode(
            Dataset<Row> aggregate,
            SpatialAggregateConfiguration configuration,
            Map<String, CanvasColumnSchema> sourceColumns
    ) {
        SpatialAggregateDissolveOptions dissolve = configuration.dissolve();
        if (dissolve == null || !dissolve.enabled()) return aggregate;
        SpatialAggregation union = configuration.aggregations().getFirst();
        String geometryName = union.outputColumnName();
        int srid = sourceColumns.get(union.geometryColumnName()).geometry().crs().code();
        Column geometry = aggregate.col(CanvasNodeSupport.quoteIdentifier(geometryName));
        Dataset<Row> nonEmpty = aggregate.filter(
                geometry.isNotNull().and(functions.not(st_functions.ST_IsEmpty(geometry)))
        );
        geometry = nonEmpty.col(CanvasNodeSupport.quoteIdentifier(geometryName));
        if (dissolve.multipart()) {
            return nonEmpty.withColumn(
                    geometryName,
                    st_functions.ST_SetSRID(
                            st_functions.ST_Multi(geometry),
                            functions.lit(srid)
                    )
            );
        }
        List<Column> projection = new ArrayList<>(nonEmpty.columns().length);
        for (String name : nonEmpty.columns()) {
            Column column = nonEmpty.col(CanvasNodeSupport.quoteIdentifier(name));
            projection.add(name.equals(geometryName)
                    ? functions.explode(st_functions.ST_Dump(column)).alias(name)
                    : column);
        }
        Dataset<Row> exploded = nonEmpty.select(projection.toArray(Column[]::new));
        return exploded.withColumn(
                geometryName,
                st_functions.ST_SetSRID(
                        exploded.col(CanvasNodeSupport.quoteIdentifier(geometryName)),
                        functions.lit(srid)
                )
        );
    }

    private static List<CanvasColumnSchema> outputFallbackColumns(
            SpatialAggregateConfiguration configuration,
            Map<String, CanvasColumnSchema> sourceColumns
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>();
        for (String name : configuration.groupByColumns()) {
            columns.add(groupByColumn(sourceColumns.get(name)));
        }
        for (SpatialAggregation item : configuration.aggregations()) {
            GeometryTypeDefinition sourceGeometry = sourceColumns
                    .get(item.geometryColumnName()).geometry();
            columns.add(new CanvasColumnSchema(
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
        SpatialAggregateDissolveOptions dissolve = configuration.dissolve();
        if (dissolve != null && dissolve.enabled()) {
            columns.add(new CanvasColumnSchema(
                    dissolve.countOutputColumnName(), PlatformDataType.LONG,
                    null, null, null, false,
                    null, false, false, null
            ));
            for (SpatialAggregateStatistic statistic : dissolve.summaryStatistics()) {
                CanvasColumnSchema source = sourceColumns.get(statistic.sourceColumnName());
                columns.add(new CanvasColumnSchema(
                        statistic.outputColumnName(), source.fieldType(),
                        source.length(), source.precision(), source.scale(), true,
                        null, false, false, source.comment()
                ));
            }
        }
        return List.copyOf(columns);
    }

    private static boolean uuid(String value) {
        if (CanvasNodeSupport.blank(value)) return false;
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean numeric(PlatformDataType type) {
        return type == PlatformDataType.BYTE || type == PlatformDataType.SHORT
                || type == PlatformDataType.INTEGER || type == PlatformDataType.LONG
                || type == PlatformDataType.FLOAT || type == PlatformDataType.DOUBLE
                || type == PlatformDataType.DECIMAL;
    }

    private static boolean connectedDissolve(SpatialAggregateConfiguration configuration) {
        SpatialAggregateDissolveOptions dissolve = configuration.dissolve();
        return dissolve != null && dissolve.enabled()
                && dissolve.effectiveGroupingMode()
                == SpatialAggregateDissolveGroupingMode.CONNECTED_COMPONENTS;
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
