package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.*;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.*;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.api.java.UDF4;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;
import org.apache.spark.sql.types.DataTypes;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT;

import java.util.List;

/** Generates real polygon regions for Within; never replaces line/polygon features with centroids. */
final class WithinGridSupport {
    static final String CELL_ID = "cell_id", CELL_GEOMETRY = "cell_geometry";
    private WithinGridSupport() { }

    record Plan(SparkCanvasTable areas, SparkCanvasTable summaries, SpatialSummarizeWithinConfiguration configuration, double side) { }

    static Plan prepare(SpatialSummarizeWithinConfiguration c, SparkCanvasTable summaries, CanvasNodeIssueSink issues) {
        var r = c.regions();
        String path = "configuration.regions";
        if (r.binShape() != SpatialBinShape.SQUARE && r.binShape() != SpatialBinShape.HEXAGON)
            issues.error("INVALID_SPATIAL_WITHIN_GRID", "汇总格网请选择方格或六边形", path + ".binShape");
        if (r.binSize() == null || !Double.isFinite(r.binSize()) || r.binSize() <= 0 || r.binSizeUnit() == null)
            issues.error("INVALID_SPATIAL_BIN_SIZE", "格网大小需要有限正数及单位", path + ".binSize");
        CanvasNodeSupport.required(r.binIdColumnName(), "请输入格网 ID 输出字段", path + ".binIdColumnName", issues);
        CanvasNodeSupport.required(r.binGeometryColumnName(), "请输入格网 Geometry 输出字段", path + ".binGeometryColumnName", issues);
        if (!CanvasNodeSupport.blank(r.binIdColumnName()) && r.binIdColumnName().equalsIgnoreCase(r.binGeometryColumnName()))
            issues.error("DUPLICATE_COLUMN_NAME", "格网 ID 与 Geometry 输出字段重名", path + ".binGeometryColumnName");
        var gridIssues = new CanvasNodeIssueSink() {
            public boolean hasErrors() { return issues.hasErrors(); }
            public void error(String code, String message, String p) { issues.error(code, message, p.replace("configuration.planarGrid", path + ".planarGrid")); }
            public void warning(String code, String message, String p) { issues.warning(code, message, p.replace("configuration.planarGrid", path + ".planarGrid")); }
        };
        if (r.planarGrid() == null) issues.error("INVALID_SPATIAL_GRID_ORIGIN", "请配置格网原点及范围", path + ".planarGrid");
        else PlanarGridSupport.validate(r.planarGrid(), gridIssues);
        var field = c.summaryGeometryColumnName() == null ? null : CanvasNodeSupport.columns(summaries.schema()).get(c.summaryGeometryColumnName());
        if (field == null || field.fieldType() != PlatformDataType.GEOMETRY || field.geometry() == null || field.geometry().crs() == null) {
            issues.error("GEOMETRY_COLUMN_REQUIRED", "请选择带完整元数据的被汇总 Geometry", "configuration.summaryGeometryColumnName");
            return null;
        }
        var geometry = field.geometry();
        if (geometry.dimension() != CoordinateDimension.XY || geometry.kind() == GeometryKind.GEOMETRY
                || geometry.kind() == GeometryKind.GEOMETRYCOLLECTION)
            issues.error("INVALID_SPATIAL_WITHIN_GRID", "格网汇总需要明确的 XY 点、线或面类型", "configuration.summaryGeometryColumnName");
        if (issues.hasErrors()) return null;
        var resolution = SpatialDistanceSupport.resolve(r.binSize(), r.binSizeUnit(), geometry.crs());
        if (!resolution.valid() || resolution.angular()) {
            issues.error("INVALID_SPATIAL_WITHIN_GRID", "平面格网需要可解析线性单位的投影 CRS，请先显式空间转换", path + ".binSizeUnit");
            return null;
        }
        double side = resolution.sourceCrsValue() / (r.binShape() == SpatialBinShape.HEXAGON ? Math.sqrt(3) : 1);
        PlanarGridSupport.bounds(r.planarGrid(), r.binShape(), side, true, gridIssues);
        if (issues.hasErrors()) return null;
        Column sourceGeometry = summaries.dataset().col(CanvasNodeSupport.quoteIdentifier(c.summaryGeometryColumnName()));
        Dataset<Row> participating = summaries.dataset().filter(validGeometry(sourceGeometry));
        if (r.planarGrid().usesExplicitBounds()) {
            var extent = r.planarGrid().extent();
            participating = participating.filter(geometry.kind() == GeometryKind.POINT
                    ? st_functions.ST_X(sourceGeometry).geq(extent.minX()).and(st_functions.ST_X(sourceGeometry).lt(extent.maxX()))
                        .and(st_functions.ST_Y(sourceGeometry).geq(extent.minY())).and(st_functions.ST_Y(sourceGeometry).lt(extent.maxY()))
                    : st_predicates.ST_Intersects(sourceGeometry, PlanarGridSupport.extentGeometry(r.planarGrid())));
        }
        Dataset<Row> regions = generate(participating, c.summaryGeometryColumnName(), r, side, geometry.crs().code(), geometry.kind());
        var schema = new CanvasTableSchema("__within_generated_regions".equals(c.summaryTableName()) ? "__within_generated_regions_1" : "__within_generated_regions", null, List.of(
                new CanvasColumnSchema(CELL_ID, PlatformDataType.STRING, 256, null, null, false, null, false, false, null, null),
                new CanvasColumnSchema(CELL_GEOMETRY, PlatformDataType.GEOMETRY, null, null, null, false, null, false, false, null,
                        new GeometryTypeDefinition(GeometryKind.POLYGON, geometry.crs(), CoordinateDimension.XY))),
                CanvasDatasetKind.BOUNDED, null, null);
        var g = c.groupResult();
        var group = g == null ? null : new SpatialWithinGroupResult(CELL_ID, g.areaKeyOutputColumnName(), g.outputTableName(),
                g.groupValueColumnName(), g.minorityValueColumnName(), g.majorityValueColumnName(),
                g.minorityPercentageColumnName(), g.majorityPercentageColumnName(), g.mode());
        var effective = new SpatialSummarizeWithinConfiguration(schema.name(), CELL_GEOMETRY, c.summaryTableName(),
                c.summaryGeometryColumnName(), c.includeEmptyAreas(), c.distanceMethod(), c.lengthUnit(), c.areaUnit(),
                List.of(new JoinOutputColumn(JoinOutputColumnSource.LEFT, CELL_ID, r.binIdColumnName(), true),
                        new JoinOutputColumn(JoinOutputColumnSource.LEFT, CELL_GEOMETRY, r.binGeometryColumnName(), true)),
                c.statistics(), c.groupSummary(), c.temporalSlicing(), c.outputTableName(), group, r);
        return new Plan(new SparkCanvasTable(schema, regions), new SparkCanvasTable(summaries.schema(), participating), effective, side);
    }

