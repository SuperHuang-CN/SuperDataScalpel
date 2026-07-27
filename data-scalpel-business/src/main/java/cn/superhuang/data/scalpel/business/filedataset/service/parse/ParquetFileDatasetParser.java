package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.GroupValueSource;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.api.ReadSupport;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.LocalInputFile;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads a bounded Parquet preview while taking field structure from the Parquet schema. */
@Component
public class ParquetFileDatasetParser implements FileDatasetParser {

    private final ObjectMapper objectMapper;

    public ParquetFileDatasetParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.PARQUET;
    }

    @Override
    public FileDatasetParserInputMode inputMode() {
        return FileDatasetParserInputMode.LOCAL_FILE;
    }

    @Override
    public ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException {
        if (!(configuration instanceof FileDatasetParsingConfiguration.Parquet)) {
            throw new FileDatasetParsingException("Parquet 解析参数无效");
        }
        if (recordLimit < 1) {
            throw new IllegalArgumentException("抽样记录数必须大于零");
        }
        Path path = FileDatasetParseSource.requireLocalFile(source);
        try {
            InputFile inputFile = new LocalInputFile(path);
            ParquetMetadata metadata = ParquetFileReader.readFooter(inputFile, ParquetMetadataConverter.NO_FILTER);
            MessageType schema = metadata.getFileMetaData().getSchema();
            long rowCount = metadata.getBlocks().stream().mapToLong(block -> block.getRowCount()).sum();
            List<Field> fields = fields(schema);
            List<Map<String, Object>> rows = new ArrayList<>();
            boolean truncated = false;
            try (ParquetReader<Group> reader = new GroupReaderBuilder(inputFile).build()) {
                Group row;
                while ((row = reader.read()) != null) {
                    if (rows.size() >= recordLimit) {
                        truncated = true;
                        break;
                    }
                    rows.add(row(row, schema));
                }
            }
            Map<String, Object> sourceMetadata = new LinkedHashMap<>();
            if (metadata.getFileMetaData().getCreatedBy() != null) {
                sourceMetadata.put("parquetCreatedBy", metadata.getFileMetaData().getCreatedBy());
            }
            return new ParseResult(
                    fields, rows, truncated || rowCount > rows.size(), true, sourceMetadata, rowCount
            );
        } catch (FileDatasetParsingException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new FileDatasetParsingException("Parquet 文件内容无效", exception);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("无法读取 Parquet 文件", exception);
        }
    }

    private List<Field> fields(MessageType schema) {
        List<Field> fields = new ArrayList<>(schema.getFieldCount());
        for (int index = 0; index < schema.getFieldCount(); index++) {
            Type type = schema.getType(index);
            fields.add(new Field(
                    type.getName(), index, typeDefinition(type), type.getRepetition() != Type.Repetition.REQUIRED
            ));
        }
        return fields;
    }

    private Map<String, Object> row(Group row, MessageType schema) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < schema.getFieldCount(); index++) {
            Type type = schema.getType(index);
            values.put(type.getName(), previewValue(fieldValue(row, index, type), logicalType(type)));
        }
        return values;
    }

    private Object fieldValue(GroupValueSource source, int fieldIndex, Type type) {
        int repetitionCount = source.getFieldRepetitionCount(fieldIndex);
        if (repetitionCount == 0) {
            return null;
        }
        if (type.getRepetition() == Type.Repetition.REPEATED) {
            List<Object> values = new ArrayList<>(repetitionCount);
            for (int index = 0; index < repetitionCount; index++) {
                values.add(valueAt(source, fieldIndex, index, type));
            }
            return values;
        }
        return valueAt(source, fieldIndex, 0, type);
    }

    private Object valueAt(GroupValueSource source, int fieldIndex, int repetitionIndex, Type type) {
        if (type.isPrimitive()) {
            return primitiveValue(source, fieldIndex, repetitionIndex, type.asPrimitiveType());
        }
        GroupValueSource group = source.getGroup(fieldIndex, repetitionIndex);
        LogicalTypeAnnotation annotation = type.getLogicalTypeAnnotation();
        if (annotation instanceof LogicalTypeAnnotation.ListLogicalTypeAnnotation) {
            return listValue(group, type.asGroupType());
        }
        if (annotation instanceof LogicalTypeAnnotation.MapLogicalTypeAnnotation) {
            return mapValue(group, type.asGroupType());
        }
        return groupValue(group, type.asGroupType());
    }

    private Object primitiveValue(
            GroupValueSource source,
            int fieldIndex,
            int repetitionIndex,
            PrimitiveType type
    ) {
        LogicalTypeAnnotation annotation = type.getLogicalTypeAnnotation();
        if (annotation instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation decimal) {
            return decimalValue(source, fieldIndex, repetitionIndex, type, decimal.getScale());
        }
        if (annotation instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation) {
            return LocalDate.ofEpochDay(source.getInteger(fieldIndex, repetitionIndex));
        }
        if (annotation instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation time) {
            return timeValue(integerValue(source, fieldIndex, repetitionIndex, type), time.getUnit());
        }
        if (annotation instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation timestamp) {
            return timestampValue(
                    source.getLong(fieldIndex, repetitionIndex), timestamp.getUnit(), timestamp.isAdjustedToUTC()
            );
        }
        if (annotation instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation integer) {
            return annotatedIntegerValue(source, fieldIndex, repetitionIndex, type, integer);
        }
        if (annotation instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation
                || annotation instanceof LogicalTypeAnnotation.EnumLogicalTypeAnnotation) {
            return source.getBinary(fieldIndex, repetitionIndex).toStringUsingUTF8();
        }
        if (annotation instanceof LogicalTypeAnnotation.UUIDLogicalTypeAnnotation) {
            return uuidValue(source.getBinary(fieldIndex, repetitionIndex));
        }
        if (annotation instanceof LogicalTypeAnnotation.JsonLogicalTypeAnnotation) {
            return source.getBinary(fieldIndex, repetitionIndex).toStringUsingUTF8();
        }

        return switch (type.getPrimitiveTypeName()) {
            case BOOLEAN -> source.getBoolean(fieldIndex, repetitionIndex);
            case INT32 -> source.getInteger(fieldIndex, repetitionIndex);
            case INT64 -> source.getLong(fieldIndex, repetitionIndex);
            case FLOAT -> source.getFloat(fieldIndex, repetitionIndex);
            case DOUBLE -> source.getDouble(fieldIndex, repetitionIndex);
            case BINARY, FIXED_LEN_BYTE_ARRAY -> base64(source.getBinary(fieldIndex, repetitionIndex));
            case INT96 -> int96Value(source.getInt96(fieldIndex, repetitionIndex));
        };
    }

    private List<Object> listValue(GroupValueSource container, GroupType listType) {
        if (listType.getFieldCount() == 0) {
            return List.of();
        }
        Type repeatedType = listType.getType(0);
        int count = container.getFieldRepetitionCount(0);
        List<Object> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (repeatedType.isPrimitive()) {
                values.add(primitiveValue(container, 0, index, repeatedType.asPrimitiveType()));
                continue;
            }
            GroupValueSource repeatedGroup = container.getGroup(0, index);
            GroupType repeatedGroupType = repeatedType.asGroupType();
            if (repeatedGroupType.getFieldCount() == 1) {
                Type elementType = repeatedGroupType.getType(0);
                values.add(fieldValue(repeatedGroup, 0, elementType));
            } else {
                values.add(groupValue(repeatedGroup, repeatedGroupType));
            }
        }
        return values;
    }

    private Map<String, Object> mapValue(GroupValueSource container, GroupType mapType) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (mapType.getFieldCount() == 0) {
            return values;
        }
        Type entryType = mapType.getType(0);
        if (entryType.isPrimitive()) {
            return values;
        }
        GroupType entryGroupType = entryType.asGroupType();
        int count = container.getFieldRepetitionCount(0);
        for (int index = 0; index < count; index++) {
            GroupValueSource entry = container.getGroup(0, index);
            if (entryGroupType.getFieldCount() == 0) {
                continue;
            }
            Object key = fieldValue(entry, 0, entryGroupType.getType(0));
            Object value = entryGroupType.getFieldCount() > 1 ? fieldValue(entry, 1, entryGroupType.getType(1)) : null;
            values.put(String.valueOf(key), value);
        }
        return values;
    }

    private Map<String, Object> groupValue(GroupValueSource source, GroupType type) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < type.getFieldCount(); index++) {
            Type childType = type.getType(index);
            values.put(childType.getName(), fieldValue(source, index, childType));
        }
        return values;
    }

    private Object previewValue(Object value, LogicalType logicalType) {
        if (value == null || (logicalType != LogicalType.ARRAY && logicalType != LogicalType.JSON)) {
            return value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("无法序列化 Parquet 嵌套字段", exception);
        }
    }

    private static LogicalType logicalType(Type type) {
        if (!type.isPrimitive()) {
            return type.getLogicalTypeAnnotation() instanceof LogicalTypeAnnotation.ListLogicalTypeAnnotation
                    ? LogicalType.ARRAY : LogicalType.JSON;
        }
        LogicalTypeAnnotation annotation = type.getLogicalTypeAnnotation();
        if (annotation instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation) {
            return LogicalType.DECIMAL;
        }
        if (annotation instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation) {
            return LogicalType.DATE;
        }
        if (annotation instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation) {
            return LogicalType.TIME;
        }
        if (annotation instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation) {
            return LogicalType.DATETIME;
        }
        if (annotation instanceof LogicalTypeAnnotation.JsonLogicalTypeAnnotation) {
            return LogicalType.JSON;
        }
        if (annotation instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation
                || annotation instanceof LogicalTypeAnnotation.EnumLogicalTypeAnnotation
                || annotation instanceof LogicalTypeAnnotation.UUIDLogicalTypeAnnotation) {
            return LogicalType.STRING;
        }
        if (annotation instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation integer
                && !integer.isSigned() && integer.getBitWidth() == Long.SIZE) {
            return LogicalType.DECIMAL;
        }
        return switch (type.asPrimitiveType().getPrimitiveTypeName()) {
            case BOOLEAN -> LogicalType.BOOLEAN;
            case INT32, INT64 -> LogicalType.INTEGER;
            case FLOAT, DOUBLE -> LogicalType.DECIMAL;
            case INT96 -> LogicalType.DATETIME;
            case BINARY, FIXED_LEN_BYTE_ARRAY -> LogicalType.BINARY;
        };
    }

    private static PlatformTypeDefinition typeDefinition(Type type) {
        if (!type.isPrimitive()) {
            return PlatformTypeDefinition.string(null);
        }
        LogicalTypeAnnotation annotation = type.getLogicalTypeAnnotation();
        if (annotation instanceof LogicalTypeAnnotation.DecimalLogicalTypeAnnotation decimal) {
            return FileDatasetTypeDefinitions.decimal(decimal.getPrecision(), decimal.getScale());
        }
        if (annotation instanceof LogicalTypeAnnotation.DateLogicalTypeAnnotation) {
            return PlatformTypeDefinition.of(PlatformDataType.DATE);
        }
        if (annotation instanceof LogicalTypeAnnotation.TimeLogicalTypeAnnotation) {
            return PlatformTypeDefinition.string(null);
        }
        if (annotation instanceof LogicalTypeAnnotation.TimestampLogicalTypeAnnotation timestamp) {
            return PlatformTypeDefinition.of(
                    timestamp.isAdjustedToUTC() ? PlatformDataType.TIMESTAMP : PlatformDataType.TIMESTAMP_NTZ
            );
        }
        if (annotation instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation integer
                && !integer.isSigned() && integer.getBitWidth() == Long.SIZE) {
            return PlatformTypeDefinition.decimal(20, 0);
        }
        if (annotation instanceof LogicalTypeAnnotation.IntLogicalTypeAnnotation) {
            return PlatformTypeDefinition.of(PlatformDataType.LONG);
        }
        if (annotation instanceof LogicalTypeAnnotation.StringLogicalTypeAnnotation
                || annotation instanceof LogicalTypeAnnotation.EnumLogicalTypeAnnotation
                || annotation instanceof LogicalTypeAnnotation.UUIDLogicalTypeAnnotation
                || annotation instanceof LogicalTypeAnnotation.JsonLogicalTypeAnnotation) {
            return PlatformTypeDefinition.string(null);
        }
        return switch (type.asPrimitiveType().getPrimitiveTypeName()) {
            case BOOLEAN -> PlatformTypeDefinition.of(PlatformDataType.BOOLEAN);
            case INT32, INT64 -> PlatformTypeDefinition.of(PlatformDataType.LONG);
            case FLOAT -> PlatformTypeDefinition.of(PlatformDataType.FLOAT);
            case DOUBLE -> PlatformTypeDefinition.of(PlatformDataType.DOUBLE);
            case INT96 -> PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP_NTZ);
            case BINARY, FIXED_LEN_BYTE_ARRAY -> PlatformTypeDefinition.of(PlatformDataType.BINARY);
        };
    }

    private static Object decimalValue(
            GroupValueSource source,
            int fieldIndex,
            int repetitionIndex,
            PrimitiveType type,
            int scale
    ) {
        BigInteger unscaled = switch (type.getPrimitiveTypeName()) {
            case INT32 -> BigInteger.valueOf(source.getInteger(fieldIndex, repetitionIndex));
            case INT64 -> BigInteger.valueOf(source.getLong(fieldIndex, repetitionIndex));
            case BINARY, FIXED_LEN_BYTE_ARRAY -> new BigInteger(source.getBinary(fieldIndex, repetitionIndex).getBytes());
            default -> throw new FileDatasetParsingException("Parquet Decimal 物理类型无效");
        };
        return new BigDecimal(unscaled, scale);
    }

    private static Object annotatedIntegerValue(
            GroupValueSource source,
            int fieldIndex,
            int repetitionIndex,
            PrimitiveType type,
            LogicalTypeAnnotation.IntLogicalTypeAnnotation annotation
    ) {
        if (type.getPrimitiveTypeName() == PrimitiveType.PrimitiveTypeName.INT32) {
            int value = source.getInteger(fieldIndex, repetitionIndex);
            return annotation.isSigned() ? value : Integer.toUnsignedLong(value);
        }
        long value = source.getLong(fieldIndex, repetitionIndex);
        return annotation.isSigned() ? value : new BigInteger(Long.toUnsignedString(value));
    }

    private static long integerValue(GroupValueSource source, int fieldIndex, int repetitionIndex, PrimitiveType type) {
        return type.getPrimitiveTypeName() == PrimitiveType.PrimitiveTypeName.INT32
                ? source.getInteger(fieldIndex, repetitionIndex) : source.getLong(fieldIndex, repetitionIndex);
    }

    private static LocalTime timeValue(long value, LogicalTypeAnnotation.TimeUnit unit) {
        long nanos = switch (unit) {
            case MILLIS -> Math.multiplyExact(value, 1_000_000L);
            case MICROS -> Math.multiplyExact(value, 1_000L);
            case NANOS -> value;
        };
        try {
            return LocalTime.ofNanoOfDay(nanos);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("Parquet TIME 值无效", exception);
        }
    }

    private static Object timestampValue(long value, LogicalTypeAnnotation.TimeUnit unit, boolean adjustedToUtc) {
        Instant instant = switch (unit) {
            case MILLIS -> Instant.ofEpochMilli(value);
            case MICROS -> Instant.ofEpochSecond(Math.floorDiv(value, 1_000_000L), Math.floorMod(value, 1_000_000L) * 1_000L);
            case NANOS -> Instant.ofEpochSecond(Math.floorDiv(value, 1_000_000_000L), Math.floorMod(value, 1_000_000_000L));
        };
        return adjustedToUtc ? instant.toString() : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String uuidValue(Binary value) {
        byte[] bytes = value.getBytes();
        if (bytes.length != 16) {
            return base64(value);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        return new java.util.UUID(buffer.getLong(), buffer.getLong()).toString();
    }

    private static LocalDateTime int96Value(Binary value) {
        byte[] bytes = value.getBytes();
        if (bytes.length != 12) {
            throw new FileDatasetParsingException("Parquet INT96 值无效");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        long nanosOfDay = buffer.getLong();
        int julianDay = buffer.getInt();
        return LocalDate.ofEpochDay(julianDay - 2_440_588L).atTime(LocalTime.ofNanoOfDay(nanosOfDay));
    }

    private static String base64(Binary value) {
        return Base64.getEncoder().encodeToString(value.getBytes());
    }

    private static final class GroupReaderBuilder extends ParquetReader.Builder<Group> {

        private GroupReaderBuilder(InputFile inputFile) {
            super(inputFile);
        }

        @Override
        protected ReadSupport<Group> getReadSupport() {
            return new GroupReadSupport();
        }
    }
}
