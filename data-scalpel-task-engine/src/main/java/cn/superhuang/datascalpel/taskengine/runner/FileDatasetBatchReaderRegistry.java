package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.filegdb.FileGdbFeatureCursor;
import cn.superhuang.data.scalpel.filegdb.FileGdbOpenOptions;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadOptions;
import cn.superhuang.data.scalpel.filegdb.FileGeodatabase;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFeature;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbField;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSchema;
import cn.superhuang.data.scalpel.filegdb.s3.S3FileGdbLocation;
import cn.superhuang.data.scalpel.filegdb.s3.S3FileGdbOptions;
import cn.superhuang.data.scalpel.filegdb.s3.S3FileGdbSource;
import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;
import cn.superhuang.data.scalpel.shapefile.ShapefileFeatureCursor;
import cn.superhuang.data.scalpel.shapefile.ShapefileOpenOptions;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadLimits;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadOptions;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFeature;
import cn.superhuang.data.scalpel.shapefile.s3.S3ShapefileLocation;
import cn.superhuang.data.scalpel.shapefile.s3.S3ShapefileOptions;
import cn.superhuang.data.scalpel.shapefile.s3.S3ShapefileSource;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.datascalpel.taskengine.contract.FileDatasetCompression;
import cn.superhuang.datascalpel.taskengine.contract.FileDatasetFormat;
import cn.superhuang.datascalpel.taskengine.contract.FileRecordDelimiter;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeFileInput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeFileParsingOptions;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeFileStorage;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.conf.Configuration;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.spark.TaskContext;
import org.apache.spark.api.java.function.MapPartitionsFunction;
import org.apache.spark.sql.DataFrameReader;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.types.ArrayType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.MapType;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