    private static Column validGeometry(Column geometry) {
        return functions.udf((UDF1<Geometry, Boolean>) value -> {
            if (value == null || value.isEmpty()) return false;
            if (!value.isValid()) throw new IllegalArgumentException("SPATIAL_GRID_GEOMETRY_INVALID");
            for (var coordinate : value.getCoordinates()) if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y))
                throw new IllegalArgumentException("SPATIAL_GRID_GEOMETRY_INVALID");
            return true;
        }, DataTypes.BooleanType).apply(geometry);
    }

    static Column pointId(Column geometry, SpatialWithinRegions options, double side, int srid) {
        var grid = options.planarGrid();
        var cell = PlanarGridSupport.pointIndices(st_functions.ST_X(geometry).minus(grid.originX()),
                st_functions.ST_Y(geometry).minus(grid.originY()), side, options.binShape());
        return functions.concat_ws(":", functions.lit(PlanarGridSupport.identity(grid, options.binShape(), side, srid)), cell.q(), cell.r());
    }

    private static Dataset<Row> generate(Dataset<Row> source, String geometryName, SpatialWithinRegions options, double side, int srid, GeometryKind kind) {
        var grid = options.planarGrid();
        double ox = grid.originX(), oy = grid.originY();
        var shape = options.binShape();
        Column geometry = source.col(CanvasNodeSupport.quoteIdentifier(geometryName));
        Dataset<Row> bounds = source.agg(functions.min(st_functions.ST_XMin(geometry)).alias("x0"),
                functions.max(st_functions.ST_XMax(geometry)).alias("x1"), functions.min(st_functions.ST_YMin(geometry)).alias("y0"),
                functions.max(st_functions.ST_YMax(geometry)).alias("y1"));
        var rangeType = DataTypes.createStructType(List.of(
                DataTypes.createStructField("q0", DataTypes.LongType, false), DataTypes.createStructField("q1", DataTypes.LongType, false),
                DataTypes.createStructField("r0", DataTypes.LongType, false), DataTypes.createStructField("r1", DataTypes.LongType, false)));
        var e = grid.extent();
        boolean explicit = grid.usesExplicitBounds();
        Column x0 = grid.usesExplicitBounds() ? functions.lit(e.minX()) : bounds.col("x0");
        Column x1 = grid.usesExplicitBounds() ? functions.lit(e.maxX()) : bounds.col("x1");
        Column y0 = grid.usesExplicitBounds() ? functions.lit(e.minY()) : bounds.col("y0");
        Column y1 = grid.usesExplicitBounds() ? functions.lit(e.maxY()) : bounds.col("y1");
        Column scopeGeometry = functions.udf((UDF4<Double, Double, Double, Double, Geometry>) (a,b,u,v) ->
                a == null || b == null || u == null || v == null ? null : new GeometryFactory().toGeometry(new Envelope(a,b,u,v)),
                new GeometryUDT()).apply(x0,x1,y0,y1);
        Column ranges = functions.udf((UDF4<Double, Double, Double, Double, Row>) (minX, maxX, minY, maxY) -> {
            if (minX == null || maxX == null || minY == null || maxY == null) return null;
            double q0, q1, r0, r1;
            double a = (minX - ox) / side, b = (maxX - ox) / side, u = (minY - oy) / side, v = (maxY - oy) / side;
            if (shape == SpatialBinShape.SQUARE) {
                q0 = Math.floor(a); q1 = explicit ? Math.ceil(b) - 1 : Math.floor(b);
                r0 = Math.floor(u); r1 = explicit ? Math.ceil(v) - 1 : Math.floor(v);
            } else {
                q0 = Math.floor((a - 1) / 1.5); q1 = Math.ceil((b + 1) / 1.5);
                r0 = Math.floor(u / Math.sqrt(3) - 0.5 - q1 / 2); r1 = Math.ceil(v / Math.sqrt(3) + 0.5 - q0 / 2);
            }
            for (double n : new double[]{q0,q1,r0,r1}) if (!Double.isFinite(n) || Math.abs(n) > (1L << 50))
                throw new IllegalArgumentException("SPATIAL_GRID_GEOMETRY_INVALID");
            if ((q1 - q0 + 1) * (r1 - r0 + 1) > PlanarGridSupport.MAX_CANDIDATE_CELLS)
                throw new IllegalArgumentException("SPATIAL_GRID_CELL_LIMIT_EXCEEDED");
            return RowFactory.create((long) q0, (long) q1, (long) r0, (long) r1);
        }, rangeType).apply(x0,x1,y0,y1);
        Dataset<Row> cells = bounds.select(ranges.alias("range"), scopeGeometry.alias("scope")).filter("range is not null")
                .withColumn("q", functions.explode(functions.sequence(functions.col("range.q0"), functions.col("range.q1"))))
                .withColumn("r", functions.explode(functions.sequence(functions.col("range.r0"), functions.col("range.r1"))));
        cells = cells.select(cells.col("scope"), functions.concat_ws(":", functions.lit(PlanarGridSupport.identity(grid, shape, side, srid)),
                        cells.col("q"), cells.col("r")).alias(CELL_ID),
                PlanarGridSupport.geometry(cells.col("q"), cells.col("r"), shape, side, srid, grid).alias(CELL_GEOMETRY));
        Column inScope = st_functions.ST_Area(cells.col("scope")).gt(0)
                .and(st_functions.ST_Area(st_functions.ST_Intersection(cells.col(CELL_GEOMETRY), cells.col("scope"))).gt(0))
                .or(st_functions.ST_Area(cells.col("scope")).equalTo(0)
                        .and(st_predicates.ST_Intersects(cells.col(CELL_GEOMETRY), cells.col("scope"))));
        if (kind == GeometryKind.POINT) {
            // Scope filtering must not discard the deterministic owner of a boundary point.
            Dataset<Row> occupied = source.select(pointId(geometry, options, side, srid).alias(CELL_ID))
                    .groupBy(CELL_ID).agg(functions.count(functions.col(CELL_ID)).alias("members"));
            cells = cells.join(occupied, CELL_ID, "left");
            inScope = inScope.or(cells.col("members").gt(0));
        }
        return cells.filter(inScope).select(CELL_ID, CELL_GEOMETRY);
    }
}
