package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.GeoParquetCoveringMode;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedFileOutput;
import cn.superhuang.datascalpel.taskengine.canvas.GeoParquetCrsSupport;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequenceFilter;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

final class GeoParquetFileOutputWriter {
    private GeoParquetFileOutputWriter() {
    }

    static void write(CanvasPreparedFileOutput output, Dataset<Row> dataset) {
        FileOutputFormatOptions.GeoParquet options =
                (FileOutputFormatOptions.GeoParquet) output.formatOptions();
        CanvasColumnSchema geometryColumn = output.sourceSchema().columns().stream()
                .filter(column -> column.name().equals(options.geometryColumnName()))
                .findFirst()
                .orElseThrow(() -> failure(
                        "GEOPARQUET_GEOMETRY_REQUIRED", "GeoParquet Geometry 字段不存在", output));
        String projJson = GeoParquetCrsSupport.projJson(geometryColumn.geometry().crs().code());
        Dataset<Row> validated = validateGeometryColumn(
                dataset, options.geometryColumnName(), geometryColumn.geometry().kind(), output.node().id());
        try {
            validated.write()
                    .mode(output.conflictPolicy()
                            == FileOutputConflictPolicy.OVERWRITE
                            ? SaveMode.Overwrite : SaveMode.ErrorIfExists)
                    .format("geoparquet")
                    .option("geoparquet.version", "1.1.0")
                    .option("geoparquet.crs." + options.geometryColumnName(), projJson)
                    .option("geoparquet.covering.mode",
                            options.coveringMode() == GeoParquetCoveringMode.ROW_BBOX
                                    ? "auto" : "legacy")
                    .option("compression", options.compression().name().toLowerCase(java.util.Locale.ROOT))
                    .save(output.targetUri());
        } catch (Exception exception) {
            RunnerExecutionException classified = nestedRunnerFailure(exception);
            if (classified != null) throw classified;
            throw failure("GEOPARQUET_WRITE_FAILED", "GeoParquet 写出失败", output, exception);
        }
    }

    private static Dataset<Row> validateGeometryColumn(
            Dataset<Row> dataset,
            String columnName,
            GeometryKind expectedKind,
            String nodeId
    ) {
        UDF1<Geometry, Geometry> validator = geometry -> {
            validateGeometry(geometry, expectedKind, nodeId, columnName);
            return geometry;
        };
        return dataset.withColumn(
                columnName,
                functions.udf(validator, new GeometryUDT()).apply(
                        functions.col(quoteIdentifier(columnName)))
        );
    }

    private static void validateGeometry(
            Geometry geometry,
            GeometryKind expectedKind,
            String nodeId,
            String columnName
    ) {
        try {
            if (geometry == null) return;
            if (geometry.isEmpty()) {
                throw new RunnerExecutionException(
                        "GEOPARQUET_EMPTY_GEOMETRY_UNSUPPORTED",
                        "GeoParquet 不支持 Empty Geometry，字段：" + columnName,
                        nodeId
                );
            }
            GeometryKind actualKind = geometryKind(geometry);
            if (actualKind == null || expectedKind != GeometryKind.GEOMETRY && actualKind != expectedKind) {
                throw new RunnerExecutionException(
                        "GEOPARQUET_GEOMETRY_TYPE_MISMATCH",
                        "GeoParquet Geometry 类型与声明不一致，字段：" + columnName,
                        nodeId
                );
            }
            geometry.apply(new CoordinateSequenceFilter() {
                @Override
                public void filter(CoordinateSequence sequence, int index) {
                    if (sequence.getDimension() != 2 || sequence.getMeasures() != 0) {
                        throw new RunnerExecutionException(
                                "GEOPARQUET_COORDINATE_INVALID",
                                "GeoParquet Geometry 实际坐标维度不是 XY，字段：" + columnName,
                                nodeId
                        );
                    }
                    if (!Double.isFinite(sequence.getX(index))
                            || !Double.isFinite(sequence.getY(index))) {
                        throw new RunnerExecutionException(
                                "GEOPARQUET_COORDINATE_INVALID",
                                "GeoParquet Geometry 包含非有限坐标，字段：" + columnName,
                                nodeId
                        );
                    }
                }

                @Override
                public boolean isDone() {
                    return false;
                }

                @Override
                public boolean isGeometryChanged() {
                    return false;
                }
            });
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RunnerExecutionException(
                    "GEOPARQUET_COORDINATE_INVALID",
                    "GeoParquet Geometry 坐标结构无效，字段：" + columnName,
                    nodeId,
                    exception
            );
        }
    }

    private static GeometryKind geometryKind(Geometry geometry) {
        if (geometry instanceof Point) return GeometryKind.POINT;
        if (geometry instanceof MultiPoint) return GeometryKind.MULTIPOINT;
        if (geometry instanceof MultiLineString) return GeometryKind.MULTILINESTRING;
        if (geometry instanceof LineString) return GeometryKind.LINESTRING;
        if (geometry instanceof MultiPolygon) return GeometryKind.MULTIPOLYGON;
        if (geometry instanceof Polygon) return GeometryKind.POLYGON;
        if (geometry instanceof GeometryCollection) return GeometryKind.GEOMETRYCOLLECTION;
        return null;
    }

    private static String quoteIdentifier(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    private static RunnerExecutionException nestedRunnerFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof RunnerExecutionException exception) return exception;
        }
        return null;
    }

    private static RunnerExecutionException failure(
            String code,
            String message,
            CanvasPreparedFileOutput output
    ) {
        return new RunnerExecutionException(code, message, output.node().id());
    }

    private static RunnerExecutionException failure(
            String code,
            String message,
            CanvasPreparedFileOutput output,
            Throwable cause
    ) {
        return new RunnerExecutionException(code, message, output.node().id(), cause);
    }
}
