package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.*;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.*;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.locationtech.jts.geom.Geometry;

import java.util.*;

/** KNN gives an upper bound, never the final tie set. A radius join recovers every eligible tie. */
final class NearestExactPlan {
    private static final String DISTANCE = "__nearest_distance";
    private static final String RANK = "__nearest_rank";
    private static final String RADIUS = "__nearest_radius";
    private static final String RADIUS_ID = "__nearest_radius_id";
    private static final String MATCH_ID = "__nearest_match_id";
    private static final String GEODESIC_MATCH = "__nearest_geodesic_match";
    private static final String GEODESIC_WITNESSES = "__nearest_geodesic_witnesses";
    private static final String GEODESIC_LOWER = "__nearest_geodesic_lower";
    private static final String GEODESIC_UPPER = "__nearest_geodesic_upper";
    private static final String GEODESIC_EXACT = "__nearest_geodesic_exact";
    private static final String FOLLOWING_LOWER = "__nearest_following_lower";
    private static final String FOLLOWING_APPROXIMATE_LOWER = "__nearest_following_approximate_lower";
    private static final String VERIFIED_RANK = "__nearest_verified_rank";
    private static final String BOUNDS_PREFIX = "__nearest_bounds_";
    private static final StructType BOUNDS_TYPE = new StructType()
            .add("xy", new GeometryUDT(), false)
            .add("minZ", DataTypes.DoubleType, false)
            .add("maxZ", DataTypes.DoubleType, false);
    private static final StructType GEODESIC_MATCH_TYPE = new StructType()
            .add("distance", DataTypes.DoubleType, false)
            .add("lowerBound", DataTypes.DoubleType, false)
            .add("upperBound", DataTypes.DoubleType, false)
            .add("witnesses", new GeometryUDT(), false)
            .add("provenZero", DataTypes.BooleanType, false)
            .add("exactDistance", DataTypes.BooleanType, false);
    private NearestExactPlan() { }

