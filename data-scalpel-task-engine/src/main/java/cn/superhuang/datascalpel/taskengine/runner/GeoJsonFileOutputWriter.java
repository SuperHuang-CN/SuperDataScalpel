package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedFileOutput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeS3Connection;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.locationtech.jts.algorithm.Orientation;
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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class GeoJsonFileOutputWriter {
    static final long MAX_ARTIFACT_BYTES = 1_800_000_000L;

    private GeoJsonFileOutputWriter() {
    }

    static void write(
            org.apache.spark.sql.SparkSession spark,
            CanvasPreparedFileOutput output,
            Dataset<Row> dataset,
            UUID executionId
    ) {
        FileOutputFormatOptions.GeoJson options =
                (FileOutputFormatOptions.GeoJson) output.formatOptions();
        RuntimeS3Connection connection = output.runtimeDataSource().s3Connection();
        Configuration hadoop = spark.sparkContext().hadoopConfiguration();
        Path targetDirectory = new Path(output.targetUri());
        String stagingKey = joinKey(
                connection.rootPrefix(),
                "_temporary/datascalpel-file-output/" + executionId + "/"
                        + output.node().id() + "/" + UUID.randomUUID()
        );
        Path stagingDirectory = new Path("s3a://" + connection.bucket() + "/" + stagingKey);

        try {
            FileSystem fileSystem = targetDirectory.getFileSystem(hadoop);
            if (output.conflictPolicy()
                    == FileOutputConflictPolicy.FAIL_IF_EXISTS
                    && fileSystem.exists(targetDirectory)) {
                throw failure("FILE_OUTPUT_TARGET_EXISTS", "File Output 目标目录已存在", output);
            }
            java.nio.file.Path localDirectory = createRestrictedTempDirectory(output);
            try {
                java.nio.file.Path artifact = writeLocal(
                        localDirectory, output, dataset, options, MAX_ARTIFACT_BYTES);
                uploadAndCommit(
                        fileSystem, hadoop, stagingDirectory, targetDirectory,
                        artifact, output, options);
            } finally {
                deleteLocalTree(localDirectory);
                try {
                    fileSystem.delete(stagingDirectory, true);
                } catch (IOException ignored) {
                    // The execution-scoped temporary prefix is safe for lifecycle cleanup.
                }
            }
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure("GEOJSON_UPLOAD_FAILED", "GeoJSON S3 提交失败", output, exception);
        }
    }

    static java.nio.file.Path writeLocal(
            java.nio.file.Path directory,
            CanvasPreparedFileOutput output,
            Dataset<Row> dataset,
            FileOutputFormatOptions.GeoJson options,
            long maxArtifactBytes
    ) {
        java.nio.file.Path artifact = directory.resolve(options.baseName() + ".geojson");
        CanvasColumnSchema geometryColumn = output.sourceSchema().columns().stream()
                .filter(column -> column.name().equals(options.geometryColumnName()))
                .findFirst()
                .orElseThrow(() -> failure(
                        "GEOJSON_GEOMETRY_REQUIRED", "GeoJSON Geometry 字段不存在", output));
        int geometryIndex = dataset.schema().fieldIndex(options.geometryColumnName());
        int idIndex = options.idColumnName() == null
                ? -1 : dataset.schema().fieldIndex(options.idColumnName());
        try (OutputStream file = createRestrictedArtifact(artifact);
             SizeLimitedOutputStream limited = new SizeLimitedOutputStream(file, maxArtifactBytes);
             JsonGenerator generator = new JsonFactory().createGenerator(limited)) {
            generator.writeStartObject();
            generator.writeStringField("type", "FeatureCollection");
            generator.writeArrayFieldStart("features");
            Iterator<Row> rows = dataset.toLocalIterator();
            long rowNumber = 0L;
            while (rows.hasNext()) {
                Row row = rows.next();
                rowNumber++;
                Geometry geometry = row.isNullAt(geometryIndex)
                        ? null : row.getAs(geometryIndex);
                validateGeometry(
                        geometry, geometryColumn.geometry().kind(), output, options.geometryColumnName(), rowNumber);
                writeFeature(generator, row, geometry, idIndex, output, options, rowNumber);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (SizeLimitExceededException exception) {
            throw failure("GEOJSON_SIZE_LIMIT_EXCEEDED",
                    "GeoJSON 文件达到 1.8GB 安全限制", output, exception);
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (FileSystemException exception) {
            String reason = exception.getReason() == null
                    ? "" : exception.getReason().toLowerCase(Locale.ROOT);
            String code = reason.contains("space") || reason.contains("磁盘")
                    ? "GEOJSON_LOCAL_STORAGE_EXHAUSTED" : "GEOJSON_WRITE_FAILED";
            throw failure(code, "GeoJSON 本地制品写入失败", output, exception);
        } catch (IOException exception) {
            throw failure("GEOJSON_WRITE_FAILED", "GeoJSON 本地制品写入失败", output, exception);
        }
        verifyArtifactSize(artifact, maxArtifactBytes, output);
        return artifact;
    }

    private static java.nio.file.Path createRestrictedTempDirectory(
            CanvasPreparedFileOutput output
    ) {
        try {
            if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                return Files.createTempDirectory(
                        "datascalpel-geojson-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------"))
                );
            }
            return Files.createTempDirectory("datascalpel-geojson-");
        } catch (FileSystemException exception) {
            String reason = exception.getReason() == null
                    ? "" : exception.getReason().toLowerCase(Locale.ROOT);
            String code = reason.contains("space") || reason.contains("quota")
                    || reason.contains("磁盘")
                    ? "GEOJSON_LOCAL_STORAGE_EXHAUSTED" : "GEOJSON_WRITE_FAILED";
            throw failure(code, "GeoJSON 本地制品写入失败", output, exception);
        } catch (IOException exception) {
            throw failure("GEOJSON_WRITE_FAILED", "GeoJSON 本地制品写入失败", output, exception);
        }
    }

    private static OutputStream createRestrictedArtifact(java.nio.file.Path artifact)
            throws IOException {
        if (artifact.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.createFile(
                    artifact,
                    PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------"))
            );
        } else {
            Files.createFile(artifact);
        }
        return Files.newOutputStream(artifact, StandardOpenOption.WRITE);
    }

    private static void verifyArtifactSize(
            java.nio.file.Path artifact,
            long maxArtifactBytes,
            CanvasPreparedFileOutput output
    ) {
        try {
            if (Files.size(artifact) >= maxArtifactBytes) {
                throw failure("GEOJSON_SIZE_LIMIT_EXCEEDED",
                        "GeoJSON 文件达到 1.8GB 安全限制", output);
            }
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (FileSystemException exception) {
            String reason = exception.getReason() == null
                    ? "" : exception.getReason().toLowerCase(Locale.ROOT);
            String code = reason.contains("space") || reason.contains("quota")
                    || reason.contains("磁盘")
                    ? "GEOJSON_LOCAL_STORAGE_EXHAUSTED" : "GEOJSON_WRITE_FAILED";
            throw failure(code, "GeoJSON 本地制品写入失败", output, exception);
        } catch (IOException exception) {
            throw failure("GEOJSON_WRITE_FAILED", "GeoJSON 本地制品写入失败", output, exception);
        }
    }

    private static void writeFeature(
            JsonGenerator generator,
            Row row,
            Geometry geometry,
            int idIndex,
            CanvasPreparedFileOutput output,
            FileOutputFormatOptions.GeoJson options,
            long rowNumber
    ) throws IOException {
        generator.writeStartObject();
        generator.writeStringField("type", "Feature");
        if (idIndex >= 0 && !row.isNullAt(idIndex)) {
            CanvasColumnSchema idColumn = output.sourceSchema().columns().stream()
                    .filter(column -> column.name().equals(options.idColumnName()))
                    .findFirst()
                    .orElseThrow();
            generator.writeFieldName("id");
            writeFeatureId(generator, row.get(idIndex), idColumn.fieldType());
        }
        generator.writeFieldName("geometry");
        if (geometry == null) {
            generator.writeNull();
        } else {
            try {
                writeGeometry(generator, geometry);
            } catch (RuntimeException exception) {
                throw failure(
                        "GEOJSON_COORDINATE_INVALID",
                        "GeoJSON Geometry 坐标结构无效，字段：" + options.geometryColumnName()
                                + "，行：" + rowNumber,
                        output,
                        exception
                );
            }
        }
        generator.writeObjectFieldStart("properties");
        List<CanvasColumnSchema> columns = output.sourceSchema().columns();
        for (int index = 0; index < columns.size(); index++) {
            CanvasColumnSchema column = columns.get(index);
            if (column.name().equals(options.geometryColumnName())) continue;
            Object value = row.isNullAt(index) ? null : row.get(index);
            if (value == null && options.ignoreNullProperties()) continue;
            generator.writeFieldName(column.name());
            writePropertyValue(generator, value, column, output, rowNumber);
        }
        generator.writeEndObject();
        generator.writeEndObject();
    }

    private static void writeFeatureId(
            JsonGenerator generator,
            Object value,
            PlatformDataType type
    ) throws IOException {
        if (type == PlatformDataType.STRING) {
            generator.writeString((String) value);
        } else {
            generator.writeNumber(((Number) value).longValue());
        }
    }

    private static void writePropertyValue(
            JsonGenerator generator,
            Object value,
            CanvasColumnSchema column,
            CanvasPreparedFileOutput output,
            long rowNumber
    ) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        switch (column.fieldType()) {
            case BOOLEAN -> generator.writeBoolean((Boolean) value);
            case BYTE, SHORT, INTEGER -> generator.writeNumber(((Number) value).intValue());
            case LONG -> generator.writeNumber(((Number) value).longValue());
            case FLOAT, DOUBLE -> {
                double number = ((Number) value).doubleValue();
                if (!Double.isFinite(number)) {
                    throw failure("GEOJSON_NON_FINITE_NUMBER",
                            "GeoJSON 属性包含非有限数值，字段：" + column.name()
                                    + "，行：" + rowNumber,
                            output);
                }
                if (column.fieldType() == PlatformDataType.FLOAT) {
                    generator.writeNumber(((Number) value).floatValue());
                } else {
                    generator.writeNumber(number);
                }
            }
            case DECIMAL -> generator.writeNumber(decimal(value).toPlainString());
            case STRING -> generator.writeString((String) value);
            case DATE -> generator.writeString(date(value).format(DateTimeFormatter.ISO_LOCAL_DATE));
            case TIMESTAMP -> generator.writeString(
                    instant(value).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT));
            case TIMESTAMP_NTZ -> generator.writeString(
                    localDateTime(value).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            case BINARY -> throw failure("GEOJSON_PROPERTY_TYPE_UNSUPPORTED",
                    "GeoJSON properties 不支持 BINARY 字段：" + column.name(), output);
            case GEOMETRY -> throw failure("GEOJSON_MULTIPLE_GEOMETRY_COLUMNS_UNSUPPORTED",
                    "GeoJSON properties 不支持额外 Geometry 字段：" + column.name(), output);
        }
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal
                ? decimal : new BigDecimal(value.toString());
    }

    private static LocalDate date(Object value) {
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString());
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        return Instant.parse(value.toString());
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(value.toString());
    }

    private static void validateGeometry(
            Geometry geometry,
            GeometryKind expectedKind,
            CanvasPreparedFileOutput output,
            String columnName,
            long rowNumber
    ) {
        try {
            if (geometry == null) return;
            GeometryKind actualKind = geometryKind(geometry);
            if (actualKind == null || expectedKind != GeometryKind.GEOMETRY && actualKind != expectedKind) {
                throw failure("GEOJSON_GEOMETRY_TYPE_MISMATCH",
                        "GeoJSON Geometry 类型与声明不一致，字段：" + columnName
                                + "，行：" + rowNumber,
                        output);
            }
            validateGeometryValues(geometry, output, columnName, rowNumber);
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(
                    "GEOJSON_COORDINATE_INVALID",
                    "GeoJSON Geometry 坐标结构无效，字段：" + columnName
                            + "，行：" + rowNumber,
                    output,
                    exception
            );
        }
    }

    private static void validateGeometryValues(
            Geometry geometry,
            CanvasPreparedFileOutput output,
            String columnName,
            long rowNumber
    ) {
        if (geometry.isEmpty()) {
            throw failure("GEOJSON_EMPTY_GEOMETRY_UNSUPPORTED",
                    "GeoJSON 不支持 Empty Geometry，字段：" + columnName
                            + "，行：" + rowNumber,
                    output);
        }
        geometry.apply(new CoordinateSequenceFilter() {
            @Override
            public void filter(CoordinateSequence sequence, int index) {
                if (sequence.getDimension() != 2 || sequence.getMeasures() != 0) {
                    throw failure("GEOJSON_COORDINATE_INVALID",
                            "GeoJSON Geometry 实际坐标维度不是 XY，字段：" + columnName
                                    + "，行：" + rowNumber,
                            output);
                }
                double longitude = sequence.getX(index);
                double latitude = sequence.getY(index);
                if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) {
                    throw failure("GEOJSON_NON_FINITE_NUMBER",
                            "GeoJSON Geometry 包含非有限坐标，字段：" + columnName
                                    + "，行：" + rowNumber,
                            output);
                }
                if (longitude < -180d || longitude > 180d
                        || latitude < -90d || latitude > 90d) {
                    throw failure("GEOJSON_COORDINATE_OUT_OF_RANGE",
                            "GeoJSON 坐标超出 WGS84 经纬度范围，字段：" + columnName
                                    + "，行：" + rowNumber,
                            output);
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
    }

    private static void writeGeometry(JsonGenerator generator, Geometry geometry) throws IOException {
        generator.writeStartObject();
        GeometryKind kind = geometryKind(geometry);
        if (kind == null) throw new IOException("Unsupported JTS Geometry kind");
        generator.writeStringField("type", geoJsonType(kind));
        if (geometry instanceof GeometryCollection collection
                && !(geometry instanceof MultiPoint)
                && !(geometry instanceof MultiLineString)
                && !(geometry instanceof MultiPolygon)) {
            generator.writeArrayFieldStart("geometries");
            for (int index = 0; index < collection.getNumGeometries(); index++) {
                writeGeometry(generator, collection.getGeometryN(index));
            }
            generator.writeEndArray();
        } else {
            generator.writeFieldName("coordinates");
            writeCoordinates(generator, geometry);
        }
        generator.writeEndObject();
    }

    private static void writeCoordinates(JsonGenerator generator, Geometry geometry) throws IOException {
        switch (geometry) {
            case Point point -> writePosition(generator, point.getCoordinateSequence(), 0);
            case MultiPoint multiPoint -> {
                generator.writeStartArray();
                for (int index = 0; index < multiPoint.getNumGeometries(); index++) {
                    Point point = (Point) multiPoint.getGeometryN(index);
                    writePosition(generator, point.getCoordinateSequence(), 0);
                }
                generator.writeEndArray();
            }
            case LineString lineString -> writeSequence(generator, lineString.getCoordinateSequence(), false);
            case MultiLineString multiLineString -> {
                generator.writeStartArray();
                for (int index = 0; index < multiLineString.getNumGeometries(); index++) {
                    LineString lineString = (LineString) multiLineString.getGeometryN(index);
                    writeSequence(generator, lineString.getCoordinateSequence(), false);
                }
                generator.writeEndArray();
            }
            case Polygon polygon -> writePolygonCoordinates(generator, polygon);
            case MultiPolygon multiPolygon -> {
                generator.writeStartArray();
                for (int index = 0; index < multiPolygon.getNumGeometries(); index++) {
                    writePolygonCoordinates(generator, (Polygon) multiPolygon.getGeometryN(index));
                }
                generator.writeEndArray();
            }
            default -> throw new IOException("Unsupported coordinate Geometry kind");
        }
    }

    private static void writePolygonCoordinates(JsonGenerator generator, Polygon polygon) throws IOException {
        generator.writeStartArray();
        writeRing(generator, polygon.getExteriorRing().getCoordinateSequence(), true);
        for (int index = 0; index < polygon.getNumInteriorRing(); index++) {
            writeRing(generator, polygon.getInteriorRingN(index).getCoordinateSequence(), false);
        }
        generator.writeEndArray();
    }

    private static void writeRing(
            JsonGenerator generator,
            CoordinateSequence sequence,
            boolean exterior
    ) throws IOException {
        boolean reverse = Orientation.isCCW(sequence) != exterior;
        writeSequence(generator, sequence, reverse);
    }

    private static void writeSequence(
            JsonGenerator generator,
            CoordinateSequence sequence,
            boolean reverse
    ) throws IOException {
        generator.writeStartArray();
        if (reverse) {
            for (int index = sequence.size() - 1; index >= 0; index--) {
                writePosition(generator, sequence, index);
            }
        } else {
            for (int index = 0; index < sequence.size(); index++) {
                writePosition(generator, sequence, index);
            }
        }
        generator.writeEndArray();
    }

    private static void writePosition(
            JsonGenerator generator,
            CoordinateSequence sequence,
            int index
    ) throws IOException {
        generator.writeStartArray();
        generator.writeNumber(sequence.getX(index));
        generator.writeNumber(sequence.getY(index));
        generator.writeEndArray();
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

    private static String geoJsonType(GeometryKind kind) {
        return switch (kind) {
            case POINT -> "Point";
            case MULTIPOINT -> "MultiPoint";
            case LINESTRING -> "LineString";
            case MULTILINESTRING -> "MultiLineString";
            case POLYGON -> "Polygon";
            case MULTIPOLYGON -> "MultiPolygon";
            case GEOMETRYCOLLECTION -> "GeometryCollection";
            case GEOMETRY -> throw new IllegalArgumentException("GEOMETRY is not a concrete value kind");
        };
    }

    static void uploadAndCommit(
            FileSystem fileSystem,
            Configuration configuration,
            Path stagingDirectory,
            Path targetDirectory,
            java.nio.file.Path artifact,
            CanvasPreparedFileOutput output,
            FileOutputFormatOptions.GeoJson options
    ) throws IOException {
        fileSystem.mkdirs(stagingDirectory);
        String artifactName = options.baseName() + ".geojson";
        fileSystem.copyFromLocalFile(
                false,
                true,
                new Path(artifact.toUri()),
                new Path(stagingDirectory, artifactName)
        );
        if (output.conflictPolicy()
                == FileOutputConflictPolicy.FAIL_IF_EXISTS) {
            if (fileSystem.exists(targetDirectory)) {
                throw failure("FILE_OUTPUT_TARGET_EXISTS", "File Output 目标目录已存在", output);
            }
        } else if (fileSystem.exists(targetDirectory)) {
            fileSystem.delete(targetDirectory, true);
        }
        fileSystem.mkdirs(targetDirectory);
        boolean copied = FileUtil.copy(
                fileSystem, new Path(stagingDirectory, artifactName),
                fileSystem, new Path(targetDirectory, artifactName),
                false, true, configuration);
        if (!copied) {
            throw failure("GEOJSON_UPLOAD_FAILED", "GeoJSON S3 制品提交失败", output);
        }
        try (OutputStream ignored = fileSystem.create(new Path(targetDirectory, "_SUCCESS"), false)) {
            // Completion marker intentionally has no content and is committed last.
        }
    }

    private static String joinKey(String prefix, String path) {
        return prefix == null || prefix.isBlank() ? path : prefix + "/" + path;
    }

    private static void deleteLocalTree(java.nio.file.Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Runner work directories are independently cleaned after process exit.
                }
            });
        } catch (IOException ignored) {
            // Runner work directories are independently cleaned after process exit.
        }
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

    private static final class SizeLimitedOutputStream extends FilterOutputStream {
        private final long maximumBytes;
        private long writtenBytes;

        private SizeLimitedOutputStream(OutputStream output, long maximumBytes) {
            super(output);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public void write(int value) throws IOException {
            ensureCapacity(1);
            out.write(value);
            writtenBytes++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            ensureCapacity(length);
            out.write(bytes, offset, length);
            writtenBytes += length;
        }

        private void ensureCapacity(int nextBytes) throws SizeLimitExceededException {
            if (nextBytes < 0 || writtenBytes + nextBytes >= maximumBytes) {
                throw new SizeLimitExceededException();
            }
        }
    }

    private static final class SizeLimitExceededException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
