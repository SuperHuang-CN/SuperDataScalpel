package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialOverlayConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialOverlayGeometryPolicy;
import cn.superhuang.data.scalpel.contract.task.SpatialOverlayOperation;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

/** Layer-family rules and lazy geometry expressions shared by the five overlay plans. */
final class SpatialOverlayGeometrySupport {
    private SpatialOverlayGeometrySupport() { }

    static int family(GeometryKind kind) {
        return switch (kind) {
            case POINT, MULTIPOINT -> 1;
            case LINESTRING, MULTILINESTRING -> 2;
            case POLYGON, MULTIPOLYGON -> 3;
            default -> 0;
        };
    }

    static boolean supports(SpatialOverlayOperation operation, int left, int right) {
        if (operation == null || left == 0 || right == 0) return false;
        return switch (operation) {
            case INTERSECTION -> true;
            case ERASE, SYMMETRICAL_DIFFERENCE -> left == right;
            case UNION -> left == 3 && right == 3;
            case IDENTITY -> left == right || right == 3;
        };
    }

    static void validate(SpatialOverlayConfiguration configuration, GeometryTypeDefinition left,
            GeometryTypeDefinition right, CanvasNodeIssueSink issues) {
        if (!configuration.usesFamilyGeometry() || configuration.operation() == null) return;
        if (configuration.geometryPolicy() == SpatialOverlayGeometryPolicy.LEGACY_GEOMETRY) {
            issues.error("SPATIAL_OVERLAY_GEOMETRY_POLICY_UNSUPPORTED",
                    "标识和对称差必须使用图层家族二维输出", "configuration.geometryPolicy");
        }
        if (!supports(configuration.operation(), family(left.kind()), family(right.kind()))) {
            issues.error("SPATIAL_OVERLAY_GEOMETRY_COMBINATION_UNSUPPORTED",
                    "当前叠加方式不支持这组点/线/面家族；通用 Geometry 需先明确家族", "configuration.operation");
        }
        if (left.dimension() != CoordinateDimension.XY || right.dimension() != CoordinateDimension.XY) {
            issues.warning("SPATIAL_OVERLAY_OUTPUT_XY", "叠加结果为 XY，不保留来源的 Z/M 坐标", "configuration.geometryPolicy");
        }
    }

    static GeometryTypeDefinition outputType(SpatialOverlayConfiguration configuration,
            GeometryTypeDefinition left, GeometryTypeDefinition right) {
        if (!configuration.usesFamilyGeometry()) {
            return new GeometryTypeDefinition(GeometryKind.GEOMETRY, left.crs(), left.dimension());
        }
        int resultFamily = configuration.operation() == SpatialOverlayOperation.INTERSECTION
                ? Math.min(family(left.kind()), family(right.kind())) : family(left.kind());
        GeometryKind kind = switch (resultFamily) {
            case 1 -> GeometryKind.MULTIPOINT;
            case 2 -> GeometryKind.MULTILINESTRING;
            case 3 -> GeometryKind.MULTIPOLYGON;
            default -> throw new IllegalStateException("Overlay family validation must precede planning");
        };
        return new GeometryTypeDefinition(kind, left.crs(), CoordinateDimension.XY);
    }

    static Dataset<Row> prepare(Dataset<Row> source, String geometryColumn, boolean familyGeometry) {
        if (!familyGeometry) return source;
        Column geometry = source.col(CanvasNodeSupport.quoteIdentifier(geometryColumn));
        // This check runs only when the Dataset is consumed, never during compilation.
        Column checked = functions.when(geometry.isNull().or(st_functions.ST_IsEmpty(geometry)), geometry)
                .when(st_functions.ST_IsValid(geometry), geometry)
                .otherwise(functions.raise_error(functions.lit("SPATIAL_OVERLAY_INVALID_GEOMETRY")));
        Dataset<Row> prepared = source.withColumn(geometryColumn, st_functions.ST_Force2D(checked));
        Column value = prepared.col(CanvasNodeSupport.quoteIdentifier(geometryColumn));
        return prepared.filter(value.isNotNull().and(functions.not(st_functions.ST_IsEmpty(value))));
    }

    static Column result(Column geometry, GeometryTypeDefinition outputType) {
        if (outputType.kind() != GeometryKind.GEOMETRY) {
            // Keep only the declared family: polygon boundary contacts do not become line features.
            geometry = st_functions.ST_Multi(st_functions.ST_CollectionExtract(
                    st_functions.ST_Force2D(geometry), functions.lit(family(outputType.kind()))));
        }
        return st_functions.ST_SetSRID(geometry, functions.lit(outputType.crs().code()));
    }
}
