package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.QualitySampleArtifactUpload;
import cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.datascalpel.taskengine.contract.QualitySampleColumn;
import cn.superhuang.datascalpel.taskengine.contract.QualitySampleResult;
import cn.superhuang.datascalpel.taskengine.contract.QualitySampleStatus;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.LocalOutputFile;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.apache.parquet.schema.Types;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.length;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.substring;

/** Writes bounded quality samples on the Driver without exposing object-store credentials. */
final class QualitySampleArtifactWriter {
    static final long MAXIMUM_RUN_BYTES = 100L * 1024 * 1024;
    static final int MAXIMUM_STRING_LENGTH = 4096;
    private static final PlatformTypeDefinition STRING = PlatformTypeDefinition.string(null);
    private static final PlatformTypeDefinition BOOLEAN = PlatformTypeDefinition.of(PlatformDataType.BOOLEAN);

    private final int sampleLimit;
    private final Path sampleDirectory;
    private final RunnerArtifactAccess artifactAccess;
    private final Map<UUID, QualitySampleArtifactUpload> uploads;
    private long uploadedBytes;

    QualitySampleArtifactWriter(
            int sampleLimit,
            TaskExecutionLaunchDescriptor launch,
            Path workDirectory,
            RunnerArtifactAccess artifactAccess
    ) {
        this.sampleLimit = sampleLimit;
        this.sampleDirectory = workDirectory.resolve("quality-samples");
        this.artifactAccess = artifactAccess;
        this.uploads = new LinkedHashMap<>();
        launch.qualitySamples().forEach(upload -> uploads.put(upload.ruleId(), upload));
    }

    private QualitySampleArtifactWriter(Path workDirectory) {
        this.sampleLimit = 0;
        this.sampleDirectory = workDirectory.resolve("quality-samples");
        this.artifactAccess = null;
        this.uploads = Map.of();
    }

    static QualitySampleArtifactWriter disabled(Path workDirectory) {
        return new QualitySampleArtifactWriter(workDirectory);
    }

    QualitySampleResult write(
            UUID ruleId,
            Dataset<Row> invalidRows,
            List<ModelQualityExecutionPayload.QualityFieldSnapshot> modelFields,
            List<UUID> involvedFieldIds,
            List<DiagnosticColumn> diagnostics,
            long violationRows
    ) {
        if (sampleLimit < 1) return QualitySampleResult.state(QualitySampleStatus.DISABLED);
        QualitySampleArtifactUpload upload = uploads.get(ruleId);
        if (upload == null) {
            throw new RunnerExecutionException(
                    "QUALITY_SAMPLE_UPLOAD_MISSING", "质检失败样本缺少受控上传地址", null);
        }
        if (invalidRows == null || violationRows < 1) {
            throw new RunnerExecutionException(
                    "QUALITY_SAMPLE_SOURCE_INVALID", "质检失败样本来源无效", null);
        }

        List<ModelQualityExecutionPayload.QualityFieldSnapshot> ordered = modelFields.stream()
                .sorted(Comparator.comparingInt(ModelQualityExecutionPayload.QualityFieldSnapshot::sortOrder))
                .toList();
        Set<UUID> involved = new HashSet<>(involvedFieldIds);
        List<ModelQualityExecutionPayload.QualityFieldSnapshot> primaryKeys = ordered.stream()
                .filter(ModelQualityExecutionPayload.QualityFieldSnapshot::primaryKey).toList();
        LinkedHashMap<UUID, ModelQualityExecutionPayload.QualityFieldSnapshot> selected = new LinkedHashMap<>();
        ordered.stream().filter(ModelQualityExecutionPayload.QualityFieldSnapshot::primaryKey)
                .filter(QualitySampleArtifactWriter::safeSourceField)
                .forEach(field -> selected.put(field.id(), field));
        ordered.stream().filter(field -> involved.contains(field.id()))
                .filter(QualitySampleArtifactWriter::safeSourceField)
                .forEach(field -> selected.put(field.id(), field));

        Set<String> occupiedCodes = new HashSet<>();
        modelFields.forEach(field -> occupiedCodes.add(field.code()));
        List<Column> projections = new ArrayList<>();
        List<QualitySampleColumn> columns = new ArrayList<>();
        Column stringTruncated = lit(false);
        for (ModelQualityExecutionPayload.QualityFieldSnapshot field : selected.values()) {
            Column source = col(field.code());
            if (field.type().type() == PlatformDataType.STRING) {
                stringTruncated = stringTruncated.or(source.isNotNull().and(length(source).gt(MAXIMUM_STRING_LENGTH)));
                source = substring(source, 1, MAXIMUM_STRING_LENGTH);
            }
            projections.add(source.alias(field.code()));
            columns.add(new QualitySampleColumn(
                    field.id(), field.code(), field.name(), field.type(), field.primaryKey(), false));
        }
        for (DiagnosticColumn diagnostic : diagnostics) {
            String code = uniqueDiagnosticCode(diagnostic.preferredCode(), occupiedCodes);
            projections.add(diagnostic.expression().alias(code));
            columns.add(new QualitySampleColumn(null, code, diagnostic.name(), diagnostic.type(), false, true));
        }
        String truncationCode = uniqueDiagnosticCode("__ds_string_truncated", occupiedCodes);
        projections.add(stringTruncated.alias(truncationCode));
        columns.add(new QualitySampleColumn(
                null, truncationCode, "字符串是否截断", BOOLEAN, false, true));

        List<Row> rows;
        try {
            rows = invalidRows.select(projections.toArray(Column[]::new)).limit(sampleLimit).collectAsList();
        } catch (RuntimeException exception) {
            throw new RunnerExecutionException(
                    "QUALITY_SAMPLE_COLLECTION_FAILED", "无法收集质检失败样本", null, exception);
        }
        long expectedRows = Math.min(violationRows, sampleLimit);
        if (rows.size() != expectedRows) {
            throw new RunnerExecutionException(
                    "QUALITY_SAMPLE_COUNT_MISMATCH", "质检失败样本数量与异常指标不一致", null);
        }

        Path file = sampleDirectory.resolve(ruleId + ".parquet");
        try {
            Files.createDirectories(sampleDirectory);
            writeParquet(file, columns, rows);
            long size = Files.size(file);
            if (size > upload.maximumBytes() || size < 8 || uploadedBytes + size > MAXIMUM_RUN_BYTES) {
                throw new RunnerExecutionException(
                        "QUALITY_SAMPLE_SIZE_EXCEEDED", "质检失败样本超过安全大小上限", null);
            }
            byte[] content = Files.readAllBytes(file);
            artifactAccess.upload(upload.putUrl(), content, "application/vnd.apache.parquet");
            uploadedBytes += size;
            boolean rowLocatable = !primaryKeys.isEmpty()
                    && primaryKeys.stream().allMatch(QualitySampleArtifactWriter::safeSourceField);
            return new QualitySampleResult(
                    QualitySampleStatus.AVAILABLE, (long) rows.size(), violationRows,
                    rows.size() < violationRows, size, sha256(content), rowLocatable, columns);
        } catch (RunnerExecutionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RunnerExecutionException(
                    "QUALITY_SAMPLE_UPLOAD_FAILED", "质检失败样本生成或上传失败", null, exception);
        } finally {
            try {
                Files.deleteIfExists(file);
            } catch (Exception ignored) {
                // The Runner work directory is ephemeral and contains no user-facing artifact reference.
            }
        }
    }

