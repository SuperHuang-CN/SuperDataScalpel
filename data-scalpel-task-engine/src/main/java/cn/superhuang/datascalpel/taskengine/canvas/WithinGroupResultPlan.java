package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Linked main/group result strategy of the single Summarize Within operator. */
final class WithinGroupResultPlan {
    private WithinGroupResultPlan() {}

    static void validate(SpatialSummarizeWithinConfiguration configuration,
                         Map<String, SparkCanvasTable> inputs, Map<String, CanvasColumnSchema> areaColumns,
                         GeometryKind shape, List<JoinOutputColumnSupport.ResolvedOutputColumn> areaOutputs,
                         CanvasNodeIssueSink issues) {
        if (!configuration.usesLinkedGroupResult()) return;
        var result = configuration.groupResult();
        String path = "configuration.groupResult";
        CanvasNodeSupport.required(result.areaKeyColumnName(), "请选择区域唯一键字段", path + ".areaKeyColumnName", issues);
        CanvasColumnSchema key = result.areaKeyColumnName() == null ? null : areaColumns.get(result.areaKeyColumnName());
        if (!CanvasNodeSupport.blank(result.areaKeyColumnName()) && key == null) {
            issues.error("COLUMN_NOT_FOUND", "区域唯一键字段不存在", path + ".areaKeyColumnName");
        } else if (key != null && key.fieldType() == PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "区域唯一键必须是标量字段", path + ".areaKeyColumnName");
        }
        CanvasNodeSupport.required(result.outputTableName(), "请输入关联组表名", path + ".outputTableName", issues);
        if (!CanvasNodeSupport.blank(result.outputTableName()) && (inputs.containsKey(result.outputTableName())
                || result.outputTableName().equals(configuration.outputTableName()))) {
            issues.error("DUPLICATE_TABLE_NAME", "关联组表名与入口表或主表重复", path + ".outputTableName");
        }
        if (shape != null && shape != GeometryKind.POINT && shape != GeometryKind.MULTIPOINT
                && shape != GeometryKind.LINESTRING && shape != GeometryKind.MULTILINESTRING
                && shape != GeometryKind.POLYGON && shape != GeometryKind.MULTIPOLYGON) {
            issues.error("SPATIAL_WITHIN_GROUP_SHAPE_UNSUPPORTED", "分组形状比例要求明确的点、线或面类型", "configuration.summaryGeometryColumnName");
        }
        Set<String> main = new HashSet<>();
        areaOutputs.forEach(item -> main.add(item.outputColumnName().toLowerCase(Locale.ROOT)));
        if (configuration.statistics() != null) configuration.statistics().forEach(item -> { if (item != null && item.outputColumnName() != null) main.add(item.outputColumnName().toLowerCase(Locale.ROOT)); });
        Set<String> group = new HashSet<>();
        if (configuration.statistics() != null) configuration.statistics().forEach(item -> { if (item != null && item.outputColumnName() != null) group.add(item.outputColumnName().toLowerCase(Locale.ROOT)); });
        addTemporalNames(configuration.temporalSlicing(), main);
        addTemporalNames(configuration.temporalSlicing(), group);
        addName(result.areaKeyOutputColumnName(), path + ".areaKeyOutputColumnName", main, issues);
        addName(result.areaKeyOutputColumnName(), path + ".areaKeyOutputColumnName", group, issues);
        addName(result.groupValueColumnName(), path + ".groupValueColumnName", group, issues);
        var summary = configuration.groupSummary();
        if (summary.includeMinorityMajority()) {
            addName(result.minorityValueColumnName(), path + ".minorityValueColumnName", main, issues);
            addName(result.majorityValueColumnName(), path + ".majorityValueColumnName", main, issues);
            if (summary.includeGroupPercentage()) {
                addName(result.minorityPercentageColumnName(), path + ".minorityPercentageColumnName", main, issues);
                addName(result.majorityPercentageColumnName(), path + ".majorityPercentageColumnName", main, issues);
            }
            issues.warning("SPATIAL_WITHIN_GROUP_TIES_USE_ORDER", "形状量并列时按组值升序选取一个组，NULL 排在最后（平台规则）", path);
        }
        if (summary.includeGroupPercentage()) addName(summary.groupPercentageColumnName(),
                "configuration.groupSummary.groupPercentageColumnName", group, issues);
    }

    private static void addTemporalNames(SpatialTemporalSlicing temporal, Set<String> names) {
        if (temporal == null) return;
        if (temporal.windowStartColumnName() != null) names.add(temporal.windowStartColumnName().toLowerCase(Locale.ROOT));
        if (temporal.windowEndColumnName() != null) names.add(temporal.windowEndColumnName().toLowerCase(Locale.ROOT));
    }

    private static void addName(String name, String path, Set<String> names, CanvasNodeIssueSink issues) {
        CanvasNodeSupport.required(name, "请输入输出字段名", path, issues);
        if (CanvasNodeSupport.blank(name)) return;
        if (!names.add(name.toLowerCase(Locale.ROOT))) issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复：" + name, path);
    }

    static Dataset<Row> withAreaKey(Dataset<Row> areas, SpatialWithinGroupResult result, String internalKey) {
        Column source = field(areas, result.areaKeyColumnName());
        // NULL keys fail independently; counting the key preserves the same uniqueness rule
        // while retaining its field dependency in the analyzed window expression.
        Column unique = functions.count(source).over(Window.partitionBy(source)).equalTo(1);
        Column checked = functions.when(source.isNotNull().and(unique), source)
                .otherwise(functions.raise_error(functions.lit("SPATIAL_WITHIN_AREA_KEY_INVALID"))
                        .cast(areas.schema().apply(result.areaKeyColumnName()).dataType()));
        return areas.withColumn(internalKey, checked);
    }

    static Column shapeMeasure(Column intersection, GeometryKind shape, SpatialDistanceMethod method) {
        Column measure = switch (shape) {
            case POINT, MULTIPOINT -> st_functions.ST_NPoints(intersection).cast("double");
            case LINESTRING, MULTILINESTRING -> method == SpatialDistanceMethod.GEODESIC
                    ? st_functions.ST_LengthSpheroid(intersection) : st_functions.ST_Length(intersection);
            case POLYGON, MULTIPOLYGON -> method == SpatialDistanceMethod.GEODESIC
                    ? st_functions.ST_AreaSpheroid(intersection) : st_functions.ST_Area(intersection);
            default -> throw new IllegalArgumentException("SPATIAL_WITHIN_GROUP_SHAPE_UNSUPPORTED");
        };
        return functions.when(measure.geq(0), WithinStatisticSupport.finiteValue(measure));
    }

    static CanvasNodeOperationResult build(SpatialSummarizeWithinConfiguration configuration,
            Dataset<Row> prepared, List<Column> statistics,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> areaOutputs,
            Map<String, CanvasColumnSchema> areaColumns, Map<String, CanvasColumnSchema> summaryColumns,
            Map<String, SparkCanvasTable> inputs, String prefix) {
        String id = prefix + "area_row_id", value = prefix + "group_value";
        String measure = prefix + "group_measure", percentage = prefix + "group_percentage";
        var options = configuration.groupResult();
        var group = configuration.groupSummary();
        List<String> keyNames = new ArrayList<>(List.of(id));
        if (configuration.temporalSlicing() != null) {
            keyNames.add(prefix + "window_start"); keyNames.add(prefix + "window_end");
        }
        List<Column> mainKeys = new ArrayList<>(keyNames.stream().map(name -> field(prepared, name)).toList());
        areaOutputs.forEach(item -> mainKeys.add(field(prepared, item.outputColumnName())));
        Dataset<Row> main = aggregate(prepared, mainKeys, statistics);

        // Only real matches enter the group relation. A genuine NULL group remains a real group.
        Dataset<Row> matched = prepared.filter(field(prepared, prefix + "matched"));
        List<Column> groupKeys = new ArrayList<>(keyNames.stream().map(name -> field(matched, name)).toList());
        groupKeys.add(field(matched, value));
        List<Column> groupStatistics = new ArrayList<>(statistics);
        groupStatistics.add(functions.sum(field(matched, prefix + "shape_measure")).alias(measure));
        Dataset<Row> groups = aggregate(matched, groupKeys, groupStatistics);
        var partition = Window.partitionBy(keyNames.stream().map(name -> functions.col(quote(name))).toArray(Column[]::new));
        groups = groups.withColumn(percentage, functions.try_divide(field(groups, measure).multiply(100d),
                functions.sum(field(groups, measure)).over(partition)));
        if (group.includeMinorityMajority()) {
            main = addDominant(main, groups, configuration, keyNames, value, measure, percentage, prefix, true);
            main = addDominant(main, groups, configuration, keyNames, value, measure, percentage, prefix, false);
        }

        List<Column> mainProjection = new ArrayList<>();
        List<CanvasColumnSchema> mainFallback = new ArrayList<>();
        mainProjection.add(field(main, id).alias(options.areaKeyOutputColumnName()));
        var keySchema = JoinOutputColumnSupport.copyWithName(areaColumns.get(options.areaKeyColumnName()), options.areaKeyOutputColumnName());
        mainFallback.add(keySchema);
        for (var item : areaOutputs) {
            mainProjection.add(field(main, item.outputColumnName()));
            mainFallback.add(JoinOutputColumnSupport.copyWithName(item.sourceColumn(), item.outputColumnName()));
        }
        appendTemporalProjection(mainProjection, main, configuration.temporalSlicing(), prefix);
        configuration.statistics().forEach(item -> mainProjection.add(functions.col(quote(item.outputColumnName()))));
        var groupColumn = summaryColumns.get(group.groupByColumnName());
        if (group.includeMinorityMajority()) {
            mainProjection.add(field(main, options.minorityValueColumnName()));
            mainProjection.add(field(main, options.majorityValueColumnName()));
            mainFallback.add(JoinOutputColumnSupport.copyWithName(groupColumn, options.minorityValueColumnName()));
            mainFallback.add(JoinOutputColumnSupport.copyWithName(groupColumn, options.majorityValueColumnName()));
            if (group.includeGroupPercentage()) {
                mainProjection.add(field(main, options.minorityPercentageColumnName()));
                mainProjection.add(field(main, options.majorityPercentageColumnName()));
            }
        }
        List<Column> groupProjection = new ArrayList<>();
        groupProjection.add(field(groups, id).alias(options.areaKeyOutputColumnName()));
        groupProjection.add(field(groups, value).alias(options.groupValueColumnName()));
        appendTemporalProjection(groupProjection, groups, configuration.temporalSlicing(), prefix);
        configuration.statistics().forEach(item -> groupProjection.add(functions.col(quote(item.outputColumnName()))));
        if (group.includeGroupPercentage()) groupProjection.add(field(groups, percentage).alias(group.groupPercentageColumnName()));
        var groupFallback = List.of(keySchema, JoinOutputColumnSupport.copyWithName(groupColumn, options.groupValueColumnName()));
        SparkCanvasTable mainTable = table(configuration.outputTableName(), main.select(mainProjection.toArray(Column[]::new)), mainFallback);
        SparkCanvasTable groupTable = table(options.outputTableName(), groups.select(groupProjection.toArray(Column[]::new)), groupFallback);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(mainTable.schema().name(), mainTable);
        output.put(groupTable.schema().name(), groupTable);
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static Dataset<Row> addDominant(Dataset<Row> main, Dataset<Row> groups,
            SpatialSummarizeWithinConfiguration configuration, List<String> keys,
            String value, String measure, String percentage, String prefix, boolean minority) {
        var options = configuration.groupResult();
        String valueName = minority ? options.minorityValueColumnName() : options.majorityValueColumnName();
        String percentageName = minority ? options.minorityPercentageColumnName() : options.majorityPercentageColumnName();
        String rank = prefix + "rank";
        var order = Window.partitionBy(keys.stream().map(name -> field(groups, name)).toArray(Column[]::new))
                .orderBy(minority ? field(groups, measure).asc() : field(groups, measure).desc(), field(groups, value).asc_nulls_last());
        Dataset<Row> winner = groups.filter(field(groups, measure).gt(0))
                .withColumn(rank, functions.row_number().over(order)).filter(functions.col(quote(rank)).equalTo(1));
        List<Column> winnerFields = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) winnerFields.add(field(winner, keys.get(i)).alias(prefix + "join_key_" + i));
        winnerFields.add(field(winner, value).alias(valueName));
        boolean includePercentage = configuration.groupSummary().includeGroupPercentage();
        if (includePercentage) winnerFields.add(field(winner, percentage).alias(percentageName));
        winner = winner.select(winnerFields.toArray(Column[]::new));
        Column condition = functions.lit(true);
        for (int i = 0; i < keys.size(); i++) condition = condition.and(field(main, keys.get(i)).eqNullSafe(field(winner, prefix + "join_key_" + i)));
        List<Column> projection = new ArrayList<>();
        for (String name : main.columns()) projection.add(field(main, name));
        projection.add(field(winner, valueName));
        if (includePercentage) projection.add(field(winner, percentageName));
        return main.join(winner, condition, "left_outer").select(projection.toArray(Column[]::new));
    }

    private static Dataset<Row> aggregate(Dataset<Row> table, List<Column> keys, List<Column> statistics) {
        return table.groupBy(keys.toArray(Column[]::new)).agg(statistics.getFirst(), statistics.subList(1, statistics.size()).toArray(Column[]::new));
    }

    private static void appendTemporalProjection(List<Column> projection, Dataset<Row> table,
                                                 SpatialTemporalSlicing time, String prefix) {
        if (time == null) return;
        projection.add(field(table, prefix + "window_start").alias(time.windowStartColumnName()));
        projection.add(field(table, prefix + "window_end").alias(time.windowEndColumnName()));
    }

    private static SparkCanvasTable table(String name, Dataset<Row> data, List<CanvasColumnSchema> fallback) {
        return new SparkCanvasTable(new CanvasTableSchema(name, null,
                SparkTypeMapper.fromStructType(data.schema(), fallback), CanvasDatasetKind.BOUNDED, null, null), data);
    }

    private static Column field(Dataset<Row> table, String name) { return table.col(quote(name)); }
    private static String quote(String name) { return CanvasNodeSupport.quoteIdentifier(name); }
}
