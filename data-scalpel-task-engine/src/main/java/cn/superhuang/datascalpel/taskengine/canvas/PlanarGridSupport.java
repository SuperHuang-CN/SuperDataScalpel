package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.SpatialBinShape;
import cn.superhuang.data.scalpel.contract.task.SpatialPlanarGridOptions;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import java.util.List;
import java.util.ArrayList;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Explicit projected grid alignment/scope. No data reads or driver-side grid materialization. */
final class PlanarGridSupport {
    static final long MAX_CANDIDATE_CELLS = 1_000_000;
    // Double grid arithmetic must still distinguish adjacent integral cell indices.
    private static final double MAX_INDEX = 1L << 50;
    private PlanarGridSupport() { }

    static Column geometry(Column binX, Column binY, SpatialBinShape shape, double size, int srid,
                                      SpatialPlanarGridOptions grid) {
        double originX = PlanarGridSupport.originX(grid), originY = PlanarGridSupport.originY(grid);
        if (shape == SpatialBinShape.SQUARE) {
            Column minX = binX.multiply(size).plus(originX);
            Column minY = binY.multiply(size).plus(originY);
            return st_functions.ST_SetSRID(st_constructors.ST_PolygonFromEnvelope(
                    minX, minY, minX.plus(size), minY.plus(size)), functions.lit(srid));
        }
        double height = Math.sqrt(3d) * size;
        Column centerX = binX.multiply(1.5d * size).plus(originX);
        Column centerY = binY.plus(binX.divide(2d)).multiply(height).plus(originY);
        List<Column> coordinates = new ArrayList<>();
        for (int index = 0; index <= 6; index++) {
            int vertex = index % 6;
            double angle = Math.toRadians(60d * vertex);
            coordinates.add(functions.concat(
                    centerX.plus(Math.cos(angle) * size).cast("string"), functions.lit(" "),
                    centerY.plus(Math.sin(angle) * size).cast("string")));
        }
        Column wkt = functions.concat(functions.lit("POLYGON(("),
                functions.concat_ws(",", coordinates.toArray(Column[]::new)), functions.lit("))"));
        return st_functions.ST_SetSRID(st_constructors.ST_GeomFromWKT(wkt), functions.lit(srid));
    }

    record Cell(Column q, Column r) { }
    static Cell pointIndices(Column x, Column y, double size, SpatialBinShape shape) {
        Column binX;
        Column binY;
        if (shape == SpatialBinShape.HEXAGON) {
            Column fractionalQ = x.multiply(2d / 3d).divide(size);
            Column fractionalR = y.multiply(Math.sqrt(3d) / 3d)
                    .minus(x.divide(3d)).divide(size);
            Column cubeX = fractionalQ;
            Column cubeZ = fractionalR;
            Column cubeY = cubeX.plus(cubeZ).multiply(-1d);
            Column roundedX = functions.round(cubeX);
            Column roundedY = functions.round(cubeY);
            Column roundedZ = functions.round(cubeZ);
            Column xDifference = functions.abs(roundedX.minus(cubeX));
            Column yDifference = functions.abs(roundedY.minus(cubeY));
            Column zDifference = functions.abs(roundedZ.minus(cubeZ));
            Column xIsLargest = xDifference.gt(yDifference).and(xDifference.gt(zDifference));
            Column yIsLargest = yDifference.gt(zDifference);
            binX = functions.when(xIsLargest, roundedY.plus(roundedZ).multiply(-1d))
                    .otherwise(roundedX).cast("long");
            binY = functions.when(xIsLargest.or(yIsLargest), roundedZ)
                    .otherwise(roundedX.plus(roundedY).multiply(-1d)).cast("long");
        } else {
            binX = functions.floor(x.divide(size)).cast("long");
            binY = functions.floor(y.divide(size)).cast("long");
        }
        return new Cell(binX, binY);
    }

    static void validate(SpatialPlanarGridOptions grid, CanvasNodeIssueSink issues) {
        if (grid == null) return;
        if (!finite(grid.originX()) || !finite(grid.originY()))
            issues.error("INVALID_SPATIAL_GRID_ORIGIN", "格网原点需要两个有限坐标值", "configuration.planarGrid");
        var extent = grid.extent();
        if (extent == null) return;
        if (extent.mode() == null)
            issues.error("INVALID_SPATIAL_GRID_EXTENT", "请选择格网范围方式", "configuration.planarGrid.extent.mode");
        if (grid.usesExplicitBounds() && (!finite(extent.minX()) || !finite(extent.minY())
                || !finite(extent.maxX()) || !finite(extent.maxY())
                || extent.minX() >= extent.maxX() || extent.minY() >= extent.maxY()))
            issues.error("INVALID_SPATIAL_GRID_EXTENT", "业务范围需要有限坐标且最小值严格小于最大值", "configuration.planarGrid.extent");
    }

