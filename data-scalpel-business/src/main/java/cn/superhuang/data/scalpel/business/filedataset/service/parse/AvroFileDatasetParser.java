package cn.superhuang.data.scalpel.business.filedataset.service.parse;

import cn.superhuang.data.scalpel.business.filedataset.domain.FileDatasetFormat;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import org.apache.avro.Conversions;
import org.apache.avro.Schema;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses a bounded sample from an Avro Object Container File. */
@Component
public class AvroFileDatasetParser implements FileDatasetParser {

    private final ObjectMapper objectMapper;
    private final Conversions.DecimalConversion decimalConversion = new Conversions.DecimalConversion();

    public AvroFileDatasetParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(FileDatasetFormat format) {
        return format == FileDatasetFormat.AVRO;
    }

    @Override
    public ParseResult parse(FileDatasetParseSource source, FileDatasetParsingConfiguration configuration, int recordLimit)
            throws IOException {
        return read(source, configuration, recordLimit, false);
    }

    @Override
    public ParseResult validate(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int previewLimit
    ) throws IOException {
        return read(source, configuration, previewLimit, true);
    }

    private ParseResult read(
            FileDatasetParseSource source,
            FileDatasetParsingConfiguration configuration,
            int recordLimit,
            boolean validateAll
    ) throws IOException {
        if (!(configuration instanceof FileDatasetParsingConfiguration.Avro)) {
            throw new FileDatasetParsingException("Avro 解析参数无效");
        }
        if (recordLimit < 1) {
            throw new IllegalArgumentException("抽样记录数必须大于零");
        }
        InputStream inputStream = FileDatasetParseSource.requireStream(source);
        try (DataFileStream<GenericRecord> reader = new DataFileStream<>(inputStream, new GenericDatumReader<>())) {
            Schema schema = reader.getSchema();
            if (schema.getType() != Schema.Type.RECORD) {
                throw new FileDatasetParsingException("Avro 顶层 Schema 必须是 record");
            }
            if (containsRecursiveSchema(schema, Collections.newSetFromMap(new IdentityHashMap<Schema, Boolean>()),
                    Collections.newSetFromMap(new IdentityHashMap<Schema, Boolean>()))) {
                throw new FileDatasetParsingException("暂不支持递归 Avro Schema 预览");
            }
            List<Field> fields = fields(schema);
            List<Map<String, Object>> rows = new ArrayList<>();
            long rowCount = 0;
            while (reader.hasNext()) {
                GenericRecord record = reader.next();
                rowCount++;
                if (rows.size() < recordLimit) {
                    rows.add(row(schema, record));
                } else if (!validateAll) {
                    return new ParseResult(fields, rows, true, true, Map.of(), rowCount);
                }
            }
            return new ParseResult(
                    fields, rows, rowCount > rows.size(), true,
                    Map.of("avroFullName", schema.getFullName()), rowCount
            );
        } catch (FileDatasetParsingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("Avro 文件内容无效：" + safeMessage(exception), exception);
        }
    }

    private List<Field> fields(Schema schema) {
        List<Field> fields = new ArrayList<>(schema.getFields().size());
        for (int index = 0; index < schema.getFields().size(); index++) {
            Schema.Field field = schema.getFields().get(index);
            SchemaType schemaType = schemaType(field.schema());
            fields.add(new Field(field.name(), index, schemaType.type(), schemaType.nullable()));
        }
        return fields;
    }

