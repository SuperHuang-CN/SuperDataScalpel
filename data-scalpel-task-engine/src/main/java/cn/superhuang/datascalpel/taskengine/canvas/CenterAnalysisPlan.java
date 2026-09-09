package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.*;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.*;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.types.*;
import org.locationtech.jts.geom.Geometry;
import scala.collection.Seq;
import scala.jdk.javaapi.CollectionConverters;
import java.util.*;

/** Independent result projections from one bounded, Executor-local group summary. */
final class CenterAnalysisPlan {
    private CenterAnalysisPlan() { }

    static CanvasNodeOperationResult apply(SpatialCenterDispersionConfiguration c, SparkCanvasTable source,
            List<CanvasColumnSchema> groups, CanvasColumnSchema geometryColumn, Map<String, SparkCanvasTable> inputs) {
        boolean centralEnabled = c.analyses().stream().anyMatch(a -> a.kind() == SpatialCenterDispersionKind.CENTRAL_FEATURE);
        boolean projectFeature = c.analyses().stream().anyMatch(a -> a.kind() == SpatialCenterDispersionKind.CENTRAL_FEATURE && a.centralFeatureColumns() != null);
        List<SpatialCenterFeatureColumn> featureColumns = c.analyses().stream()
                .filter(a -> a.kind() == SpatialCenterDispersionKind.CENTRAL_FEATURE && a.centralFeatureColumns() != null)
                .flatMap(a -> a.centralFeatureColumns().stream()).filter(SpatialCenterFeatureColumn::included).toList();
        DataType geometryType = source.dataset().schema().apply(c.pointGeometryColumnName()).dataType();
        DataType idType = centralEnabled ? source.dataset().schema().apply(c.featureIdColumnName()).dataType() : DataTypes.StringType;
        List<Column> projection = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) projection.add(source.dataset().col(CanvasNodeSupport.quoteIdentifier(groups.get(i).name())).alias("g" + i));
        projection.add(functions.udf((UDF1<Geometry, Geometry>) CenterGeometryStatistics::checked, geometryType)
                .apply(source.dataset().col(CanvasNodeSupport.quoteIdentifier(c.pointGeometryColumnName()))).alias("geometry"));
        Column weight = CanvasNodeSupport.blank(c.weightColumnName()) ? functions.lit(1d)
                : source.dataset().col(CanvasNodeSupport.quoteIdentifier(c.weightColumnName())).cast(DataTypes.DoubleType);
        projection.add(functions.udf((UDF1<Double, Double>) value -> {
            if (value != null && (!Double.isFinite(value) || value < 0)) throw new IllegalArgumentException("SPATIAL_CENTER_WEIGHT_INVALID");
            return value;
        }, DataTypes.DoubleType).apply(weight).alias("weight"));
        projection.add((centralEnabled ? source.dataset().col(CanvasNodeSupport.quoteIdentifier(c.featureIdColumnName())) : functions.lit(null).cast(idType)).alias("feature_id"));
        if (projectFeature) {
            // Keep attributes outside collect_list; only the selected row is joined back below.
            for (int i = 0; i < featureColumns.size(); i++) projection.add(source.dataset()
                    .col(CanvasNodeSupport.quoteIdentifier(featureColumns.get(i).sourceColumnName())).alias("original_" + i));
            projection.add(source.dataset().col(CanvasNodeSupport.quoteIdentifier(c.pointGeometryColumnName())).alias("original_geometry"));
        }
        Dataset<Row> base = source.dataset().select(projection.toArray(Column[]::new))
                .filter(col("geometry").isNotNull().and(col("weight").isNotNull()));
        Column[] groupExpressions = new Column[groups.size()];
        for (int i = 0; i < groups.size(); i++) groupExpressions[i] = col("g" + i);
        if (centralEnabled) {
            Column validId = col("feature_id").isNotNull().and(functions.count(col("feature_id")).over(Window.partitionBy(col("feature_id"))).equalTo(1));
            base = base.withColumn("feature_id", functions.when(validId, col("feature_id"))
                    .otherwise(functions.raise_error(functions.lit("SPATIAL_CENTER_FEATURE_ID_INVALID")))).filter(col("feature_id").isNotNull());
            // IDs are unique/non-null here. A cumulative count has the same native-ID order as
            // row_number while retaining a concrete field dependency for Catalyst lineage.
            base = base.withColumn("feature_order", functions.count(col("feature_id")).over(Window.partitionBy(groupExpressions)
                    .orderBy(col("feature_id")).rowsBetween(Window.unboundedPreceding(), Window.currentRow())).cast(DataTypes.IntegerType));
        } else base = base.withColumn("feature_order", functions.lit(0));
        int limit = centralEnabled ? CenterGeometryStatistics.MAX_CENTRAL_FEATURES : CenterGeometryStatistics.MAX_GROUP_FEATURES;
        Column vertices = functions.udf((UDF1<Geometry, Long>) geometry -> (long) geometry.getNumPoints(), DataTypes.LongType).apply(col("geometry"));
        base = base.withColumn("group_size", functions.count(col("geometry")).over(Window.partitionBy(groupExpressions)))
                .withColumn("group_vertices", functions.sum(vertices).over(Window.partitionBy(groupExpressions)))
                .withColumn("weight", functions.when(col("group_size").leq(limit).and(col("group_vertices").leq(CenterGeometryStatistics.MAX_GROUP_VERTICES)), col("weight"))
                        .otherwise(functions.raise_error(functions.lit("SPATIAL_CENTER_GROUP_LIMIT_EXCEEDED"))))
                .filter(col("weight").isNotNull());
        Column collected = functions.collect_list(functions.struct(col("geometry"), col("weight"), col("feature_id"), col("feature_order"))).alias("observations");
        Dataset<Row> grouped = groups.isEmpty() ? base.agg(collected) : base.groupBy(groupExpressions).agg(collected);
        StructType summaryType = new StructType();
        for (int i = 0; i < c.analyses().size(); i++) summaryType = summaryType.add("geometry_" + i, geometryType, true);
        summaryType = summaryType.add("selected_id", idType, true);
        var analyses = c.analyses(); int srid = geometryColumn.geometry().crs().code();
        SpatialCenterDispersionKind[] kinds = analyses.stream().map(SpatialCenterDispersionAnalysis::kind).toArray(SpatialCenterDispersionKind[]::new);
        int[] deviations = analyses.stream().mapToInt(a -> a.standardDeviations() == null ? 1 : a.standardDeviations()).toArray();
        Column summary = functions.udf((UDF1<Seq<Row>, Row>) values -> {
            if (values == null || values.isEmpty()) return null;
            List<CenterGeometryStatistics.Observation> observations = new ArrayList<>();
            for (Row row : CollectionConverters.asJava(values)) observations.add(new CenterGeometryStatistics.Observation(row.getAs(0), row.getDouble(1), row.get(2), row.getInt(3)));
            if (centralEnabled) observations.sort(Comparator.comparingInt(CenterGeometryStatistics.Observation::order));
            CenterGeometryStatistics statistics = new CenterGeometryStatistics(observations, srid);
            if (!statistics.hasWeight()) return null;
            CenterGeometryStatistics.Observation central = centralEnabled ? statistics.central() : null;
            Object[] result = new Object[kinds.length + 1];
            for (int i = 0; i < kinds.length; i++) {
                result[i] = switch (kinds[i]) {
                    case MEAN_CENTER -> statistics.mean();
                    case MEDIAN_CENTER -> statistics.median();
                    case CENTRAL_FEATURE -> central.geometry();
                    case STANDARD_DISTANCE -> statistics.dispersion(deviations[i], false);
                    case DIRECTIONAL_ELLIPSE -> statistics.dispersion(deviations[i], true);
                };
            }
            result[kinds.length] = central == null ? null : central.id();
            return RowFactory.create(result);
        }, summaryType).apply(col("observations"));
        Dataset<Row> statistics = grouped.withColumn("summary", summary).filter(col("summary").isNotNull());
        Map<String, SparkCanvasTable> result = new LinkedHashMap<>(inputs);
        for (int i = 0; i < analyses.size(); i++) {
            var analysis = analyses.get(i);
            boolean explicitProjection = analysis.kind() == SpatialCenterDispersionKind.CENTRAL_FEATURE && analysis.centralFeatureColumns() != null;
            Dataset<Row> resultRows = statistics;
            List<Column> selection = new ArrayList<>();
            List<CanvasColumnSchema> schemaColumns = new ArrayList<>();
            String eventTime = null;
            Column outputGeometry = col("summary").getField("geometry_" + i);
            if (explicitProjection) {
                Dataset<Row> selected = statistics.alias("center_stats");
                List<Column> originalSelection = new ArrayList<>(List.of(col("feature_id"), col("original_geometry")));
                for (int j = 0; j < featureColumns.size(); j++) originalSelection.add(col("original_" + j));
                Dataset<Row> originals = base.select(originalSelection.toArray(Column[]::new)).alias("center_originals");
                resultRows = selected.join(originals, col("center_stats.summary").getField("selected_id").equalTo(col("center_originals.feature_id")), "inner");
                outputGeometry = col("center_originals.original_geometry");
                for (int j = 0; j < featureColumns.size(); j++) {
                    var field = featureColumns.get(j);
                    var original = CanvasNodeSupport.columns(source.schema()).get(field.sourceColumnName());
                    selection.add(col("center_originals.original_" + j).alias(field.outputColumnName()));
                    schemaColumns.add(new CanvasColumnSchema(field.outputColumnName(), original.fieldType(), original.length(), original.precision(), original.scale(),
                            original.nullable(), original.defaultValue(), false, false, original.comment(), original.geometry()));
                    if (field.sourceColumnName().equals(source.schema().eventTimeColumn())) eventTime = field.outputColumnName();
                }
            } else {
                schemaColumns.addAll(groups);
                for (int j = 0; j < groups.size(); j++) selection.add(col("g" + j).alias(groups.get(j).name()));
            }
            if (!explicitProjection && analysis.kind() == SpatialCenterDispersionKind.CENTRAL_FEATURE && groups.stream().noneMatch(g -> g.name().equals(c.featureIdColumnName()))) {
                selection.add(col("summary").getField("selected_id").alias(c.featureIdColumnName()));
                schemaColumns.add(CanvasNodeSupport.columns(source.schema()).get(c.featureIdColumnName()));
            }
            selection.add(outputGeometry.alias(analysis.outputColumnName()));
            GeometryKind kind = switch (analysis.kind()) {
                case CENTRAL_FEATURE -> geometryColumn.geometry().kind();
                case MEAN_CENTER, MEDIAN_CENTER -> GeometryKind.POINT;
                case STANDARD_DISTANCE, DIRECTIONAL_ELLIPSE -> GeometryKind.POLYGON;
            };
            schemaColumns.add(new CanvasColumnSchema(analysis.outputColumnName(), PlatformDataType.GEOMETRY, null, null, null, false, null, false, false, null,
                    new GeometryTypeDefinition(kind, geometryColumn.geometry().crs(), CoordinateDimension.XY)));
            CanvasTableSchema schema = new CanvasTableSchema(analysis.outputTableName(), null, schemaColumns, CanvasDatasetKind.BOUNDED, eventTime, null);
            result.put(schema.name(), new SparkCanvasTable(schema, resultRows.select(selection.toArray(Column[]::new))));
        }
        return CanvasNodeOperationResult.propagated(result, CanvasNodeSupport.schemas(result));
    }
    private static Column col(String name) { return functions.col(name); }
}