    private static void writeParquet(
            Path file,
            List<QualitySampleColumn> columns,
            List<Row> rows
    ) throws Exception {
        List<Type> parquetFields = columns.stream().map(QualitySampleArtifactWriter::parquetType).toList();
        MessageType schema = new MessageType("data_scalpel_quality_sample", parquetFields);
        SimpleGroupFactory groups = new SimpleGroupFactory(schema);
        try (ParquetWriter<Group> writer = ExampleParquetWriter.builder(new LocalOutputFile(file))
                .withType(schema)
                .withCompressionCodec(CompressionCodecName.UNCOMPRESSED)
                .build()) {
            for (Row row : rows) {
                Group group = groups.newGroup();
                for (int index = 0; index < columns.size(); index++) {
                    if (!row.isNullAt(index)) append(group, columns.get(index), row.get(index));
                }
                writer.write(group);
            }
        }
    }

    private static Type parquetType(QualitySampleColumn column) {
        PrimitiveType.PrimitiveTypeName physical = switch (column.type().type()) {
            case BOOLEAN -> PrimitiveType.PrimitiveTypeName.BOOLEAN;
            case BYTE, SHORT, INTEGER -> PrimitiveType.PrimitiveTypeName.INT32;
            case FLOAT -> PrimitiveType.PrimitiveTypeName.FLOAT;
            case DOUBLE -> PrimitiveType.PrimitiveTypeName.DOUBLE;
            default -> PrimitiveType.PrimitiveTypeName.BINARY;
        };
        Types.PrimitiveBuilder<PrimitiveType> builder = Types.optional(physical);
        if (physical == PrimitiveType.PrimitiveTypeName.BINARY) {
            builder = builder.as(org.apache.parquet.schema.LogicalTypeAnnotation.stringType());
        }
        return builder.named(column.code());
    }

    private static void append(Group group, QualitySampleColumn column, Object value) {
        switch (column.type().type()) {
            case BOOLEAN -> group.append(column.code(), (Boolean) value);
            case BYTE, SHORT, INTEGER -> group.append(column.code(), ((Number) value).intValue());
            case FLOAT -> group.append(column.code(), ((Number) value).floatValue());
            case DOUBLE -> group.append(column.code(), ((Number) value).doubleValue());
            case TIMESTAMP -> group.append(column.code(), timestamp(value));
            case TIMESTAMP_NTZ -> group.append(column.code(), timestampNtz(value));
            case DECIMAL -> group.append(column.code(), ((BigDecimal) value).toPlainString());
            default -> group.append(column.code(), String.valueOf(value));
        }
    }

    private static String timestamp(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().toString();
        if (value instanceof Instant instant) return instant.toString();
        return String.valueOf(value);
    }

    private static String timestampNtz(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        if (value instanceof LocalDateTime dateTime) return dateTime.toString();
        return String.valueOf(value);
    }

    private static boolean safeSourceField(ModelQualityExecutionPayload.QualityFieldSnapshot field) {
        return field.type().type() != PlatformDataType.BINARY
                && field.type().type() != PlatformDataType.GEOMETRY;
    }

    private static String uniqueDiagnosticCode(String preferred, Set<String> occupied) {
        String candidate = preferred;
        for (int suffix = 2; occupied.contains(candidate); suffix++) candidate = preferred + "_" + suffix;
        occupied.add(candidate);
        return candidate;
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    record DiagnosticColumn(
            String preferredCode,
            String name,
            PlatformTypeDefinition type,
            Column expression
    ) {
        DiagnosticColumn {
            if (preferredCode == null || !preferredCode.startsWith("__ds_") || name == null
                    || name.isBlank() || type == null || expression == null) {
                throw new IllegalArgumentException("质检样本诊断字段无效");
            }
        }

        static DiagnosticColumn text(String code, String name, Column expression) {
            return new DiagnosticColumn(code, name, STRING, expression);
        }
    }
}