    private Map<String, Object> row(Schema schema, GenericRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Schema.Field field : schema.getFields()) {
            row.put(field.name(), previewValue(field.schema(), record.get(field.pos())));
        }
        return row;
    }

    private SchemaType schemaType(Schema schema) {
        if (schema.getType() != Schema.Type.UNION) {
            return new SchemaType(typeDefinition(schema), false);
        }
        List<Schema> nonNullBranches = schema.getTypes().stream()
                .filter(branch -> branch.getType() != Schema.Type.NULL)
                .toList();
        boolean nullable = nonNullBranches.size() != schema.getTypes().size();
        if (nonNullBranches.size() == 1) {
            return new SchemaType(typeDefinition(nonNullBranches.getFirst()), nullable);
        }
        if (!nonNullBranches.isEmpty() && nonNullBranches.stream().allMatch(this::isNumeric)) {
            boolean decimal = nonNullBranches.stream().anyMatch(this::isDecimalNumber);
            return new SchemaType(
                    decimal
                            ? FileDatasetTypeDefinitions.decimal(38, 18)
                            : PlatformTypeDefinition.of(PlatformDataType.LONG),
                    nullable
            );
        }
        return new SchemaType(PlatformTypeDefinition.string(null), nullable);
    }

    private PlatformTypeDefinition typeDefinition(Schema schema) {
        String logicalTypeName = schema.getLogicalType() == null ? null : schema.getLogicalType().getName();
        if ("decimal".equals(logicalTypeName)) {
            org.apache.avro.LogicalTypes.Decimal decimal =
                    (org.apache.avro.LogicalTypes.Decimal) schema.getLogicalType();
            return FileDatasetTypeDefinitions.decimal(decimal.getPrecision(), decimal.getScale());
        }
        if ("date".equals(logicalTypeName)) {
            return PlatformTypeDefinition.of(PlatformDataType.DATE);
        }
        if ("time-millis".equals(logicalTypeName) || "time-micros".equals(logicalTypeName)) {
            return PlatformTypeDefinition.string(null);
        }
        if ("timestamp-millis".equals(logicalTypeName) || "timestamp-micros".equals(logicalTypeName)) {
            return PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP);
        }
        if ("local-timestamp-millis".equals(logicalTypeName) || "local-timestamp-micros".equals(logicalTypeName)) {
            return PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP_NTZ);
        }
        if ("uuid".equals(logicalTypeName)) {
            return PlatformTypeDefinition.string(null);
        }
        return switch (schema.getType()) {
            case BOOLEAN -> PlatformTypeDefinition.of(PlatformDataType.BOOLEAN);
            case INT, LONG -> PlatformTypeDefinition.of(PlatformDataType.LONG);
            case FLOAT -> PlatformTypeDefinition.of(PlatformDataType.FLOAT);
            case DOUBLE -> PlatformTypeDefinition.of(PlatformDataType.DOUBLE);
            case STRING, ENUM, ARRAY, MAP, RECORD, UNION, NULL -> PlatformTypeDefinition.string(null);
            case BYTES, FIXED -> PlatformTypeDefinition.of(PlatformDataType.BINARY);
        };
    }

    private LogicalType logicalType(Schema schema) {
        String logicalTypeName = schema.getLogicalType() == null ? null : schema.getLogicalType().getName();
        if ("decimal".equals(logicalTypeName)) {
            return LogicalType.DECIMAL;
        }
        if ("date".equals(logicalTypeName)) {
            return LogicalType.DATE;
        }
        if ("time-millis".equals(logicalTypeName) || "time-micros".equals(logicalTypeName)) {
            return LogicalType.TIME;
        }
        if ("timestamp-millis".equals(logicalTypeName) || "timestamp-micros".equals(logicalTypeName)
                || "local-timestamp-millis".equals(logicalTypeName) || "local-timestamp-micros".equals(logicalTypeName)) {
            return LogicalType.DATETIME;
        }
        if ("uuid".equals(logicalTypeName)) {
            return LogicalType.STRING;
        }
        return switch (schema.getType()) {
            case BOOLEAN -> LogicalType.BOOLEAN;
            case INT, LONG -> LogicalType.INTEGER;
            case FLOAT, DOUBLE -> LogicalType.DECIMAL;
            case STRING, ENUM -> LogicalType.STRING;
            case BYTES, FIXED -> LogicalType.BINARY;
            case ARRAY -> LogicalType.ARRAY;
            case MAP, RECORD, UNION -> LogicalType.JSON;
            case NULL -> LogicalType.STRING;
        };
    }

    private Object previewValue(Schema sourceSchema, Object value) {
        if (value == null) {
            return null;
        }
        Schema schema = resolveUnion(sourceSchema, value);
        if (sourceSchema.getType() == Schema.Type.UNION
                && sourceSchema.getTypes().stream().filter(branch -> branch.getType() != Schema.Type.NULL).count() > 1) {
            return compactJson(jsonValue(schema, value));
        }
        String logicalTypeName = schema.getLogicalType() == null ? null : schema.getLogicalType().getName();
        if ("decimal".equals(logicalTypeName)) {
            return decimalValue(schema, value);
        }
        if ("date".equals(logicalTypeName)) {
            return value instanceof LocalDate localDate ? localDate : LocalDate.ofEpochDay(number(value).longValue());
        }
        if ("time-millis".equals(logicalTypeName)) {
            return value instanceof LocalTime localTime ? localTime : LocalTime.ofNanoOfDay(number(value).longValue() * 1_000_000L);
        }
        if ("time-micros".equals(logicalTypeName)) {
            return value instanceof LocalTime localTime ? localTime : LocalTime.ofNanoOfDay(number(value).longValue() * 1_000L);
        }
        if ("timestamp-millis".equals(logicalTypeName)) {
            return value instanceof Instant instant ? instant : Instant.ofEpochMilli(number(value).longValue());
        }
        if ("timestamp-micros".equals(logicalTypeName)) {
            return value instanceof Instant instant ? instant : Instant.ofEpochSecond(
                    Math.floorDiv(number(value).longValue(), 1_000_000L),
                    Math.floorMod(number(value).longValue(), 1_000_000L) * 1_000L
            );
        }
        if ("local-timestamp-millis".equals(logicalTypeName)) {
            return value instanceof LocalDateTime localDateTime ? localDateTime
                    : LocalDateTime.ofInstant(Instant.ofEpochMilli(number(value).longValue()), ZoneOffset.UTC);
        }
        if ("local-timestamp-micros".equals(logicalTypeName)) {
            if (value instanceof LocalDateTime localDateTime) {
                return localDateTime;
            }
            long micros = number(value).longValue();
            return LocalDateTime.ofEpochSecond(Math.floorDiv(micros, 1_000_000L),
                    Math.toIntExact(Math.floorMod(micros, 1_000_000L) * 1_000L), ZoneOffset.UTC);
        }
        return switch (schema.getType()) {
            case BOOLEAN -> value;
            case INT, LONG -> number(value).longValue();
            case FLOAT, DOUBLE -> BigDecimal.valueOf(number(value).doubleValue());
            case STRING, ENUM -> String.valueOf(value);
            case BYTES, FIXED -> base64(value);
            case ARRAY -> compactJson(jsonValue(schema, value));
            case MAP, RECORD, UNION -> compactJson(jsonValue(schema, value));
            case NULL -> null;
        };
    }

    private Object jsonValue(Schema sourceSchema, Object value) {
        if (value == null) {
            return null;
        }
        Schema schema = resolveUnion(sourceSchema, value);
        return switch (schema.getType()) {
            case RECORD -> {
                GenericRecord record = (GenericRecord) value;
                Map<String, Object> map = new LinkedHashMap<>();
                for (Schema.Field field : schema.getFields()) {
                    map.put(field.name(), jsonValue(field.schema(), record.get(field.pos())));
                }
                yield map;
            }
            case ARRAY -> {
                List<Object> values = new ArrayList<>();
                for (Object item : (Iterable<?>) value) {
                    values.add(jsonValue(schema.getElementType(), item));
                }
                yield values;
            }
            case MAP -> {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    map.put(String.valueOf(entry.getKey()), jsonValue(schema.getValueType(), entry.getValue()));
                }
                yield map;
            }
            case BYTES, FIXED -> base64(value);
            case STRING, ENUM -> String.valueOf(value);
            case INT, LONG, FLOAT, DOUBLE, BOOLEAN, NULL, UNION -> previewValue(schema, value);
        };
    }

    private Schema resolveUnion(Schema schema, Object value) {
        if (schema.getType() != Schema.Type.UNION) {
            return schema;
        }
        int branchIndex = GenericData.get().resolveUnion(schema, value);
        return schema.getTypes().get(branchIndex);
    }

    private BigDecimal decimalValue(Schema schema, Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof ByteBuffer buffer) {
            return decimalConversion.fromBytes(buffer.duplicate(), schema, schema.getLogicalType());
        }
        if (value instanceof GenericFixed fixed) {
            return decimalConversion.fromFixed(fixed, schema, schema.getLogicalType());
        }
        throw new FileDatasetParsingException("Avro decimal 字段值无效");
    }

    private static String base64(Object value) {
        byte[] bytes;
        if (value instanceof ByteBuffer buffer) {
            ByteBuffer copy = buffer.duplicate();
            bytes = new byte[copy.remaining()];
            copy.get(bytes);
        } else if (value instanceof GenericFixed fixed) {
            bytes = fixed.bytes();
        } else if (value instanceof byte[] array) {
            bytes = array;
        } else {
            throw new FileDatasetParsingException("Avro 二进制字段值无效");
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        throw new FileDatasetParsingException("Avro 数值字段值无效");
    }

    private String compactJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new FileDatasetParsingException("无法序列化 Avro 嵌套字段", exception);
        }
    }

    private boolean containsRecursiveSchema(Schema schema, Set<Schema> visiting, Set<Schema> visited) {
        if (visited.contains(schema)) {
            return false;
        }
        if (!visiting.add(schema)) {
            return true;
        }
        boolean recursive = switch (schema.getType()) {
            case RECORD -> schema.getFields().stream().anyMatch(field -> containsRecursiveSchema(field.schema(), visiting, visited));
            case ARRAY -> containsRecursiveSchema(schema.getElementType(), visiting, visited);
            case MAP -> containsRecursiveSchema(schema.getValueType(), visiting, visited);
            case UNION -> schema.getTypes().stream().anyMatch(branch -> containsRecursiveSchema(branch, visiting, visited));
            case BOOLEAN, INT, LONG, FLOAT, DOUBLE, STRING, BYTES, FIXED, ENUM, NULL -> false;
        };
        visiting.remove(schema);
        visited.add(schema);
        return recursive;
    }

    private boolean isNumeric(Schema schema) {
        return switch (schema.getType()) {
            case INT, LONG, FLOAT, DOUBLE -> true;
            case BYTES, FIXED -> "decimal".equals(schema.getLogicalType() == null ? null : schema.getLogicalType().getName());
            case BOOLEAN, STRING, ARRAY, MAP, RECORD, ENUM, NULL, UNION -> false;
        };
    }

    private boolean isDecimalNumber(Schema schema) {
        return switch (schema.getType()) {
            case FLOAT, DOUBLE -> true;
            case BYTES, FIXED -> "decimal".equals(schema.getLogicalType() == null ? null : schema.getLogicalType().getName());
            case INT, LONG, BOOLEAN, STRING, ARRAY, MAP, RECORD, ENUM, NULL, UNION -> false;
        };
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "格式错误" : message;
    }

    private record SchemaType(PlatformTypeDefinition type, boolean nullable) {
    }
}