    static Bounds bounds(SpatialPlanarGridOptions grid, SpatialBinShape shape, double side,
                         boolean generateEmpty, CanvasNodeIssueSink issues) {
        if (grid == null) return null;
        if (!Double.isFinite(grid.originX() + side) || grid.originX() + side == grid.originX()
                || !Double.isFinite(grid.originY() + side) || grid.originY() + side == grid.originY()) {
            issues.error("INVALID_SPATIAL_GRID_ORIGIN", "原点与格网大小的浮点精度不足以生成有效格网", "configuration.planarGrid");
            return null;
        }
        if (!grid.usesExplicitBounds()) return null;
        var e = grid.extent();
        double x0 = (e.minX() - grid.originX()) / side, x1 = (e.maxX() - grid.originX()) / side;
        double y0 = (e.minY() - grid.originY()) / side, y1 = (e.maxY() - grid.originY()) / side;
        double minQ, maxQ, minR, maxR;
        if (shape == SpatialBinShape.SQUARE) {
            minQ = Math.floor(x0); maxQ = Math.ceil(x1) - 1;
            minR = Math.floor(y0); maxR = Math.ceil(y1) - 1;
        } else {
            // Every intersecting flat-top hex center lies at most one side/half-height beyond the extent.
            // Cover the axial-coordinate parallelogram, then remove nonintersecting cells in the lazy plan.
            minQ = Math.floor((x0 - 1) / 1.5); maxQ = Math.ceil((x1 + 1) / 1.5);
            minR = Math.floor(y0 / Math.sqrt(3) - 0.5 - maxQ / 2);
            maxR = Math.ceil(y1 / Math.sqrt(3) + 0.5 - minQ / 2);
        }
        for (double v : new double[]{minQ, maxQ, minR, maxR}) if (!Double.isFinite(v) || Math.abs(v) > MAX_INDEX) {
            issues.error("INVALID_SPATIAL_GRID_EXTENT", "范围与格网大小组合超出可靠索引范围", "configuration.planarGrid.extent");
            return null;
        }
        Bounds result = new Bounds((long) minQ, (long) maxQ, (long) minR, (long) maxR);
        if (maxQ < minQ || maxR < minR) {
            issues.error("INVALID_SPATIAL_GRID_EXTENT", "范围与大小的浮点精度不足以确定格网", "configuration.planarGrid.extent");
            return null;
        }
        if (generateEmpty && (maxQ - minQ + 1) * (maxR - minR + 1) > MAX_CANDIDATE_CELLS) {
            issues.error("SPATIAL_GRID_CELL_LIMIT_EXCEEDED", "显式范围候选格网超过 100 万，请缩小范围或增大格网", "configuration.planarGrid.extent");
        }
        return result;
    }

    static Column extentGeometry(SpatialPlanarGridOptions grid) {
        var e = grid.extent();
        return st_constructors.ST_PolygonFromEnvelope(functions.lit(e.minX()), functions.lit(e.minY()),
                functions.lit(e.maxX()), functions.lit(e.maxY()));
    }

    static Column checkedCoordinate(Column relative, double side) {
        Column index = relative.divide(side);
        return functions.when(functions.isnan(index).or(functions.abs(index).gt(MAX_INDEX)),
                functions.raise_error(functions.lit("SPATIAL_GRID_POINT_INVALID")).cast("double")).otherwise(relative);
    }

    static String identity(SpatialPlanarGridOptions grid, SpatialBinShape shape, double side, int srid) {
        if (grid == null) return shape.name(); // Preserve legacy IDs byte-for-byte.
        String value = shape.name() + "|" + srid + "|" + Double.toHexString(side)
                + "|" + Double.toHexString(grid.originX() == 0 ? 0 : grid.originX())
                + "|" + Double.toHexString(grid.originY() == 0 ? 0 : grid.originY());
        try {
            return shape.name() + ":" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    static double originX(SpatialPlanarGridOptions grid) { return grid == null ? 0 : grid.originX(); }
    static double originY(SpatialPlanarGridOptions grid) { return grid == null ? 0 : grid.originY(); }
    private static boolean finite(Double value) { return value != null && Double.isFinite(value); }
    record Bounds(long minX, long maxX, long minY, long maxY) { }
}