    static void validate(SpatialNearestConfiguration c, SparkCanvasTable source, SparkCanvasTable candidate,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputs, Map<String, SparkCanvasTable> inputs, CanvasNodeIssueSink issues) {
        if (!c.usesExactMatching()) return;
        String id = c.matching().sourceIdColumnName();
        var column = CanvasNodeSupport.blank(id) ? null : CanvasNodeSupport.columns(source.schema()).get(id);
        CanvasNodeSupport.required(id, "请选择来源唯一区分字段；重复 Geometry 仍须有独立身份", "configuration.matching.sourceIdColumnName", issues);
        if (!CanvasNodeSupport.blank(id) && column == null) issues.error("COLUMN_NOT_FOUND", "来源身份字段不在入口表中", "configuration.matching.sourceIdColumnName");
        else if (column != null && column.fieldType() == PlatformDataType.GEOMETRY)
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "来源身份字段不能是 Geometry", "configuration.matching.sourceIdColumnName");
        if (c.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            validateGeodesicKind(c, source, c.sourceGeometryColumnName(), "configuration.sourceGeometryColumnName", issues);
            validateGeodesicKind(c, candidate, c.candidateGeometryColumnName(), "configuration.candidateGeometryColumnName", issues);
        }
        if (c.maximumDistance() == null) issues.warning("SPATIAL_NEAREST_UNBOUNDED_SEARCH",
                "全范围搜索使用 KNN 上界及半径候选恢复；大量同距候选可能产生很大的中间结果，建议配置业务半径", "configuration.maximumDistance");
        if (!c.outputsConnectionLines()) return;
        var lines = c.matching().connectionLines();
        String path = "configuration.matching.connectionLines";
        CanvasNodeSupport.required(lines.outputTableName(), "请输入独立连接线表名", path + ".outputTableName", issues);
        CanvasNodeSupport.required(lines.geometryColumnName(), "请输入连接线 Geometry 字段名", path + ".geometryColumnName", issues);
        if (!CanvasNodeSupport.blank(lines.outputTableName()) && (inputs.containsKey(lines.outputTableName()) || lines.outputTableName().equals(c.outputTableName())))
            issues.error("DUPLICATE_TABLE_NAME", "连接线表名与入口表或匹配表冲突", path + ".outputTableName");
        Set<String> names = new HashSet<>();
        outputs.forEach(output -> names.add(output.outputColumnName().toLowerCase(Locale.ROOT)));
        if (c.distanceColumnName() != null) names.add(c.distanceColumnName().toLowerCase(Locale.ROOT));
        if (c.rankColumnName() != null) names.add(c.rankColumnName().toLowerCase(Locale.ROOT));
        if (!CanvasNodeSupport.blank(lines.geometryColumnName()) && names.contains(lines.geometryColumnName().toLowerCase(Locale.ROOT)))
            issues.error("DUPLICATE_COLUMN_NAME", "连接线字段与匹配结果字段冲突", path + ".geometryColumnName");
        if (c.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            double step = lineStep(c);
            if (!Double.isFinite(step) || step <= 0)
                issues.error("INVALID_SPATIAL_NEAREST_LINE_SEGMENT_LENGTH", "测地连接线最大段长必须为有限正数和明确线性单位", path + ".maximumGeodesicSegmentLength");
        }
    }

    private static void validateGeodesicKind(SpatialNearestConfiguration configuration, SparkCanvasTable table,
            String name, String path, CanvasNodeIssueSink issues) {
        var column = CanvasNodeSupport.blank(name) ? null : CanvasNodeSupport.columns(table.schema()).get(name);
        if (column == null || column.geometry() == null) return;
        GeometryKind kind = column.geometry().kind();
        if (!configuration.matching().allowsGeodesicGeometry()
                && kind != GeometryKind.POINT && kind != GeometryKind.GEOMETRY) {
            issues.error("GEODESIC_NEAREST_REQUIRES_POINTS",
                    "当前配置使用兼容模式，只接受 Point；可改用非点 Geometry 真实最近位置模式", path);
        } else if (configuration.matching().allowsGeodesicGeometry()
                && kind == GeometryKind.GEOMETRYCOLLECTION) {
            issues.error("GEODESIC_DISTANCE_GEOMETRY_UNSUPPORTED",
                    "真实测地最近位置不支持 GeometryCollection", path);
        }
    }

    static CanvasNodeOperationResult apply(SpatialNearestConfiguration c, SparkCanvasTable source, SparkCanvasTable candidate,
            Map<String, SparkCanvasTable> inputs, List<JoinOutputColumnSupport.ResolvedOutputColumn> outputs,
            Double maximum, double outputFactor) {
        boolean geodesic = c.distanceMethod() == SpatialDistanceMethod.GEODESIC;
        Side left = prepare(source, c.matching().sourceIdColumnName(), c.sourceGeometryColumnName(), "l", geodesic,
                c.matching().effectiveGeodesicGeometryMode());
        Side right = prepare(candidate, c.candidateIdColumnName(), c.candidateGeometryColumnName(), "r", geodesic,
                c.matching().effectiveGeodesicGeometryMode());
        Dataset<Row> eligibleLeft = left.data().filter(col(left.geometry()).isNotNull());
        Dataset<Row> eligibleRight = right.data().filter(col(right.geometry()).isNotNull());
        Column match = geodesic ? geodesicMatch(col(left.geometry()), col(right.geometry())) : null;
        Column distance = geodesic ? match.getField("distance")
                : st_functions.ST_Distance(col(left.geometry()), col(right.geometry()));
        if (!geodesic) distance = functions.udf((UDF1<Double, Double>) NearestExactPlan::checkedDistance,
                DataTypes.DoubleType).apply(distance);
        Dataset<Row> pairs;
        if (geodesic) {
            pairs = geodesicCandidates(left, right, eligibleLeft, eligibleRight, c.nearestCount(), maximum, distance);
        } else if (maximum != null) {
            pairs = eligibleLeft.join(eligibleRight, st_predicates.ST_DWithin(col(left.geometry()), col(right.geometry()),
                    functions.lit(expandedRadius(maximum)), functions.lit(false)), "inner");
        } else {
            Dataset<Row> seeds = eligibleLeft.join(eligibleRight, st_predicates.ST_KNN(col(left.geometry()), col(right.geometry()),
                    functions.lit(c.nearestCount()), functions.lit(geodesic)), "inner");
            Dataset<Row> radii = seeds.select(col(left.id()).alias(RADIUS_ID), distance.alias(DISTANCE))
                    .groupBy(col(RADIUS_ID)).agg(functions.max(col(DISTANCE)).alias(RADIUS));
            Dataset<Row> searches = eligibleLeft.join(radii, col(left.id()).equalTo(col(RADIUS_ID)), "inner");
            Column expandedRadius = functions.udf((UDF1<Double, Double>) NearestExactPlan::expandedRadius, DataTypes.DoubleType).apply(col(RADIUS));
            pairs = searches.join(eligibleRight, st_predicates.ST_DWithin(col(left.geometry()), col(right.geometry()),
                    expandedRadius, functions.lit(geodesic)), "inner");
        }
        if (geodesic) {
            pairs = pairs.withColumn(GEODESIC_MATCH, match)
                    .withColumn(DISTANCE, col(GEODESIC_MATCH).getField("distance"))
                    .withColumn(GEODESIC_LOWER, col(GEODESIC_MATCH).getField("lowerBound"))
                    .withColumn(GEODESIC_UPPER, col(GEODESIC_MATCH).getField("upperBound"))
                    .withColumn(GEODESIC_EXACT, col(GEODESIC_MATCH).getField("exactDistance"));
        } else {
            pairs = pairs.withColumn(DISTANCE, distance);
        }
        if (maximum != null) pairs = pairs.filter(geodesic
                ? resolvedThreshold(maximum)
                : col(DISTANCE).leq(maximum));
        var order = Window.partitionBy(col(left.id())).orderBy(col(DISTANCE).asc(), col(right.id()).asc());
        Dataset<Row> ranked = pairs.withColumn(RANK, functions.row_number().over(order));
        if (geodesic) {
            var following = order.rowsBetween(1, Window.unboundedFollowing());
            ranked = ranked.withColumn(FOLLOWING_LOWER, functions.min(col(GEODESIC_LOWER)).over(following))
                    .withColumn(FOLLOWING_APPROXIMATE_LOWER, functions.min(functions.when(
                            functions.not(col(GEODESIC_EXACT)), col(GEODESIC_LOWER))).over(following))
                    .withColumn(VERIFIED_RANK, verifiedRank());
        }
        ranked = ranked.filter(col(RANK).leq(c.nearestCount()));
        List<Column> matchesProjection = new ArrayList<>();
        Column matchId = geodesic
                ? functions.when(col(VERIFIED_RANK).gt(0), col(left.id()))
                : col(left.id());
        matchesProjection.add(matchId.alias(MATCH_ID));
        right.names().values().forEach(name -> matchesProjection.add(col(name)));
        matchesProjection.add(col(right.geometry()));
        matchesProjection.add(col(DISTANCE));
        matchesProjection.add(geodesic ? col(VERIFIED_RANK).alias(RANK) : col(RANK));
        if (geodesic) matchesProjection.add(col(GEODESIC_MATCH).getField("witnesses").alias(GEODESIC_WITNESSES));
        Dataset<Row> matches = ranked.select(matchesProjection.toArray(Column[]::new));
        // Both output tables project this one relation; they do not perform independent nearest searches.
        Dataset<Row> joined = left.data().join(matches, col(left.id()).equalTo(col(MATCH_ID)), c.includeUnmatched() ? "left_outer" : "inner");
        List<Column> projection = new ArrayList<>();
        List<CanvasColumnSchema> columns = new ArrayList<>();
        for (var output : outputs) {
            Side side = output.sourceSide() == JoinOutputColumnSource.LEFT ? left : right;
            projection.add(col(side.names().get(output.sourceColumn().name())).alias(output.outputColumnName()));
            var schema = JoinOutputColumnSupport.copyWithName(output.sourceColumn(), output.outputColumnName());
            columns.add(c.includeUnmatched() && output.sourceSide() == JoinOutputColumnSource.RIGHT ? SpatialNearestNodeOperator.nullable(schema) : schema);
        }
        Column convertedDistance = functions.udf((UDF1<Double, java.math.BigDecimal>) value -> {
            if (value == null) return null;
            double converted = checkedDistance(value / outputFactor);
            java.math.BigDecimal decimal = java.math.BigDecimal.valueOf(converted).setScale(12, java.math.RoundingMode.HALF_UP);
            if (decimal.precision() > 38) throw new IllegalArgumentException("SPATIAL_NEAREST_DISTANCE_INVALID");
            return decimal;
        }, DataTypes.createDecimalType(38, 12)).apply(col(DISTANCE));
        projection.add(convertedDistance.alias(c.distanceColumnName()));
        columns.add(SpatialNearestNodeOperator.decimalColumn(c.distanceColumnName(), c.includeUnmatched()));
        if (!CanvasNodeSupport.blank(c.rankColumnName())) {
            projection.add(col(RANK).alias(c.rankColumnName()));
            columns.add(SpatialNearestNodeOperator.integerColumn(c.rankColumnName(), c.includeUnmatched()));
        }
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        CanvasTableSchema schema = new CanvasTableSchema(c.outputTableName(), null, columns, CanvasDatasetKind.BOUNDED, null, null);
        output.put(schema.name(), new SparkCanvasTable(schema, joined.select(projection.toArray(Column[]::new))));
        if (c.outputsConnectionLines()) {
            var lines = c.matching().connectionLines();
            double step = lineStep(c);
            Column geometry = geodesic
                    ? functions.udf((UDF1<Geometry, Geometry>) witnesses -> NearestGeometrySupport.geodesicConnection(witnesses, step),
                            source.dataset().schema().apply(c.sourceGeometryColumnName()).dataType()).apply(col(GEODESIC_WITNESSES))
                    : functions.udf((UDF2<Geometry, Geometry, Geometry>) (a, b) -> NearestGeometrySupport.connection(a, b, false, step),
                            source.dataset().schema().apply(c.sourceGeometryColumnName()).dataType()).apply(col(left.geometry()), col(right.geometry()));
            List<Column> lineProjection = new ArrayList<>(projection);
            lineProjection.add(geometry.alias(lines.geometryColumnName()));
            List<CanvasColumnSchema> lineColumns = new ArrayList<>(columns);
            var inputGeometry = CanvasNodeSupport.columns(source.schema()).get(c.sourceGeometryColumnName()).geometry();
            lineColumns.add(new CanvasColumnSchema(lines.geometryColumnName(), PlatformDataType.GEOMETRY, null, null, null, false, null,
                    false, false, null, new GeometryTypeDefinition(GeometryKind.MULTILINESTRING, inputGeometry.crs(), CoordinateDimension.XY)));
            var lineSchema = new CanvasTableSchema(lines.outputTableName(), null, lineColumns, CanvasDatasetKind.BOUNDED, null, null);
            output.put(lineSchema.name(), new SparkCanvasTable(lineSchema, joined.filter(col(MATCH_ID).isNotNull()).select(lineProjection.toArray(Column[]::new))));
        }
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    /**
     * A planar ECEF projection is only a conservative recall index. If two WGS84 points are
     * within R metres, their chord and every Cartesian component gap are also within R.
     * Therefore XY envelope distance plus the independent Z interval cannot exclude a true
     * candidate. Final filtering and ordering use the WGS84 match Struct, never this envelope.
     */
    private static Dataset<Row> geodesicCandidates(Side left, Side right, Dataset<Row> eligibleLeft,
            Dataset<Row> eligibleRight, int nearestCount, Double maximum, Column distance) {
        if (maximum != null) return recall(left, right, eligibleLeft, eligibleRight, functions.lit(expandedRadius(maximum)));

        Dataset<Row> seeds = eligibleLeft.join(eligibleRight,
                st_predicates.ST_KNN(boundXy(left), boundXy(right), functions.lit(nearestCount), functions.lit(false)), "inner");
        Dataset<Row> radii = seeds.select(col(left.id()).alias(RADIUS_ID), distance.alias(DISTANCE))
                .groupBy(col(RADIUS_ID)).agg(functions.max(col(DISTANCE)).alias(RADIUS));
        Dataset<Row> searches = eligibleLeft.join(radii, col(left.id()).equalTo(col(RADIUS_ID)), "inner");
        Column radius = functions.udf((UDF1<Double, Double>) NearestExactPlan::expandedRadius, DataTypes.DoubleType)
                .apply(col(RADIUS));
        return recall(left, right, searches, eligibleRight, radius);
    }

    private static Dataset<Row> recall(Side left, Side right, Dataset<Row> source, Dataset<Row> candidates, Column radius) {
        Column xy = st_predicates.ST_DWithin(boundXy(left), boundXy(right), radius, functions.lit(false));
        Column z = bound(left, "minZ").leq(bound(right, "maxZ").plus(radius))
                .and(bound(right, "minZ").leq(bound(left, "maxZ").plus(radius)));
        return source.join(candidates, xy.and(z), "inner");
    }

    private static Column boundXy(Side side) { return bound(side, "xy"); }
    private static Column bound(Side side, String field) { return col(side.bounds()).getField(field); }

    private static Column geodesicMatch(Column source, Column candidate) {
        return functions.udf((UDF2<Geometry, Geometry, Row>) (left, right) -> {
            var value = Wgs84NearestMatch.solve(left, right);
            return value == null ? null : RowFactory.create(value.distanceMetres(), value.lowerBoundMetres(),
                    value.upperBoundMetres(), value.witnesses(), value.provenZero(), value.exactDistance());
        }, GEODESIC_MATCH_TYPE).apply(source, candidate);
    }

    private static Column resolvedThreshold(double maximum) {
        Column unresolved = functions.raise_error(functions.lit("GEODESIC_DISTANCE_PRECISION_NOT_REACHED"))
                .cast(DataTypes.BooleanType);
        return functions.when(col(GEODESIC_UPPER).leq(maximum), functions.lit(true))
                .when(col(GEODESIC_LOWER).gt(maximum), functions.lit(false))
                .otherwise(unresolved);
    }

    /**
     * Only projected rows in the selected prefix evaluate this guard. An exact point distance
     * may tie with later exact points and use the configured ID. Every approximate interval
     * that could precede the current row must be strictly beyond its upper bound.
     */
    private static Column verifiedRank() {
        Column requiredLower = functions.when(col(GEODESIC_EXACT), col(FOLLOWING_APPROXIMATE_LOWER))
                .otherwise(col(FOLLOWING_LOWER));
        Column resolved = requiredLower.isNull().or(col(GEODESIC_UPPER).lt(requiredLower));
        Column unresolved = functions.raise_error(functions.lit("GEODESIC_DISTANCE_PRECISION_NOT_REACHED"))
                .cast(DataTypes.IntegerType);
        return functions.when(resolved, col(RANK)).otherwise(unresolved);
    }

    private static Side prepare(SparkCanvasTable table, String id, String geometry, String side, boolean geodesic,
            SpatialNearestGeodesicGeometryMode geodesicGeometryMode) {
        Map<String, String> names = new LinkedHashMap<>();
        List<Column> projection = new ArrayList<>();
        for (var column : table.schema().columns()) {
            String name = "__nearest_" + side + "_" + names.size();
            names.put(column.name(), name);
            projection.add(table.dataset().col(CanvasNodeSupport.quoteIdentifier(column.name())).alias(name));
        }
        String rowId = names.get(id), shape = names.get(geometry);
        Dataset<Row> data = table.dataset().select(projection.toArray(Column[]::new));
        Column validId = col(rowId).isNotNull().and(functions.count(functions.lit(1)).over(Window.partitionBy(col(rowId))).equalTo(1));
        data = data.withColumn(rowId, functions.when(validId, col(rowId)).otherwise(functions.raise_error(functions.lit(
                side.equals("l") ? "SPATIAL_NEAREST_SOURCE_ID_INVALID" : "SPATIAL_NEAREST_CANDIDATE_ID_INVALID"))))
                .filter(col(rowId).isNotNull());
        Column checked = functions.udf((UDF1<Geometry, Geometry>) input ->
                        NearestGeometrySupport.checked(input, geodesic, geodesicGeometryMode),
                table.dataset().schema().apply(geometry).dataType()).apply(col(shape));
        // Search eligibility must not turn a projected source EMPTY Geometry into NULL.
        String checkedShape = "__nearest_checked_" + side;
        data = data.withColumn(checkedShape, checked);
        String bounds = BOUNDS_PREFIX + side;
        if (geodesic) {
            Column bound = functions.udf((UDF1<Geometry, Row>) input -> {
                var value = Wgs84GeometryBounds.of(input);
                return value == null ? null : RowFactory.create(value.xyEnvelope(), value.minZ(), value.maxZ());
            }, BOUNDS_TYPE).apply(col(checkedShape));
            data = data.withColumn(bounds, bound);
        }
        return new Side(data, names, rowId, checkedShape, geodesic ? bounds : null);
    }

    private static double lineStep(SpatialNearestConfiguration c) {
        if (c.matching() == null || c.matching().connectionLines() == null || c.matching().connectionLines().maximumGeodesicSegmentLength() == null) return Double.NaN;
        var lines = c.matching().connectionLines();
        return lines.maximumGeodesicSegmentLength() * SpatialDistanceSupport.metresPerConfiguredUnit(lines.maximumGeodesicSegmentLengthUnit());
    }
    private static Double expandedRadius(Double radius) { return radius == null || radius == Double.MAX_VALUE ? radius : Math.nextUp(radius); }
    private static Double checkedDistance(Double distance) {
        if (distance == null || !Double.isFinite(distance) || distance < 0)
            throw new IllegalArgumentException("SPATIAL_NEAREST_DISTANCE_INVALID");
        return distance;
    }
    private static Column col(String name) { return functions.col(CanvasNodeSupport.quoteIdentifier(name)); }
    private record Side(Dataset<Row> data, Map<String, String> names, String id, String geometry, String bounds) { }
}