final class FileDatasetBatchReaderRegistry {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    Dataset<Row> read(
            SparkSession spark,
            RuntimeFileStorage storage,
            RuntimeFileInput input,
            CanvasTableSchema logicalSchema,
            String nodeId,
            String nodeName
    ) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(input, "input");
        StructType schema = SparkTypeMapper.toStructType(logicalSchema.columns());
        configureS3A(spark.sparkContext().hadoopConfiguration(), storage);
        if (input.sources().isEmpty()) {
            throw parseFailure(nodeId, nodeName, "文件数据集逻辑表没有可读取的当前来源", null);
        }
        return unionSources(input, sourceInput ->
                readSingle(spark, storage, sourceInput, logicalSchema, schema, nodeId, nodeName));
    }

    static Dataset<Row> unionSources(
            RuntimeFileInput input,
            Function<RuntimeFileInput, Dataset<Row>> sourceReader
    ) {
        Dataset<Row> combined = null;
        for (cn.superhuang.datascalpel.taskengine.contract.RuntimeFileSource source : input.sources()) {
            Dataset<Row> current = sourceReader.apply(input.forSource(source));
            combined = combined == null ? current : combined.unionByName(current);
        }
        if (combined == null) {
            throw new IllegalArgumentException("文件数据集逻辑表没有可读取的来源");
        }
        return combined;
    }

    private Dataset<Row> readSingle(
            SparkSession spark,
            RuntimeFileStorage storage,
            RuntimeFileInput input,
            CanvasTableSchema logicalSchema,
            StructType schema,
            String nodeId,
            String nodeName
    ) {
        try {
            Dataset<Row> source = switch (input.format()) {
                case CSV, TSV -> readDelimited(spark, storage, input, schema, nodeId, nodeName);
                case TXT -> readText(spark, storage, input, schema, nodeId, nodeName);
                case JSON, JSONL -> readJson(
                        spark, storage, input, logicalSchema.columns(), schema, nodeId, nodeName);
                case PARQUET -> projectToLogicalSchema(
                        spark.read().parquet(objectUri(storage, input)),
                        logicalSchema.columns(),
                        schema
                );
                case AVRO -> projectToLogicalSchema(
                        spark.read().format("avro").load(objectUri(storage, input)),
                        logicalSchema.columns(),
                        schema
                );
                case XLS, XLSX -> readSpreadsheet(
                        spark, storage, input, logicalSchema.columns(), schema, nodeId, nodeName);
                case GDB -> readGdb(
                        spark, storage, input, logicalSchema.columns(), schema, nodeId, nodeName);
                case SHP -> readShapefile(
                        spark, storage, input, logicalSchema.columns(), schema, nodeId, nodeName);
            };
            return guardReadFailure(source, schema, nodeId, nodeName);
        } catch (Throwable throwable) {
            throw normalizeFailure(nodeId, nodeName, throwable);
        }
    }

    private static Dataset<Row> readDelimited(
            SparkSession spark,
            RuntimeFileStorage storage,
            RuntimeFileInput input,
            StructType schema,
            String nodeId,
            String nodeName
    ) {
        if (!(input.parsingOptions() instanceof RuntimeFileParsingOptions.Csv options)) {
            throw parseFailure(nodeId, nodeName, "文件解析参数与分隔文本格式不匹配", null);
        }
        DataFrameReader reader = spark.read()
                .schema(schema)
                .option("mode", "FAILFAST")
                .option("encoding", options.charset())
                .option("sep", options.fieldDelimiter())
                .option("header", Boolean.toString(options.firstRowHeader()));
        if (options.quoteCharacter() != null) {
            reader.option("quote", options.quoteCharacter());
        }
        if (options.escapeCharacter() != null) {
            reader.option("escape", options.escapeCharacter());
        }
        String lineSep = lineSeparator(options.recordDelimiter());
        if (lineSep != null) {
            reader.option("lineSep", lineSep);
        }
        return reader.csv(objectUri(storage, input));
    }

    private static Dataset<Row> readText(
            SparkSession spark,
            RuntimeFileStorage storage,
            RuntimeFileInput input,
            StructType schema,
            String nodeId,
            String nodeName
    ) {
        if (!(input.parsingOptions() instanceof RuntimeFileParsingOptions.Text options)) {
            throw parseFailure(nodeId, nodeName, "文件解析参数与文本格式不匹配", null);
        }
        DataFrameReader reader = spark.read()
                .format("text")
                .schema(schema)
                .option("encoding", options.charset());
        String lineSep = lineSeparator(options.recordDelimiter());
        if (lineSep != null) {
            reader.option("lineSep", lineSep);
        }
        return reader.load(objectUri(storage, input));
    }

    private static Dataset<Row> readJson(
            SparkSession spark,
            RuntimeFileStorage storage,
            RuntimeFileInput input,
            List<CanvasColumnSchema> columns,
            StructType schema,
            String nodeId,
            String nodeName
    ) {
        List<RuntimeColumnSchema> runtimeColumns = runtimeColumns(columns);
        return singlePartition(spark, schema, ignored -> {
            List<CanvasColumnSchema> executorColumns = canvasColumns(runtimeColumns);
            try (S3Client client = s3Client(storage);
                 InputStream raw = objectStream(client, storage, input, nodeId, nodeName);
                 InputStream content = decompress(raw, input.compression())) {
                List<Map<String, Object>> records = input.format() == FileDatasetFormat.JSON
                        ? jsonRecords(content, input)
                        : jsonLinesRecords(content, input);
                return records.stream().map(record -> row(record, executorColumns)).iterator();
            } catch (FileDatasetReadException exception) {
                throw exception;
            } catch (RuntimeException | IOException exception) {
                throw parseFailure(nodeId, nodeName, "文件数据集 JSON 内容解析失败", exception);
            }
        });
    }

    private static Dataset<Row> readSpreadsheet(
            SparkSession spark,
            RuntimeFileStorage storage,
            RuntimeFileInput input,
            List<CanvasColumnSchema> columns,
            StructType schema,
            String nodeId,
            String nodeName
    ) {
        if (!(input.parsingOptions() instanceof RuntimeFileParsingOptions.Spreadsheet options)) {
            throw parseFailure(nodeId, nodeName, "文件解析参数与 Excel 格式不匹配", null);
        }
        List<RuntimeColumnSchema> runtimeColumns = runtimeColumns(columns);
        return singlePartition(spark, schema, ignored -> {
            List<CanvasColumnSchema> executorColumns = canvasColumns(runtimeColumns);
            Path temporary = null;
            try (S3Client client = s3Client(storage)) {
                temporary = Files.createTempFile("datascalpel-file-input-", input.format() == FileDatasetFormat.XLS
                        ? ".xls" : ".xlsx");
                try (ResponseInputStream<GetObjectResponse> source = client.getObject(GetObjectRequest.builder()
                        .bucket(storage.bucket())
                        .key(input.objectKey())
                        .build())) {
                    Files.copy(source, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                try (Workbook workbook = WorkbookFactory.create(temporary.toFile())) {
                    Sheet sheet = workbook.getSheet(input.sourceKey());
                    if (sheet == null) {
                        throw parseFailure(nodeId, nodeName, "Excel Sheet 不存在或已经变化", null);
                    }
                    List<Row> rows = new ArrayList<>();
                    for (int rowIndex = options.dataStartRowIndex();
                         rowIndex <= sheet.getLastRowNum();
                         rowIndex++) {
                        org.apache.poi.ss.usermodel.Row sourceRow = sheet.getRow(rowIndex);
                        if (sourceRow == null) {
                            continue;
                        }
                        Object[] values = new Object[executorColumns.size()];
                        boolean hasValue = false;
                        for (int columnIndex = 0; columnIndex < executorColumns.size(); columnIndex++) {
                            Cell cell = sourceRow.getCell(columnIndex, MissingCellPolicy.RETURN_BLANK_AS_NULL);
                            Object value = spreadsheetValue(cell);
                            values[columnIndex] = convert(value, executorColumns.get(columnIndex));
                            hasValue |= value != null;
                        }
                        if (hasValue) {
                            rows.add(RowFactory.create(values));
                        }
                    }
                    return rows.iterator();
                }
            } catch (FileDatasetReadException exception) {
                throw exception;
            } catch (NoSuchKeyException exception) {
                throw objectMissing(nodeId, nodeName, exception);
            } catch (S3Exception exception) {
                throw s3Failure(nodeId, nodeName, exception);
            } catch (RuntimeException | IOException exception) {
                throw normalizeFailure(nodeId, nodeName, exception);
            } finally {
                if (temporary != null) {
                    try {
                        Files.deleteIfExists(temporary);
                    } catch (IOException ignoredDelete) {
                        // Task temporary directories are cleaned independently.
                    }
                }
            }
        });
    }

    private static Dataset<Row> readGdb(
            SparkSession spark,
            RuntimeFileStorage storage,
            RuntimeFileInput input,
            List<CanvasColumnSchema> columns,
            StructType schema,
            String nodeId,
            String nodeName
    ) {
        List<RuntimeColumnSchema> runtimeColumns = runtimeColumns(columns);
        return singlePartition(spark, schema, ignored -> {
            List<CanvasColumnSchema> executorColumns = canvasColumns(runtimeColumns);
            S3Client client = s3Client(storage);
            FileGeodatabase database = null;
            FileGdbFeatureCursor cursor = null;
            try {
                database = FileGeodatabase.open(
                        S3FileGdbSource.create(
                                client,
                                new S3FileGdbLocation(storage.bucket(), input.materializedPrefix()),
                                S3FileGdbOptions.defaults()
                        ),
                        fullGdbOptions()
                );
                FileGdbSchema sourceSchema = database.schema(input.sourceKey());
                cursor = database.openCursor(input.sourceKey(), FileGdbReadOptions.limit(Integer.MAX_VALUE));
                FileGeodatabase openedDatabase = database;
                FileGdbFeatureCursor openedCursor = cursor;
                registerClose(openedCursor, openedDatabase, client);
                return mappingIterator(
                        openedCursor,
                        feature -> gdbRow(feature, sourceSchema, executorColumns)
                );
            } catch (RuntimeException exception) {
                closeQuietly(cursor, database, client);
                throw normalizeFailure(nodeId, nodeName, exception);
            }
        });
    }

    private static Dataset<Row> readShapefile(
            SparkSession spark,
            RuntimeFileStorage storage,
            RuntimeFileInput input,
            List<CanvasColumnSchema> columns,
            StructType schema,
            String nodeId,
            String nodeName
    ) {
        if (!(input.parsingOptions() instanceof RuntimeFileParsingOptions.Shp options)) {
            throw parseFailure(nodeId, nodeName, "文件解析参数与 Shapefile 格式不匹配", null);
        }
        List<RuntimeColumnSchema> runtimeColumns = runtimeColumns(columns);
        return singlePartition(spark, schema, ignored -> {
            List<CanvasColumnSchema> executorColumns = canvasColumns(runtimeColumns);
            S3Client client = s3Client(storage);
            ShapefileDataset dataset = null;
            ShapefileFeatureCursor cursor = null;
            try {
                String shpKey = joinKey(input.materializedPrefix(), input.sourceKey());
                dataset = ShapefileDataset.open(
                        S3ShapefileSource.create(
                                client,
                                S3ShapefileLocation.fromShpKey(storage.bucket(), shpKey),
                                S3ShapefileOptions.defaults()
                        ),
                        fullShapefileOptions(options)
                );
                cursor = dataset.openCursor(ShapefileReadOptions.limit(Integer.MAX_VALUE));
                ShapefileDataset openedDataset = dataset;
                ShapefileFeatureCursor openedCursor = cursor;
                registerClose(openedCursor, openedDataset, client);
                return mappingIterator(openedCursor, feature -> shapefileRow(feature, executorColumns));
            } catch (RuntimeException exception) {
                closeQuietly(cursor, dataset, client);
                throw normalizeFailure(nodeId, nodeName, exception);
            }
        });
    }

    private static Dataset<Row> singlePartition(
            SparkSession spark,
            StructType schema,
            MapPartitionsFunction<Long, Row> reader
    ) {
        return spark.range(1).coalesce(1).mapPartitions(reader, Encoders.row(schema));
    }

    private static Dataset<Row> guardReadFailure(
            Dataset<Row> source,
            StructType schema,
            String nodeId,
            String nodeName
    ) {
        return source.mapPartitions(
                (MapPartitionsFunction<Row, Row>)
                        rows -> guardedIterator(rows, nodeId, nodeName),
                Encoders.row(schema)
        );
    }

    private static Iterator<Row> guardedIterator(
            Iterator<Row> source,
            String nodeId,
            String nodeName
    ) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                try {
                    return source.hasNext();
                } catch (Throwable throwable) {
                    throw normalizeFailure(nodeId, nodeName, throwable);
                }
            }

            @Override
            public Row next() {
                try {
                    return source.next();
                } catch (Throwable throwable) {
                    throw normalizeFailure(nodeId, nodeName, throwable);
                }
            }
        };
    }

    private static Dataset<Row> projectToLogicalSchema(
            Dataset<Row> source,
            List<CanvasColumnSchema> columns,
            StructType targetSchema
    ) {
        Map<String, StructField> sourceFields = new LinkedHashMap<>();
        for (StructField field : source.schema().fields()) {
            sourceFields.put(field.name(), field);
        }
        List<Column> projections = new ArrayList<>(columns.size());
        for (int index = 0; index < columns.size(); index++) {
            CanvasColumnSchema column = columns.get(index);
            StructField sourceField = sourceFields.get(column.name());
            if (sourceField == null) {
                // Let Spark's analyzer produce the stable missing-column failure.
                projections.add(functions.col(quoted(column.name())).alias(column.name()));
                continue;
            }
            Column value = functions.col(quoted(column.name()));
            DataType sourceType = sourceField.dataType();
            DataType targetType = targetSchema.fields()[index].dataType();
            if (column.fieldType() == PlatformDataType.STRING
                    && (sourceType instanceof StructType
                    || sourceType instanceof ArrayType
                    || sourceType instanceof MapType)) {
                value = functions.to_json(value);
            } else if (!sourceType.sameType(targetType)) {
                value = value.cast(targetType);
            }
            projections.add(value.alias(column.name()));
        }
        return source.select(projections.toArray(Column[]::new));
    }

    private static String quoted(String name) {
        return "`" + name.replace("`", "``") + "`";
    }

    private static Map<String, Object> jsonRecord(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, fieldValue) -> result.put(String.valueOf(key), jsonScalar(fieldValue)));
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", jsonScalar(value));
        return result;
    }

    private static Object jsonScalar(Object value) {
        if (!(value instanceof Map<?, ?> || value instanceof List<?>)) {
            return value;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Nested JSON value cannot be serialized", exception);
        }
    }

    private static List<Map<String, Object>> jsonRecords(InputStream input, RuntimeFileInput runtime)
            throws IOException {
        RuntimeFileParsingOptions.Json options = (RuntimeFileParsingOptions.Json) runtime.parsingOptions();
        Object root = OBJECT_MAPPER.readValue(
                new InputStreamReader(input, Charset.forName(options.charset())),
                Object.class
        );
        Object selected = followPointer(root, options.rootPointer());
        List<?> records = selected instanceof List<?> list
                ? list
                : selected instanceof Map<?, ?> ? List.of(selected) : null;
        if (records == null) {
            throw new IllegalArgumentException("JSON root must be an object or array");
        }
        return records.stream().map(FileDatasetBatchReaderRegistry::jsonRecord).toList();
    }

    private static List<Map<String, Object>> jsonLinesRecords(InputStream input, RuntimeFileInput runtime)
            throws IOException {
        RuntimeFileParsingOptions.JsonLines options =
                (RuntimeFileParsingOptions.JsonLines) runtime.parsingOptions();
        String separator = lineSeparator(options.recordDelimiter());
        String content = new String(input.readAllBytes(), Charset.forName(options.charset()));
        String[] records = separator == null
                ? content.split("\\R")
                : content.split(java.util.regex.Pattern.quote(separator));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String record : records) {
            if (!record.isBlank()) {
                Object value = OBJECT_MAPPER.readValue(record, Object.class);
                rows.add(jsonRecord(value));
            }
        }
        return List.copyOf(rows);
    }

    private static Object followPointer(Object root, String pointer) {
        if (pointer == null || pointer.isBlank()) {
            return root;
        }
        Object current = root;
        for (String token : pointer.substring(1).split("/", -1)) {
            String decoded = token.replace("~1", "/").replace("~0", "~");
            if (current instanceof Map<?, ?> map && map.containsKey(decoded)) {
                current = map.get(decoded);
            } else if (current instanceof List<?> list) {
                current = list.get(Integer.parseInt(decoded));
            } else {
                throw new IllegalArgumentException("JSON Pointer does not resolve");
            }
        }
        return current;
    }

    private static Row gdbRow(
            FileGdbFeature feature,
            FileGdbSchema sourceSchema,
            List<CanvasColumnSchema> columns
    ) {
        Map<String, FileGdbField> fields = new LinkedHashMap<>();
        sourceSchema.fields().forEach(field -> fields.put(field.name(), field));
        Object[] values = new Object[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            CanvasColumnSchema column = columns.get(index);
            FileGdbField field = fields.get(column.name());
            Object value = column.fieldType() == PlatformDataType.GEOMETRY
                    ? FileDatasetGeometryConverter.convert(feature.geometry(), column.geometry())
                    : field == null ? null : feature.attribute(column.name());
            values[index] = convert(value, column);
        }
        return RowFactory.create(values);
    }

    private static Row shapefileRow(ShapefileFeature feature, List<CanvasColumnSchema> columns) {
        Object[] values = new Object[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            CanvasColumnSchema column = columns.get(index);
            Object value = column.fieldType() == PlatformDataType.GEOMETRY
                    ? FileDatasetGeometryConverter.convert(feature.geometry(), column.geometry())
                    : feature.attributes().containsKey(column.name())
                            ? feature.attribute(column.name())
                            : null;
            values[index] = convert(value, column);
        }
        return RowFactory.create(values);
    }

    private static Row row(Map<String, Object> values, List<CanvasColumnSchema> columns) {
        Object[] converted = new Object[columns.size()];
        for (int index = 0; index < columns.size(); index++) {
            CanvasColumnSchema column = columns.get(index);
            converted[index] = convert(values.get(column.name()), column);
        }
        return RowFactory.create(converted);
    }

    private static Object convert(Object value, CanvasColumnSchema column) {
        if (value == null) {
            return null;
        }
        String text = value instanceof String string ? string.trim() : String.valueOf(value);
        return switch (column.fieldType()) {
            case BOOLEAN -> value instanceof Boolean bool ? bool : Boolean.valueOf(text);
            case BYTE -> value instanceof Number number ? number.byteValue() : Byte.valueOf(text);
            case SHORT -> value instanceof Number number ? number.shortValue() : Short.valueOf(text);
            case INTEGER -> value instanceof Number number ? number.intValue() : Integer.valueOf(text);
            case LONG -> value instanceof Number number ? number.longValue() : Long.valueOf(text);
            case FLOAT -> value instanceof Number number ? number.floatValue() : Float.valueOf(text);
            case DOUBLE -> value instanceof Number number ? number.doubleValue() : Double.valueOf(text);
            case DECIMAL -> value instanceof BigDecimal decimal ? decimal : new BigDecimal(text);
            case STRING -> value instanceof String ? value : jsonOrString(value);
            case BINARY -> value instanceof byte[] bytes ? bytes : text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            case DATE -> value instanceof Date date ? date
                    : value instanceof LocalDate date ? Date.valueOf(date) : Date.valueOf(text);
            case TIMESTAMP -> value instanceof Timestamp timestamp ? timestamp
                    : value instanceof Instant instant ? Timestamp.from(instant)
                    : value instanceof OffsetDateTime offset ? Timestamp.from(offset.toInstant())
                    : value instanceof LocalDateTime local ? Timestamp.valueOf(local)
                    : Timestamp.valueOf(text.replace('T', ' '));
            case TIMESTAMP_NTZ -> value instanceof LocalDateTime local ? local
                    : value instanceof Timestamp timestamp ? timestamp.toLocalDateTime()
                    : LocalDateTime.parse(text.replace(' ', 'T'));
            case GEOMETRY -> value instanceof org.locationtech.jts.geom.Geometry geometry
                    ? geometry
                    : throwGeometryValue();
        };
    }

    private static Object throwGeometryValue() {
        throw new IllegalArgumentException("FILE_GEOMETRY_INVALID: 文件 Geometry 运行时值无效");
    }

    private static List<RuntimeColumnSchema> runtimeColumns(List<CanvasColumnSchema> columns) {
        return columns.stream().map(RuntimeColumnSchema::from).toList();
    }

    private static List<CanvasColumnSchema> canvasColumns(List<RuntimeColumnSchema> columns) {
        return columns.stream().map(RuntimeColumnSchema::toCanvas).toList();
    }

    /** Explicit Spark closure payload; stable Canvas contracts stay independent from Java serialization. */
    private record RuntimeColumnSchema(
            String name,
            PlatformDataType fieldType,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable,
            String defaultValue,
            boolean autoIncrement,
            boolean generated,
            String comment,
            GeometryKind geometryKind,
            String crsAuthority,
            Integer crsCode,
            CoordinateDimension coordinateDimension
    ) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private static RuntimeColumnSchema from(CanvasColumnSchema column) {
            GeometryTypeDefinition geometry = column.geometry();
            return new RuntimeColumnSchema(
                    column.name(), column.fieldType(), column.length(), column.precision(), column.scale(),
                    column.nullable(), column.defaultValue(), column.autoIncrement(), column.generated(),
                    column.comment(), geometry == null ? null : geometry.kind(),
                    geometry == null ? null : geometry.crs().authority(),
                    geometry == null ? null : geometry.crs().code(),
                    geometry == null ? null : geometry.dimension()
            );
        }

        private CanvasColumnSchema toCanvas() {
            GeometryTypeDefinition geometry = geometryKind == null ? null : new GeometryTypeDefinition(
                    geometryKind,
                    new CrsReference(crsAuthority, crsCode),
                    coordinateDimension
            );
            return new CanvasColumnSchema(
                    name, fieldType, length, precision, scale, nullable, defaultValue,
                    autoIncrement, generated, comment, geometry
            );
        }
    }

    private static Object spreadsheetValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
        return switch (type) {
            case BOOLEAN -> cell.getBooleanCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue()
                    : BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> cell.getStringCellValue();
            case BLANK -> null;
            case ERROR -> cell.getErrorCellValue();
            default -> cell.toString();
        };
    }

    private static String jsonOrString(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?> || value.getClass().isRecord()) {
            try {
                return OBJECT_MAPPER.writeValueAsString(value);
            } catch (IOException ignored) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private static InputStream objectStream(
            S3Client client,
            RuntimeFileStorage storage,
            RuntimeFileInput input,
            String nodeId,
            String nodeName
    ) {
        try {
            return client.getObject(GetObjectRequest.builder()
                    .bucket(storage.bucket())
                    .key(input.objectKey())
                    .build());
        } catch (NoSuchKeyException exception) {
            throw objectMissing(nodeId, nodeName, exception);
        } catch (S3Exception exception) {
            throw s3Failure(nodeId, nodeName, exception);
        }
    }

    private static InputStream decompress(InputStream input, FileDatasetCompression compression) throws IOException {
        return compression == FileDatasetCompression.GZIP ? new GZIPInputStream(input) : input;
    }

    private static S3Client s3Client(RuntimeFileStorage storage) {
        return S3Client.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .region(Region.of(storage.region()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        storage.accessKey(), storage.secretKey()
                )))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.pathStyleAccess())
                        .build())
                .build();
    }

    private static void configureS3A(Configuration configuration, RuntimeFileStorage storage) {
        configuration.set("fs.s3a.endpoint", storage.endpoint());
        configuration.set("fs.s3a.endpoint.region", storage.region());
        configuration.set("fs.s3a.access.key", storage.accessKey());
        configuration.set("fs.s3a.secret.key", storage.secretKey());
        configuration.set("fs.s3a.aws.credentials.provider",
                "org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider");
        configuration.setBoolean("fs.s3a.path.style.access", storage.pathStyleAccess());
        configuration.setBoolean("fs.s3a.connection.ssl.enabled",
                storage.endpoint().toLowerCase(java.util.Locale.ROOT).startsWith("https://"));
    }

    private static String objectUri(RuntimeFileStorage storage, RuntimeFileInput input) {
        return "s3a://" + storage.bucket() + "/" + input.objectKey();
    }

    private static String lineSeparator(FileRecordDelimiter delimiter) {
        if (delimiter == null || delimiter == FileRecordDelimiter.AUTO) {
            return null;
        }
        return switch (delimiter) {
            case LF -> "\n";
            case CRLF -> "\r\n";
            case CR -> "\r";
            case AUTO -> null;
        };
    }

    private static FileGdbOpenOptions fullGdbOptions() {
        FileGdbReadLimits defaults = FileGdbReadLimits.defaults();
        return new FileGdbOpenOptions(
                false,
                false,
                new FileGdbReadLimits(
                        defaults.maxTableFileBytes(),
                        defaults.maxFields(),
                        defaults.maxRecordBytes(),
                        defaults.maxStringBytes(),
                        defaults.maxBinaryBytes(),
                        defaults.maxGeometryParts(),
                        defaults.maxGeometryPoints(),
                        defaults.maxIndexSlotsPerCursor(),
                        Integer.MAX_VALUE
                )
        );
    }

    private static ShapefileOpenOptions fullShapefileOptions(RuntimeFileParsingOptions.Shp options) {
        ShapefileReadLimits defaults = ShapefileReadLimits.defaults();
        ShapefileReadLimits limits = new ShapefileReadLimits(
                defaults.maxComponentFileBytes(),
                defaults.maxFields(),
                defaults.maxRecordBytes(),
                defaults.maxMetadataBytes(),
                defaults.maxParts(),
                defaults.maxGeometryPoints(),
                defaults.maxIndexRecords(),
                Integer.MAX_VALUE
        );
        Charset override = options.dbfCharsetOverride() == null
                ? null : Charset.forName(options.dbfCharsetOverride());
        return new ShapefileOpenOptions(
                limits,
                override,
                Charset.forName(options.dbfFallbackCharset()),
                false
        );
    }

    private static <S, T> Iterator<T> mappingIterator(
            Iterator<S> source,
            java.util.function.Function<S, T> mapper
    ) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return source.hasNext();
            }

            @Override
            public T next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return mapper.apply(source.next());
            }
        };
    }

    private static void registerClose(AutoCloseable... resources) {
        TaskContext context = TaskContext.get();
        if (context != null) {
            context.addTaskCompletionListener(ignored -> {
                closeQuietly(resources);
                return null;
            });
        }
    }

    private static void closeQuietly(AutoCloseable... resources) {
        for (AutoCloseable resource : resources) {
            if (resource == null) {
                continue;
            }
            try {
                resource.close();
            } catch (Exception ignored) {
                // The primary read failure is retained.
            }
        }
    }

    private static String joinKey(String prefix, String child) {
        return prefix.endsWith("/") ? prefix + child : prefix + "/" + child;
    }

    static FileDatasetReadException normalizeFailure(
            String nodeId,
            String nodeName,
            Throwable throwable
    ) {
        FileDatasetReadException existing = findCause(throwable, FileDatasetReadException.class);
        if (existing != null) {
            if (Objects.equals(nodeName, existing.nodeName())) {
                return existing;
            }
            return new FileDatasetReadException(
                    existing.code(),
                    existing.getMessage(),
                    existing.nodeId(),
                    nodeName,
                    existing.retryable(),
                    existing
            );
        }
        if (findCause(throwable, NoSuchKeyException.class) != null
                || findCause(throwable, FileNotFoundException.class) != null
                || messageContains(throwable, "PATH_NOT_FOUND")) {
            return objectMissing(nodeId, nodeName, throwable);
        }
        S3Exception s3Exception = findCause(throwable, S3Exception.class);
        if (s3Exception != null) {
            return s3Failure(nodeId, nodeName, s3Exception);
        }
        if (findCause(throwable, ConnectException.class) != null
                || findCause(throwable, SocketException.class) != null
                || findCause(throwable, SocketTimeoutException.class) != null
                || findCause(throwable, SdkClientException.class) != null) {
            return storageUnavailable(nodeId, nodeName, throwable);
        }
        return parseFailure(nodeId, nodeName, "文件数据集内容解析失败", throwable);
    }

    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static boolean messageContains(Throwable throwable, String token) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(token)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static FileDatasetReadException objectMissing(
            String nodeId,
            String nodeName,
            Throwable cause
    ) {
        return new FileDatasetReadException(
                "FILE_DATASET_OBJECT_NOT_FOUND",
                "文件数据集对象不存在",
                nodeId,
                nodeName,
                false,
                cause
        );
    }

    private static FileDatasetReadException s3Failure(
            String nodeId,
            String nodeName,
            S3Exception cause
    ) {
        if (cause.statusCode() == 404) {
            return objectMissing(nodeId, nodeName, cause);
        }
        return storageUnavailable(nodeId, nodeName, cause);
    }

    private static FileDatasetReadException storageUnavailable(
            String nodeId,
            String nodeName,
            Throwable cause
    ) {
        return new FileDatasetReadException(
                "FILE_DATASET_STORAGE_UNAVAILABLE",
                "文件数据集存储暂不可用",
                nodeId,
                nodeName,
                true,
                cause
        );
    }

    private static FileDatasetReadException parseFailure(
            String nodeId,
            String nodeName,
            String message,
            Throwable cause
    ) {
        return new FileDatasetReadException(
                "FILE_DATASET_PARSE_FAILED",
                message,
                nodeId,
                nodeName,
                false,
                cause
        );
    }
}
