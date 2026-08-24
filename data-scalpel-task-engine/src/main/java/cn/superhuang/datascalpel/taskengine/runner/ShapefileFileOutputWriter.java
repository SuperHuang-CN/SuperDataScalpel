package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.data.scalpel.contract.task.ShapefileAttributeMapping;
import cn.superhuang.data.scalpel.contract.task.ShapefilePackageMode;
import cn.superhuang.data.scalpel.contract.task.ShapefileShapeType;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasPreparedFileOutput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeS3Connection;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.geotools.api.data.FeatureWriter;
import org.geotools.api.data.Transaction;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.shapefile.dbf.DbaseFileHeader;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ShapefileFileOutputWriter {
    static final long MAX_ARTIFACT_BYTES = 1_800_000_000L;
    private static final int SIZE_CHECK_INTERVAL = 1_024;
    private static final String GEOMETRY_ATTRIBUTE = "__datascalpel_geometry";
    private static final String DUMMY_ATTRIBUTE = "DS_TMP";
    private static final List<String> COMPONENT_EXTENSIONS =
            List.of("shp", "shx", "dbf", "prj", "cpg");

    private ShapefileFileOutputWriter() {
    }

    static void write(
            org.apache.spark.sql.SparkSession spark,
            CanvasPreparedFileOutput output,
            Dataset<Row> dataset,
            UUID executionId
    ) {
        FileOutputFormatOptions.Shapefile options =
                (FileOutputFormatOptions.Shapefile) output.formatOptions();
        RuntimeS3Connection connection = output.runtimeDataSource().s3Connection();
        Configuration hadoop = spark.sparkContext().hadoopConfiguration();
        Path targetDirectory = new Path(output.targetUri());
        String stagingKey = joinKey(
                connection.rootPrefix(),
                "_temporary/datascalpel-file-output/" + executionId + "/"
                        + output.node().id() + "/" + UUID.randomUUID()
        );
        Path stagingDirectory = new Path(
                "s3a://" + connection.bucket() + "/" + stagingKey
        );

        try {
            FileSystem fileSystem = targetDirectory.getFileSystem(hadoop);
            if (output.conflictPolicy()
                    == FileOutputConflictPolicy.FAIL_IF_EXISTS
                    && fileSystem.exists(targetDirectory)) {
                throw failure("FILE_OUTPUT_TARGET_EXISTS", "File Output 目标目录已存在", output);
            }
            java.nio.file.Path localDirectory = Files.createTempDirectory("datascalpel-shapefile-");
            try {
                List<java.nio.file.Path> artifacts = writeLocal(
                        localDirectory, output, dataset, options);
                uploadAndCommit(
                        fileSystem, hadoop, stagingDirectory, targetDirectory,
                        artifacts, output, options);
            } finally {
                deleteLocalTree(localDirectory);
                try {
                    fileSystem.delete(stagingDirectory, true);
                } catch (IOException ignored) {
                    // The execution-scoped temporary prefix is also safe for later lifecycle cleanup.
                }
            }
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (FileSystemException exception) {
            String message = exception.getReason() == null ? "" : exception.getReason().toLowerCase(Locale.ROOT);
            String code = message.contains("space") || message.contains("磁盘")
                    ? "SHAPEFILE_LOCAL_STORAGE_EXHAUSTED" : "SHAPEFILE_WRITE_FAILED";
            throw failure(code, "Shapefile 本地制品写入失败", output, exception);
        } catch (IOException exception) {
            throw failure("SHAPEFILE_UPLOAD_FAILED", "Shapefile S3 提交失败", output, exception);
        }
    }

    static List<java.nio.file.Path> writeLocal(
            java.nio.file.Path directory,
            CanvasPreparedFileOutput output,
            Dataset<Row> dataset,
            FileOutputFormatOptions.Shapefile options
    ) {
        return writeLocal(directory, output, dataset, options, MAX_ARTIFACT_BYTES);
    }

    static List<java.nio.file.Path> writeLocal(
            java.nio.file.Path directory,
            CanvasPreparedFileOutput output,
            Dataset<Row> dataset,
            FileOutputFormatOptions.Shapefile options,
            long maxArtifactBytes
    ) {
        java.nio.file.Path shp = directory.resolve(options.baseName() + ".shp");
        java.nio.file.Path generatedDbf = directory.resolve(options.baseName() + ".dbf");
        java.nio.file.Path strictDbf = directory.resolve(options.baseName() + ".attributes.dbf");
        ShapefileDataStore dataStore = null;
        try {
            CoordinateReferenceSystem crs = CRS.decode(
                    "EPSG:" + geometryColumn(output, options).geometry().crs().code(), true);
            SimpleFeatureType featureType = featureType(options, crs);
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("url", shp.toUri().toURL());
            parameters.put("create spatial index", Boolean.FALSE);
            parameters.put("charset", StandardCharsets.UTF_8.name());
            dataStore = (ShapefileDataStore) new ShapefileDataStoreFactory()
                    .createNewDataStore(parameters);
            dataStore.setCharset(StandardCharsets.UTF_8);
            dataStore.setIndexCreationEnabled(false);
            dataStore.createSchema(featureType);

            DbfSchema dbfSchema = DbfSchema.create(output, options);
            int geometryIndex = dataset.schema().fieldIndex(options.geometryColumnName());
            int[] attributeIndexes = options.attributeMappings().stream()
                    .mapToInt(mapping -> dataset.schema().fieldIndex(mapping.sourceColumnName()))
                    .toArray();
            long rows = 0L;
            try (FeatureWriter<SimpleFeatureType, SimpleFeature> featureWriter =
                         dataStore.getFeatureWriterAppend(Transaction.AUTO_COMMIT);
                 StrictDbfWriter dbfWriter = new StrictDbfWriter(strictDbf, dbfSchema)) {
                Iterator<Row> iterator = dataset.toLocalIterator();
                while (iterator.hasNext()) {
                    Row row = iterator.next();
                    rows++;
                    if (rows > Integer.MAX_VALUE) {
                        throw failure("SHAPEFILE_SIZE_LIMIT_EXCEEDED",
                                "Shapefile 记录数量超过 DBF 上限", output);
                    }
                    Geometry geometry = row.isNullAt(geometryIndex)
                            ? null : requireGeometry(row.get(geometryIndex), rows, output);
                    Geometry normalized = normalizeGeometry(
                            geometry, options.targetShapeType(), rows, output);
                    Object[] attributes = dbfValues(
                            row, attributeIndexes, dbfSchema, rows, output);

                    SimpleFeature feature = featureWriter.next();
                    feature.setDefaultGeometry(normalized);
                    feature.setAttribute(DUMMY_ATTRIBUTE, Boolean.TRUE);
                    featureWriter.write();
                    dbfWriter.write(attributes, rows, output);

                    if (rows % SIZE_CHECK_INTERVAL == 0) {
                        enforceComponentSizes(
                                directory, options.baseName(), maxArtifactBytes, output);
                    }
                }
                dbfWriter.finish((int) rows);
            }
            dataStore.dispose();
            dataStore = null;

            Files.move(strictDbf, generatedDbf, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(
                    directory.resolve(options.baseName() + ".cpg"),
                    "UTF-8\n",
                    StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            enforceComponentsPresent(directory, options.baseName(), output);
            enforceComponentSizes(directory, options.baseName(), maxArtifactBytes, output);

            if (options.packageMode() == ShapefilePackageMode.ZIP) {
                java.nio.file.Path zip = directory.resolve(options.baseName() + ".zip");
                zipComponents(
                        directory, options.baseName(), zip, maxArtifactBytes, output);
                return List.of(zip);
            }
            return componentPaths(directory, options.baseName());
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (FileSystemException exception) {
            String reason = exception.getReason() == null ? "" : exception.getReason().toLowerCase(Locale.ROOT);
            String code = reason.contains("space") || reason.contains("磁盘")
                    ? "SHAPEFILE_LOCAL_STORAGE_EXHAUSTED" : "SHAPEFILE_WRITE_FAILED";
            throw failure(code, "Shapefile 本地制品写入失败", output, exception);
        } catch (Exception exception) {
            throw failure("SHAPEFILE_WRITE_FAILED", "Shapefile 本地制品写入失败", output, exception);
        } finally {
            if (dataStore != null) dataStore.dispose();
        }
    }

    private static SimpleFeatureType featureType(
            FileOutputFormatOptions.Shapefile options,
            CoordinateReferenceSystem crs
    ) {
        Class<? extends Geometry> binding = switch (options.targetShapeType()) {
            case POINT -> Point.class;
            case MULTIPOINT -> MultiPoint.class;
            case POLYLINE -> MultiLineString.class;
            case POLYGON -> MultiPolygon.class;
        };
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName(options.baseName());
        builder.setCRS(crs);
        builder.add(GEOMETRY_ATTRIBUTE, binding, crs);
        builder.setDefaultGeometry(GEOMETRY_ATTRIBUTE);
        builder.add(DUMMY_ATTRIBUTE, Boolean.class);
        return builder.buildFeatureType();
    }

    private static CanvasColumnSchema geometryColumn(
            CanvasPreparedFileOutput output,
            FileOutputFormatOptions.Shapefile options
    ) {
        return output.sourceSchema().columns().stream()
                .filter(column -> column.name().equals(options.geometryColumnName()))
                .findFirst()
                .orElseThrow(() -> failure(
                        "SHAPEFILE_GEOMETRY_REQUIRED", "Shapefile Geometry 字段不存在", output));
    }

    private static Geometry requireGeometry(Object value, long rowNumber, CanvasPreparedFileOutput output) {
        if (value instanceof Geometry geometry) return geometry;
        throw failure("SHAPEFILE_GEOMETRY_TYPE_MISMATCH",
                "Shapefile 第 " + rowNumber + " 行 Geometry 运行时类型无效", output);
    }

    private static Geometry normalizeGeometry(
            Geometry geometry,
            ShapefileShapeType target,
            long rowNumber,
            CanvasPreparedFileOutput output
    ) {
        if (geometry == null) return null;
        if (geometry.isEmpty()) {
            throw failure("SHAPEFILE_EMPTY_GEOMETRY_UNSUPPORTED",
                    "Shapefile 第 " + rowNumber + " 行包含 Empty Geometry", output);
        }
        return switch (target) {
            case POINT -> {
                if (geometry instanceof Point point) yield point;
                throw kindMismatch(rowNumber, output);
            }
            case MULTIPOINT -> {
                if (geometry instanceof MultiPoint multiPoint) yield multiPoint;
                if (geometry instanceof Point point) {
                    yield point.getFactory().createMultiPoint(new Point[]{point});
                }
                throw kindMismatch(rowNumber, output);
            }
            case POLYLINE -> {
                if (geometry instanceof MultiLineString multiLine) yield multiLine;
                if (geometry instanceof LineString line) {
                    yield line.getFactory().createMultiLineString(new LineString[]{line});
                }
                throw kindMismatch(rowNumber, output);
            }
            case POLYGON -> {
                if (geometry instanceof MultiPolygon multiPolygon) yield multiPolygon;
                if (geometry instanceof Polygon polygon) {
                    yield polygon.getFactory().createMultiPolygon(new Polygon[]{polygon});
                }
                throw kindMismatch(rowNumber, output);
            }
        };
    }

    private static RunnerExecutionException kindMismatch(
            long rowNumber,
            CanvasPreparedFileOutput output
    ) {
        return failure("SHAPEFILE_GEOMETRY_TYPE_MISMATCH",
                "Shapefile 第 " + rowNumber + " 行 Geometry 与目标 Shape 类型不一致", output);
    }

    private static Object[] dbfValues(
            Row row,
            int[] indexes,
            DbfSchema schema,
            long rowNumber,
            CanvasPreparedFileOutput output
    ) {
        Object[] values = new Object[indexes.length];
        for (int index = 0; index < indexes.length; index++) {
            values[index] = row.isNullAt(indexes[index]) ? null : row.get(indexes[index]);
            schema.fields().get(index).validate(values[index], rowNumber, output);
        }
        return values;
    }

    private static void enforceComponentsPresent(
            java.nio.file.Path directory,
            String baseName,
            CanvasPreparedFileOutput output
    ) {
        for (java.nio.file.Path path : componentPaths(directory, baseName)) {
            if (!Files.isRegularFile(path)) {
                throw failure("SHAPEFILE_WRITE_FAILED",
                        "Shapefile 制品缺少 " + extension(path), output);
            }
        }
    }

    private static void enforceComponentSizes(
            java.nio.file.Path directory,
            String baseName,
            long maxArtifactBytes,
            CanvasPreparedFileOutput output
    ) throws IOException {
        for (String extension : List.of("shp", "shx", "dbf")) {
            java.nio.file.Path path = directory.resolve(baseName + "." + extension);
            if (Files.exists(path) && Files.size(path) >= maxArtifactBytes) {
                throw failure("SHAPEFILE_SIZE_LIMIT_EXCEEDED",
                        "Shapefile " + extension.toUpperCase(Locale.ROOT)
                                + " 文件达到 1.8GB 安全限制", output);
            }
        }
    }

    private static void zipComponents(
            java.nio.file.Path directory,
            String baseName,
            java.nio.file.Path zip,
            long maxArtifactBytes,
            CanvasPreparedFileOutput output
    ) throws IOException {
        try (OutputStream file = Files.newOutputStream(
                zip, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             ZipOutputStream archive = new ZipOutputStream(file, StandardCharsets.UTF_8)) {
            for (java.nio.file.Path component : componentPaths(directory, baseName)) {
                archive.putNextEntry(new ZipEntry(component.getFileName().toString()));
                Files.copy(component, archive);
                archive.closeEntry();
                archive.flush();
                if (Files.size(zip) >= maxArtifactBytes) {
                    throw failure("SHAPEFILE_SIZE_LIMIT_EXCEEDED",
                            "Shapefile ZIP 文件达到 1.8GB 安全限制", output);
                }
            }
        }
        if (Files.size(zip) >= maxArtifactBytes) {
            throw failure("SHAPEFILE_SIZE_LIMIT_EXCEEDED",
                    "Shapefile ZIP 文件达到 1.8GB 安全限制", output);
        }
    }

    static void uploadAndCommit(
            FileSystem fileSystem,
            Configuration configuration,
            Path stagingDirectory,
            Path targetDirectory,
            List<java.nio.file.Path> artifacts,
            CanvasPreparedFileOutput output,
            FileOutputFormatOptions.Shapefile options
    ) throws IOException {
        fileSystem.mkdirs(stagingDirectory);
        for (java.nio.file.Path artifact : artifacts) {
            fileSystem.copyFromLocalFile(
                    false,
                    true,
                    new Path(artifact.toUri()),
                    new Path(stagingDirectory, artifact.getFileName().toString())
            );
        }

        if (output.conflictPolicy()
                == FileOutputConflictPolicy.FAIL_IF_EXISTS) {
            if (fileSystem.exists(targetDirectory)) {
                throw failure("FILE_OUTPUT_TARGET_EXISTS", "File Output 目标目录已存在", output);
            }
        } else if (fileSystem.exists(targetDirectory)) {
            fileSystem.delete(targetDirectory, true);
        }
        fileSystem.mkdirs(targetDirectory);
        List<String> artifactNames = options.packageMode() == ShapefilePackageMode.ZIP
                ? List.of(options.baseName() + ".zip")
                : COMPONENT_EXTENSIONS.stream().map(extension -> options.baseName() + "." + extension).toList();
        for (String artifactName : artifactNames) {
            boolean copied = FileUtil.copy(
                    fileSystem, new Path(stagingDirectory, artifactName),
                    fileSystem, new Path(targetDirectory, artifactName),
                    false, true, configuration);
            if (!copied) {
                throw failure("SHAPEFILE_UPLOAD_FAILED",
                        "Shapefile S3 制品提交失败", output);
            }
        }
        try (OutputStream ignored = fileSystem.create(new Path(targetDirectory, "_SUCCESS"), false)) {
            // Completion marker intentionally has no content and is always committed last.
        }
    }

    private static List<java.nio.file.Path> componentPaths(
            java.nio.file.Path directory,
            String baseName
    ) {
        return COMPONENT_EXTENSIONS.stream()
                .map(extension -> directory.resolve(baseName + "." + extension))
                .toList();
    }

    private static String extension(java.nio.file.Path path) {
        String name = path.getFileName().toString();
        int separator = name.lastIndexOf('.');
        return separator < 0 ? name : name.substring(separator + 1).toUpperCase(Locale.ROOT);
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

    private record DbfSchema(List<DbfField> fields, DbaseFileHeader header) {
        private static DbfSchema create(
                CanvasPreparedFileOutput output,
                FileOutputFormatOptions.Shapefile options
        ) throws Exception {
            Map<String, CanvasColumnSchema> sourceColumns = new HashMap<>();
            output.sourceSchema().columns().forEach(column -> sourceColumns.put(column.name(), column));
            DbaseFileHeader header = new DbaseFileHeader(StandardCharsets.UTF_8);
            List<DbfField> fields = new ArrayList<>();
            for (ShapefileAttributeMapping mapping : options.attributeMappings()) {
                CanvasColumnSchema source = sourceColumns.get(mapping.sourceColumnName());
                DbfField field = DbfField.create(source, mapping);
                header.addColumn(
                        mapping.targetFieldName(), field.dbfType(), field.width(), field.scale());
                fields.add(field);
            }
            header.setNumRecords(0);
            return new DbfSchema(List.copyOf(fields), header);
        }
    }

    private record DbfField(
            String sourceName,
            PlatformDataType type,
            char dbfType,
            int width,
            int scale
    ) {
        private static DbfField create(
                CanvasColumnSchema source,
                ShapefileAttributeMapping mapping
        ) {
            return switch (source.fieldType()) {
                case BOOLEAN -> new DbfField(source.name(), source.fieldType(), 'L', 1, 0);
                case BYTE -> new DbfField(source.name(), source.fieldType(), 'N', 4, 0);
                case SHORT -> new DbfField(source.name(), source.fieldType(), 'N', 6, 0);
                case INTEGER -> new DbfField(source.name(), source.fieldType(), 'N', 11, 0);
                case LONG -> new DbfField(source.name(), source.fieldType(), 'N', 20, 0);
                case DECIMAL -> new DbfField(
                        source.name(), source.fieldType(), 'N',
                        source.precision() + 1 + (source.scale() > 0 ? 1 : 0),
                        source.scale());
                case STRING -> new DbfField(
                        source.name(), source.fieldType(), 'C', mapping.targetStringByteLength(), 0);
                case DATE -> new DbfField(source.name(), source.fieldType(), 'D', 8, 0);
                default -> throw new IllegalArgumentException(
                        "Unsupported Shapefile DBF field type " + source.fieldType());
            };
        }

        private void validate(
                Object value,
                long rowNumber,
                CanvasPreparedFileOutput output
        ) {
            if (value == null) return;
            if (type == PlatformDataType.STRING) {
                int bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8).length;
                if (bytes > width) {
                    throw failure("SHAPEFILE_ATTRIBUTE_VALUE_TOO_LONG",
                            "Shapefile 第 " + rowNumber + " 行字段 " + sourceName
                                    + " 超过 DBF UTF-8 字节宽度", output);
                }
            } else if (type == PlatformDataType.DECIMAL) {
                numeric(value, rowNumber, output);
            } else if (SetSupport.NUMERIC_TYPES.contains(type)) {
                numeric(value, rowNumber, output);
            }
        }

        private String numeric(
                Object value,
                long rowNumber,
                CanvasPreparedFileOutput output
        ) {
            String text;
            try {
                if (type == PlatformDataType.DECIMAL) {
                    BigDecimal decimal = value instanceof BigDecimal candidate
                            ? candidate : new BigDecimal(String.valueOf(value));
                    text = decimal.setScale(scale, RoundingMode.UNNECESSARY).toPlainString();
                } else {
                    text = String.valueOf(((Number) value).longValue());
                }
            } catch (RuntimeException exception) {
                throw failure("SHAPEFILE_NUMERIC_OVERFLOW",
                        "Shapefile 第 " + rowNumber + " 行字段 " + sourceName
                                + " 无法按 DBF 数值精度写入", output, exception);
            }
            if (text.getBytes(StandardCharsets.US_ASCII).length > width) {
                throw failure("SHAPEFILE_NUMERIC_OVERFLOW",
                        "Shapefile 第 " + rowNumber + " 行字段 " + sourceName
                                + " 超过 DBF 数值宽度", output);
            }
            return text;
        }
    }

    private static final class StrictDbfWriter implements AutoCloseable {
        private final java.nio.file.Path path;
        private final DbfSchema schema;
        private final FileChannel channel;
        private boolean finished;

        private StrictDbfWriter(java.nio.file.Path path, DbfSchema schema) throws IOException {
            this.path = path;
            this.schema = schema;
            this.channel = FileChannel.open(
                    path,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
            );
            schema.header().writeHeader(channel);
        }

        private void write(
                Object[] values,
                long rowNumber,
                CanvasPreparedFileOutput output
        ) throws IOException {
            ByteBuffer record = ByteBuffer.allocate(schema.header().getRecordLength());
            record.put((byte) ' ');
            for (int index = 0; index < values.length; index++) {
                DbfField field = schema.fields().get(index);
                Object value = values[index];
                byte[] encoded = encode(field, value, rowNumber, output);
                record.put(encoded);
            }
            record.flip();
            while (record.hasRemaining()) channel.write(record);
        }

        private byte[] encode(
                DbfField field,
                Object value,
                long rowNumber,
                CanvasPreparedFileOutput output
        ) {
            byte[] result = new byte[field.width()];
            java.util.Arrays.fill(result, (byte) ' ');
            if (value == null) {
                if (field.dbfType() == 'L') result[0] = '?';
                return result;
            }
            if (field.dbfType() == 'L') {
                result[0] = Boolean.TRUE.equals(value) ? (byte) 'T' : (byte) 'F';
                return result;
            }
            if (field.dbfType() == 'C') {
                byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
                System.arraycopy(bytes, 0, result, 0, bytes.length);
                return result;
            }
            if (field.dbfType() == 'D') {
                LocalDate date = value instanceof Date sqlDate
                        ? sqlDate.toLocalDate()
                        : value instanceof LocalDate localDate ? localDate : null;
                if (date == null) {
                    throw failure("SHAPEFILE_ATTRIBUTE_TYPE_UNSUPPORTED",
                            "Shapefile 第 " + rowNumber + " 行字段 " + field.sourceName()
                                    + " 不是 DATE", output);
                }
                byte[] bytes = DateTimeFormatter.BASIC_ISO_DATE.format(date)
                        .getBytes(StandardCharsets.US_ASCII);
                if (bytes.length != 8) {
                    throw failure("SHAPEFILE_ATTRIBUTE_VALUE_TOO_LONG",
                            "Shapefile 第 " + rowNumber + " 行字段 " + field.sourceName()
                                    + " 超过 DBF DATE 范围", output);
                }
                System.arraycopy(bytes, 0, result, 0, bytes.length);
                return result;
            }
            byte[] bytes = field.numeric(value, rowNumber, output)
                    .getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(bytes, 0, result, result.length - bytes.length, bytes.length);
            return result;
        }

        private void finish(int records) throws IOException {
            channel.write(ByteBuffer.wrap(new byte[]{0x1A}));
            schema.header().setNumRecords(records);
            channel.position(0L);
            schema.header().writeHeader(channel);
            channel.force(true);
            finished = true;
        }

        @Override
        public void close() throws IOException {
            channel.close();
            if (!finished) Files.deleteIfExists(path);
        }
    }

    private static final class SetSupport {
        private static final java.util.Set<PlatformDataType> NUMERIC_TYPES = java.util.Set.of(
                PlatformDataType.BYTE,
                PlatformDataType.SHORT,
                PlatformDataType.INTEGER,
                PlatformDataType.LONG
        );

        private SetSupport() {
        }
    }
}
