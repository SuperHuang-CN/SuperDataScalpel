package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinSpatialNearCondition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.api.java.UDF2;
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.locationtech.jts.geom.Geometry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Spatial Near is one condition with one distance source, separate from topology predicates. */
final class SpatialJoinNearSupport {
    private static final String PATH = "configuration.spatialNear";
    private static final StructType BOUNDS_TYPE = new StructType()
            .add("xy", new GeometryUDT(), false)
            .add("minZ", DataTypes.DoubleType, false)
            .add("maxZ", DataTypes.DoubleType, false);

    private SpatialJoinNearSupport() {
    }

    static void validate(
            SpatialJoinSpatialNearCondition condition,
            CanvasTableSchema left,
            CanvasTableSchema right,
            CanvasNodeIssueSink issues
    ) {
        if (condition == null) return;
        CanvasNodeSupport.required(condition.leftGeometryColumnName(),
                "请选择目标表 Geometry 字段", PATH + ".leftGeometryColumnName", issues);
        CanvasNodeSupport.required(condition.rightGeometryColumnName(),
                "请选择连接表 Geometry 字段", PATH + ".rightGeometryColumnName", issues);
        if (condition.distanceMethod() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择空间邻近距离方法",
                    PATH + ".distanceMethod");
        }
        if (condition.distance() == null || !Double.isFinite(condition.distance())
                || condition.distance() <= 0) {
            issues.error("INVALID_SPATIAL_JOIN_NEAR_DISTANCE", "空间邻近距离必须是有限正数",
                    PATH + ".distance");
        }
        if (condition.distanceUnit() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择空间邻近距离单位",
                    PATH + ".distanceUnit");
        }
        Map<String, CanvasColumnSchema> leftColumns = CanvasNodeSupport.columns(left);
        Map<String, CanvasColumnSchema> rightColumns = CanvasNodeSupport.columns(right);
        CanvasColumnSchema leftColumn = geometry(leftColumns, condition.leftGeometryColumnName(),
                "目标表", PATH + ".leftGeometryColumnName", issues);
        CanvasColumnSchema rightColumn = geometry(rightColumns, condition.rightGeometryColumnName(),
                "连接表", PATH + ".rightGeometryColumnName", issues);
        if (leftColumn == null || rightColumn == null
                || leftColumn.geometry() == null || rightColumn.geometry() == null) return;
        GeometryTypeDefinition leftGeometry = leftColumn.geometry();
        GeometryTypeDefinition rightGeometry = rightColumn.geometry();
        if (!leftGeometry.crs().equals(rightGeometry.crs())) {
            issues.error("SPATIAL_CRS_MISMATCH", "空间邻近两侧 CRS 不一致，请先使用空间转换节点",
                    PATH + ".rightGeometryColumnName");
        }
        if (leftGeometry.dimension() != rightGeometry.dimension()) {
            issues.error("SPATIAL_DIMENSION_MISMATCH", "空间邻近两侧坐标维度不一致",
                    PATH + ".rightGeometryColumnName");
        }
        if (leftGeometry.dimension() != CoordinateDimension.XY
                || rightGeometry.dimension() != CoordinateDimension.XY) {
            issues.error("UNSUPPORTED_GEOMETRY_DIMENSION", "空间邻近只支持 XY 维度", PATH);
        }
        if (condition.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            if (!wgs84(leftGeometry) || !wgs84(rightGeometry)) {
                issues.error("GEODESIC_DISTANCE_REQUIRES_WGS84",
                        "Near Geodesic 仅支持 EPSG:4326 XY", PATH + ".distanceMethod");
            }
            if (leftGeometry.kind() == GeometryKind.GEOMETRYCOLLECTION
                    || rightGeometry.kind() == GeometryKind.GEOMETRYCOLLECTION) {
                issues.error("GEODESIC_DISTANCE_GEOMETRY_UNSUPPORTED",
                        "Near Geodesic 不支持 GeometryCollection", PATH);
            }
            if (condition.distanceUnit() == SpatialDistanceUnit.SOURCE_CRS_UNIT) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED",
                        "Near Geodesic 不能使用来源 CRS 单位", PATH + ".distanceUnit");
            } else if (condition.distance() != null && condition.distanceUnit() != null
                    && !Double.isFinite(condition.distance()
                    * SpatialDistanceSupport.metresPerConfiguredUnit(condition.distanceUnit()))) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED",
                        "空间邻近距离换算后不是有限数值", PATH + ".distanceUnit");
            }
        } else if (condition.distanceMethod() == SpatialDistanceMethod.PLANAR
                && condition.distance() != null && condition.distanceUnit() != null) {
            SpatialDistanceSupport.Resolution resolved = SpatialDistanceSupport.resolve(
                    condition.distance(), condition.distanceUnit(), leftGeometry.crs());
            if (!resolved.valid()) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", resolved.error(),
                        PATH + ".distanceUnit");
            } else if (resolved.angular()) {
                issues.warning("PLANAR_DISTANCE_USES_ANGULAR_UNITS",
                        "地理 CRS 的平面 Near 使用角度，结果随纬度变化",
                        PATH + ".distanceUnit");
            }
        }
    }

    static Column expression(
            SpatialJoinSpatialNearCondition condition,
            Dataset<Row> left,
            Dataset<Row> right,
            GeometryTypeDefinition geometry,
            PreparedGeodesicJoin prepared
    ) {
        Column leftGeometry = column(left, condition.leftGeometryColumnName());
        Column rightGeometry = column(right, condition.rightGeometryColumnName());
        double threshold = threshold(condition, geometry);
        if (condition.distanceMethod() == SpatialDistanceMethod.PLANAR) {
            return st_predicates.ST_DWithin(leftGeometry, rightGeometry,
                    functions.lit(threshold), functions.lit(false));
        }
        if (prepared == null) {
            throw new IllegalArgumentException("GEODESIC Spatial Join requires prepared candidates");
        }
        Column exact = functions.udf((UDF2<Geometry, Geometry, Boolean>)
                        (first, second) -> Wgs84GeometryDistance.withinDistance(
                                first, second, threshold),
                DataTypes.BooleanType).apply(
                prepared.left().col(CanvasNodeSupport.quoteIdentifier(prepared.leftChecked())),
                prepared.right().col(CanvasNodeSupport.quoteIdentifier(prepared.rightChecked())));
        return recall(prepared, expandedThreshold(threshold)).and(exact);
    }

    /**
     * Builds a conservative ECEF candidate index for Near Geodesic. The envelope join can
     * admit false positives, but it cannot exclude Geometry locations whose WGS84 distance
     * is within the configured threshold. The exact predicate above remains authoritative.
     */
    static PreparedGeodesicJoin prepareGeodesicJoin(
            SpatialJoinSpatialNearCondition condition,
            Dataset<Row> left,
            Dataset<Row> right,
            String leftAlias,
            String rightAlias
    ) {
        if (condition == null || condition.distanceMethod() != SpatialDistanceMethod.GEODESIC) {
            return null;
        }
        Set<String> names = new HashSet<>();
        names.addAll(List.of(left.columns()));
        names.addAll(List.of(right.columns()));
        String leftChecked = CanvasSortSupport.temporaryColumnName(
                names, "__datascalpel_spatial_join_near_left_geometry");
        names.add(leftChecked);
        String rightChecked = CanvasSortSupport.temporaryColumnName(
                names, "__datascalpel_spatial_join_near_right_geometry");
        names.add(rightChecked);
        String leftBounds = CanvasSortSupport.temporaryColumnName(
                names, "__datascalpel_spatial_join_near_left_bounds");
        names.add(leftBounds);
        String rightBounds = CanvasSortSupport.temporaryColumnName(
                names, "__datascalpel_spatial_join_near_right_bounds");

        Dataset<Row> preparedLeft = withGeodesicIndex(
                left, condition.leftGeometryColumnName(), leftChecked, leftBounds)
                .alias(leftAlias);
        Dataset<Row> preparedRight = withGeodesicIndex(
                right, condition.rightGeometryColumnName(), rightChecked, rightBounds)
                .alias(rightAlias);
        return new PreparedGeodesicJoin(
                preparedLeft, preparedRight,
                leftChecked, rightChecked, leftBounds, rightBounds,
                List.of(leftChecked, rightChecked, leftBounds, rightBounds));
    }

    static Dataset<Row> removeInternalColumns(
            Dataset<Row> joined,
            PreparedGeodesicJoin prepared
    ) {
        if (prepared == null) return joined;
        Dataset<Row> result = joined;
        for (String name : prepared.internalColumns()) result = result.drop(name);
        return result;
    }

    static Column outputDistance(
            SpatialJoinSpatialNearCondition condition,
            SpatialDistanceUnit outputUnit,
            Dataset<Row> left,
            Dataset<Row> right,
            GeometryTypeDefinition geometry
    ) {
        Column leftGeometry = column(left, condition.leftGeometryColumnName());
        Column rightGeometry = column(right, condition.rightGeometryColumnName());
        Column raw;
        double factor;
        if (condition.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            raw = functions.udf((UDF2<Geometry, Geometry, Double>) (first, second) -> {
                Geometry checkedFirst = checked(first);
                Geometry checkedSecond = checked(second);
                Wgs84NearestMatch.Match match = Wgs84NearestMatch.solve(checkedFirst, checkedSecond);
                if (match == null) return null;
                return match.distanceMetres();
            }, DataTypes.DoubleType).apply(leftGeometry, rightGeometry);
            factor = SpatialDistanceSupport.metresPerConfiguredUnit(outputUnit);
        } else {
            raw = st_functions.ST_Distance(leftGeometry, rightGeometry);
            factor = SpatialDistanceSupport.sourceUnitsPerConfiguredUnit(
                    outputUnit, geometry.crs()).sourceCrsValue();
        }
        return decimal(raw, factor, "SPATIAL_JOIN_DISTANCE_INVALID");
    }

    static Column decimal(Column raw, double unitFactor, String errorCode) {
        return functions.udf((UDF1<Double, BigDecimal>) value -> {
            if (value == null) return null;
            double converted = value / unitFactor;
            if (!Double.isFinite(converted) || converted < 0) {
                throw new IllegalArgumentException(errorCode);
            }
            BigDecimal decimal = BigDecimal.valueOf(converted).setScale(12, RoundingMode.HALF_UP);
            if (decimal.precision() > 38) throw new IllegalArgumentException(errorCode);
            return decimal;
        }, DataTypes.createDecimalType(38, 12)).apply(raw);
    }

    private static CanvasColumnSchema geometry(
            Map<String, CanvasColumnSchema> columns,
            String name,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(name)) return null;
        CanvasColumnSchema column = columns.get(name);
        if (column == null) {
            issues.error("COLUMN_NOT_FOUND", label + " Geometry 字段不存在：" + name, path);
        } else if (column.fieldType() != PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", label + "字段不是 Geometry：" + name, path);
        } else {
            CanvasNodeSupport.validateSupportedGeometry(List.of(column), path, issues);
        }
        return column;
    }

    private static boolean wgs84(GeometryTypeDefinition geometry) {
        return "EPSG".equals(geometry.crs().authority()) && geometry.crs().code() == 4326
                && geometry.dimension() == CoordinateDimension.XY;
    }

    private static Dataset<Row> withGeodesicIndex(
            Dataset<Row> dataset,
            String geometryColumnName,
            String checkedColumnName,
            String boundsColumnName
    ) {
        Column source = column(dataset, geometryColumnName);
        Column checkedGeometry = functions.udf(
                (UDF1<Geometry, Geometry>) SpatialJoinNearSupport::checked,
                dataset.schema().apply(geometryColumnName).dataType()).apply(source);
        Dataset<Row> checked = dataset.withColumn(checkedColumnName, checkedGeometry);
        Column bounds = functions.udf((UDF1<Geometry, Row>) input -> {
            Wgs84GeometryBounds.Bounds value = Wgs84GeometryBounds.of(input);
            return value == null ? null : RowFactory.create(
                    value.xyEnvelope(), value.minZ(), value.maxZ());
        }, BOUNDS_TYPE).apply(
                checked.col(CanvasNodeSupport.quoteIdentifier(checkedColumnName)));
        return checked.withColumn(boundsColumnName, bounds);
    }

    private static Column recall(PreparedGeodesicJoin prepared, double threshold) {
        Column leftBounds = prepared.left().col(
                CanvasNodeSupport.quoteIdentifier(prepared.leftBounds()));
        Column rightBounds = prepared.right().col(
                CanvasNodeSupport.quoteIdentifier(prepared.rightBounds()));
        Column xy = st_predicates.ST_DWithin(
                leftBounds.getField("xy"), rightBounds.getField("xy"),
                functions.lit(threshold), functions.lit(false));
        Column z = leftBounds.getField("minZ")
                .leq(rightBounds.getField("maxZ").plus(threshold))
                .and(rightBounds.getField("minZ")
                        .leq(leftBounds.getField("maxZ").plus(threshold)));
        return xy.and(z);
    }

    private static double expandedThreshold(double threshold) {
        return threshold == Double.MAX_VALUE ? threshold : Math.nextUp(threshold);
    }

    private static Geometry checked(Geometry geometry) {
        try {
            return NearestGeometrySupport.checked(
                    geometry, true,
                    cn.superhuang.data.scalpel.contract.task.SpatialNearestGeodesicGeometryMode.GEOMETRY);
        } catch (IllegalArgumentException failure) {
            if ("SPATIAL_NEAREST_GEOMETRY_INVALID".equals(failure.getMessage())) {
                throw new IllegalArgumentException("SPATIAL_JOIN_GEOMETRY_INVALID");
            }
            throw failure;
        }
    }

    private static double threshold(
            SpatialJoinSpatialNearCondition condition,
            GeometryTypeDefinition geometry
    ) {
        if (condition.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            return condition.distance()
                    * SpatialDistanceSupport.metresPerConfiguredUnit(condition.distanceUnit());
        }
        return SpatialDistanceSupport.resolve(
                condition.distance(), condition.distanceUnit(), geometry.crs()).sourceCrsValue();
    }

    private static Column column(Dataset<Row> dataset, String name) {
        return dataset.col(CanvasNodeSupport.quoteIdentifier(name));
    }

    record PreparedGeodesicJoin(
            Dataset<Row> left,
            Dataset<Row> right,
            String leftChecked,
            String rightChecked,
            String leftBounds,
            String rightBounds,
            List<String> internalColumns
    ) {
        PreparedGeodesicJoin {
            internalColumns = List.copyOf(internalColumns);
        }
    }
}
