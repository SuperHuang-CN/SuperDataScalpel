package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityAttributeCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityAttributeRelationship;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximitySpatialRelationship;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityTemporalCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityTemporalRelationship;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityTemporalUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinSpatialNearCondition;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;
import org.apache.spark.sql.types.DataTypes;
import org.graphframes.GraphFrame;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Runtime graph plan for Group By Proximity. Compiler preview never enters this class. */
final class SpatialGroupByProximitySupport {
    private static final String LEFT_ALIAS = "group_proximity_left";
    private static final String RIGHT_ALIAS = "group_proximity_right";

    private SpatialGroupByProximitySupport() {
    }

    static Dataset<Row> schemaPlan(
            Dataset<Row> source,
            SpatialGroupByProximityConfiguration configuration
    ) {
        List<Column> dependencies = new ArrayList<>();
        dependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(
                configuration.geometryColumnName())));
        SpatialGroupByProximityTemporalCondition temporal = configuration.temporalCondition();
        if (temporal != null) {
            dependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(
                    temporal.startColumnName())));
            if (!CanvasNodeSupport.blank(temporal.endColumnName())) {
                dependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(
                        temporal.endColumnName())));
            }
        }
        for (SpatialGroupByProximityAttributeCondition condition
                : configuration.attributeConditions()) {
            dependencies.add(source.col(CanvasNodeSupport.quoteIdentifier(
                    condition.columnName())));
        }
        // Compiler inputs are zero-row tables. This deliberately non-executable expression
        // exposes the collective fields that determine connected-component membership without
        // running checkpoints, the self join, or GraphFrames during preview.
        Column members = functions.collect_list(functions.struct(
                dependencies.toArray(Column[]::new))).over(Window.partitionBy());
        Column group = functions.udf((UDF1<scala.collection.Seq<Row>, Long>) ignored -> {
            throw new IllegalArgumentException("SPATIAL_GROUP_PREVIEW_NOT_EXECUTABLE");
        }, DataTypes.LongType).apply(members);
        return source.withColumn(configuration.groupIdColumnName(), group);
    }

    static Dataset<Row> run(
            Dataset<Row> source,
            SpatialGroupByProximityConfiguration configuration,
            GeometryTypeDefinition geometryType
    ) {
        Set<String> names = new HashSet<>(List.of(source.columns()));
        String identity = CanvasSortSupport.temporaryColumnName(
                names, "__datascalpel_group_proximity_identity");
        names.add(identity);
        String temporalGuard = CanvasSortSupport.temporaryColumnName(
                names, "__datascalpel_group_proximity_temporal_valid");

        Column sourceGeometry = source.col(
                CanvasNodeSupport.quoteIdentifier(configuration.geometryColumnName()));
        boolean geodesic = configuration.spatialRelationship()
                == SpatialGroupByProximitySpatialRelationship.NEAR_GEODESIC;
        GeometryKind expectedKind = geometryType.kind();
        Column checkedGeometry = functions.udf(
                (UDF1<Geometry, Geometry>) geometry -> checked(
                        geometry, expectedKind, geodesic),
                source.schema().apply(configuration.geometryColumnName()).dataType()
        ).apply(sourceGeometry);
        Dataset<Row> prepared = source.withColumn(
                configuration.geometryColumnName(), checkedGeometry);
        if (configuration.temporalCondition() != null
                && !CanvasNodeSupport.blank(configuration.temporalCondition().endColumnName())) {
            SpatialGroupByProximityTemporalCondition time = configuration.temporalCondition();
            Column start = prepared.col(CanvasNodeSupport.quoteIdentifier(time.startColumnName()));
            Column end = prepared.col(CanvasNodeSupport.quoteIdentifier(time.endColumnName()));
            Column valid = start.isNull().or(end.isNull()).or(start.leq(end));
            prepared = prepared.withColumn(temporalGuard, functions.when(valid, functions.lit(true))
                            .otherwise(functions.raise_error(functions.lit(
                                    "SPATIAL_GROUP_TEMPORAL_INTERVAL_INVALID")).cast("boolean")))
                    .filter(functions.col(CanvasNodeSupport.quoteIdentifier(temporalGuard)))
                    .drop(temporalGuard);
        }
        prepared = prepared.withColumn(identity, functions.monotonically_increasing_id()).checkpoint();

        Dataset<Row> vertices = prepared.select(
                prepared.col(CanvasNodeSupport.quoteIdentifier(identity)).alias("id"));
        Dataset<Row> left = detachedAlias(prepared, LEFT_ALIAS);
        Dataset<Row> right = detachedAlias(prepared, RIGHT_ALIAS);
        SpatialJoinNearSupport.PreparedGeodesicJoin geodesicJoin = null;
        if (geodesic) {
            geodesicJoin = SpatialJoinNearSupport.prepareGeodesicJoin(
                    SpatialGroupByProximityNodeOperator.nearCondition(configuration),
                    left, right, LEFT_ALIAS, RIGHT_ALIAS);
            left = geodesicJoin.left();
            right = geodesicJoin.right();
        }

        Column leftId = column(LEFT_ALIAS, identity);
        Column rightId = column(RIGHT_ALIAS, identity);
        Column relationship = spatialRelationship(
                configuration, geometryType, left, right, geodesicJoin);
        if (configuration.temporalCondition() != null) {
            relationship = relationship.and(temporalRelationship(
                    configuration.temporalCondition(), left, right));
        }
        for (SpatialGroupByProximityAttributeCondition condition
                : configuration.attributeConditions()) {
            relationship = relationship.and(attributeRelationship(condition, left, right));
        }
        Dataset<Row> edges = left.join(
                        right, leftId.lt(rightId).and(relationship), "inner")
                .select(leftId.alias("src"), rightId.alias("dst"));

        Dataset<Row> components = GraphFrame.apply(vertices, edges).connectedComponents().run();
        try {
            Dataset<Row> membership = components.select(
                    functions.col("id").alias(identity),
                    functions.col("component").cast("long").alias(configuration.groupIdColumnName()));
            Dataset<Row> joined = prepared.join(membership, new String[]{identity}, "inner");
            List<Column> projection = new ArrayList<>(source.columns().length + 1);
            for (String column : source.columns()) {
                projection.add(joined.col(CanvasNodeSupport.quoteIdentifier(column)));
            }
            projection.add(joined.col(CanvasNodeSupport.quoteIdentifier(
                    configuration.groupIdColumnName())));
            // Bind the GraphFrames result before releasing its persisted connected-components plan.
            return joined.select(projection.toArray(Column[]::new)).checkpoint();
        } finally {
            components.unpersist(false);
        }
    }

    private static Column spatialRelationship(
            SpatialGroupByProximityConfiguration configuration,
            GeometryTypeDefinition geometryType,
            Dataset<Row> left,
            Dataset<Row> right,
            SpatialJoinNearSupport.PreparedGeodesicJoin geodesicJoin
    ) {
        Column leftGeometry = column(LEFT_ALIAS, configuration.geometryColumnName());
        Column rightGeometry = column(RIGHT_ALIAS, configuration.geometryColumnName());
        return switch (configuration.spatialRelationship()) {
            case INTERSECTS -> st_predicates.ST_Intersects(leftGeometry, rightGeometry);
            case TOUCHES -> st_predicates.ST_Touches(leftGeometry, rightGeometry);
            case NEAR_PLANAR, NEAR_GEODESIC -> {
                SpatialJoinSpatialNearCondition near =
                        SpatialGroupByProximityNodeOperator.nearCondition(configuration);
                yield SpatialJoinNearSupport.expression(
                        near, left, right, geometryType, geodesicJoin);
            }
        };
    }

    private static Column temporalRelationship(
            SpatialGroupByProximityTemporalCondition condition,
            Dataset<Row> left,
            Dataset<Row> right
    ) {
        Column leftStart = column(LEFT_ALIAS, condition.startColumnName());
        Column leftEnd = CanvasNodeSupport.blank(condition.endColumnName())
                ? leftStart : column(LEFT_ALIAS, condition.endColumnName());
        Column rightStart = column(RIGHT_ALIAS, condition.startColumnName());
        Column rightEnd = CanvasNodeSupport.blank(condition.endColumnName())
                ? rightStart : column(RIGHT_ALIAS, condition.endColumnName());
        Column complete = leftStart.isNotNull().and(leftEnd.isNotNull())
                .and(rightStart.isNotNull()).and(rightEnd.isNotNull());
        Column intersects = leftStart.leq(rightEnd).and(leftEnd.geq(rightStart));
        if (condition.relationship()
                == SpatialGroupByProximityTemporalRelationship.INTERSECTS) {
            return complete.and(intersects);
        }
        Column interval = temporalInterval(condition);
        Column leftBefore = leftEnd.leq(rightStart)
                .and(leftEnd.plus(interval).geq(rightStart));
        Column rightBefore = rightEnd.leq(leftStart)
                .and(rightEnd.plus(interval).geq(leftStart));
        return complete.and(intersects.or(leftBefore).or(rightBefore));
    }

    private static Column temporalInterval(SpatialGroupByProximityTemporalCondition condition) {
        long value = condition.nearDistance();
        return switch (condition.nearDistanceUnit()) {
            case MILLISECONDS -> fixedInterval(Math.multiplyExact(value, 1_000L));
            case SECONDS -> fixedInterval(Math.multiplyExact(value, 1_000_000L));
            case MINUTES -> fixedInterval(Math.multiplyExact(value, 60_000_000L));
            case HOURS -> fixedInterval(Math.multiplyExact(value, 3_600_000_000L));
            case DAYS -> fixedInterval(Math.multiplyExact(value, 86_400_000_000L));
            case WEEKS -> fixedInterval(Math.multiplyExact(value, 604_800_000_000L));
            case MONTHS -> calendarInterval(value, "MONTHS");
            case YEARS -> calendarInterval(value, "YEARS");
        };
    }

    private static Column fixedInterval(long micros) {
        return functions.expr("INTERVAL " + micros + " MICROSECONDS");
    }

    private static Column calendarInterval(long value, String unit) {
        return functions.expr("INTERVAL " + value + " " + unit);
    }

    private static Column attributeRelationship(
            SpatialGroupByProximityAttributeCondition condition,
            Dataset<Row> left,
            Dataset<Row> right
    ) {
        Column leftValue = column(LEFT_ALIAS, condition.columnName());
        Column rightValue = column(RIGHT_ALIAS, condition.columnName());
        if (condition.relationship() == SpatialGroupByProximityAttributeRelationship.EQUALS) {
            return leftValue.equalTo(rightValue);
        }
        return functions.abs(leftValue.minus(rightValue))
                .leq(functions.lit(condition.maximumDifference()));
    }

    private static Dataset<Row> detachedAlias(Dataset<Row> source, String alias) {
        Column[] projection = new Column[source.columns().length];
        for (int index = 0; index < source.columns().length; index++) {
            String name = source.columns()[index];
            projection[index] = source.col(CanvasNodeSupport.quoteIdentifier(name)).alias(name);
        }
        return source.select(projection).alias(alias);
    }

    private static Column column(String alias, String name) {
        return functions.col(alias + "." + CanvasNodeSupport.quoteIdentifier(name));
    }

    private static Geometry checked(
            Geometry geometry,
            GeometryKind expectedKind,
            boolean geodesic
    ) {
        if (geometry == null || geometry.isEmpty()) return geometry;
        if (!geometry.isValid() || !matchesFamily(geometry, expectedKind)) {
            throw new IllegalArgumentException("SPATIAL_GROUP_GEOMETRY_INVALID");
        }
        for (var coordinate : geometry.getCoordinates()) {
            if (!Double.isFinite(coordinate.getX()) || !Double.isFinite(coordinate.getY())
                    || geodesic && (Math.abs(coordinate.getX()) > 180d
                    || Math.abs(coordinate.getY()) > 90d)) {
                throw new IllegalArgumentException("SPATIAL_GROUP_GEOMETRY_INVALID");
            }
        }
        return geometry;
    }

    private static boolean matchesFamily(Geometry geometry, GeometryKind expectedKind) {
        return switch (expectedKind) {
            case POINT, MULTIPOINT -> geometry instanceof Point || geometry instanceof MultiPoint;
            case LINESTRING, MULTILINESTRING -> geometry instanceof LineString
                    || geometry instanceof MultiLineString;
            case POLYGON, MULTIPOLYGON -> geometry instanceof Polygon
                    || geometry instanceof MultiPolygon;
            default -> false;
        };
    }
}
