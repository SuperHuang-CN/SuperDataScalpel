package cn.superhuang.datascalpel.taskengine.spark;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.spark.sql.types.DataType;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.DecimalType;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.MetadataBuilder;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SparkTypeMapper {
    private static final String TYPE = "datascalpel.type";
    private static final String LENGTH = "datascalpel.length";
    private static final String PRECISION = "datascalpel.precision";
    private static final String SCALE = "datascalpel.scale";
    private static final String DEFAULT_VALUE = "datascalpel.defaultValue";
    private static final String AUTO_INCREMENT = "datascalpel.autoIncrement";
    private static final String GENERATED = "datascalpel.generated";
    private static final String COMMENT = "datascalpel.comment";

    private SparkTypeMapper() {
    }

    public static StructType toStructType(List<CanvasColumnSchema> columns) {
        List<StructField> fields = columns.stream().map(SparkTypeMapper::toStructField).toList();
        return DataTypes.createStructType(fields);
    }

    public static DataType toDataType(CanvasColumnSchema column) {
        return sparkType(column);
    }

    public static List<CanvasColumnSchema> fromStructType(
            StructType structType,
            List<CanvasColumnSchema> fallbackColumns
    ) {
        Map<String, CanvasColumnSchema> fallback = new LinkedHashMap<>();
        fallbackColumns.forEach(column -> fallback.put(column.name(), column));
        List<CanvasColumnSchema> columns = new ArrayList<>();
        for (StructField field : structType.fields()) {
            CanvasColumnSchema prior = fallback.get(field.name());
            Metadata metadata = field.metadata();
            PlatformDataType type = platformType(field.dataType(), metadata);
            Integer length = optionalInteger(metadata, LENGTH, prior == null ? null : prior.length());
            Integer precision = type == PlatformDataType.DECIMAL
                    ? ((DecimalType) field.dataType()).precision()
                    : null;
            Integer scale = type == PlatformDataType.DECIMAL
                    ? ((DecimalType) field.dataType()).scale()
                    : null;
            columns.add(new CanvasColumnSchema(
                    field.name(),
                    type,
                    length,
                    precision,
                    scale,
                    field.nullable(),
                    optionalString(metadata, DEFAULT_VALUE, prior == null ? null : prior.defaultValue()),
                    optionalBoolean(metadata, AUTO_INCREMENT, prior != null && prior.autoIncrement()),
                    optionalBoolean(metadata, GENERATED, prior != null && prior.generated()),
                    optionalString(metadata, COMMENT, prior == null ? null : prior.comment())
            ));
        }
        return List.copyOf(columns);
    }

    private static StructField toStructField(CanvasColumnSchema column) {
        MetadataBuilder metadata = new MetadataBuilder()
                .putString(TYPE, column.fieldType().name())
                .putBoolean(AUTO_INCREMENT, column.autoIncrement())
                .putBoolean(GENERATED, column.generated());
        if (column.length() != null) {
            metadata.putLong(LENGTH, column.length());
        }
        if (column.precision() != null) {
            metadata.putLong(PRECISION, column.precision());
        }
        if (column.scale() != null) {
            metadata.putLong(SCALE, column.scale());
        }
        if (column.defaultValue() != null) {
            metadata.putString(DEFAULT_VALUE, column.defaultValue());
        }
        if (column.comment() != null) {
            metadata.putString(COMMENT, column.comment());
        }
        return DataTypes.createStructField(column.name(), sparkType(column), column.nullable(), metadata.build());
    }

    private static DataType sparkType(CanvasColumnSchema column) {
        return switch (column.fieldType()) {
            case BOOLEAN -> DataTypes.BooleanType;
            case BYTE -> DataTypes.ByteType;
            case SHORT -> DataTypes.ShortType;
            case INTEGER -> DataTypes.IntegerType;
            case LONG -> DataTypes.LongType;
            case FLOAT -> DataTypes.FloatType;
            case DOUBLE -> DataTypes.DoubleType;
            case DECIMAL -> DataTypes.createDecimalType(column.precision(), column.scale());
            case STRING -> DataTypes.StringType;
            case BINARY -> DataTypes.BinaryType;
            case DATE -> DataTypes.DateType;
            case TIMESTAMP -> DataTypes.TimestampType;
            case TIMESTAMP_NTZ -> DataTypes.TimestampNTZType;
            case GEOMETRY -> throw new IllegalArgumentException("SPATIAL_FIELD_UNSUPPORTED: Spark Canvas 不支持空间字段");
        };
    }

    private static PlatformDataType platformType(DataType dataType, Metadata metadata) {
        if (metadata.contains(TYPE)) {
            return PlatformDataType.valueOf(metadata.getString(TYPE));
        }
        if (dataType.equals(DataTypes.BooleanType)) return PlatformDataType.BOOLEAN;
        if (dataType.equals(DataTypes.ByteType)) return PlatformDataType.BYTE;
        if (dataType.equals(DataTypes.ShortType)) return PlatformDataType.SHORT;
        if (dataType.equals(DataTypes.IntegerType)) return PlatformDataType.INTEGER;
        if (dataType.equals(DataTypes.LongType)) return PlatformDataType.LONG;
        if (dataType.equals(DataTypes.FloatType)) return PlatformDataType.FLOAT;
        if (dataType.equals(DataTypes.DoubleType)) return PlatformDataType.DOUBLE;
        if (dataType instanceof DecimalType) return PlatformDataType.DECIMAL;
        if (dataType.equals(DataTypes.StringType)) return PlatformDataType.STRING;
        if (dataType.equals(DataTypes.BinaryType)) return PlatformDataType.BINARY;
        if (dataType.equals(DataTypes.DateType)) return PlatformDataType.DATE;
        if (dataType.equals(DataTypes.TimestampType)) return PlatformDataType.TIMESTAMP;
        if (dataType.equals(DataTypes.TimestampNTZType)) return PlatformDataType.TIMESTAMP_NTZ;
        throw new IllegalArgumentException("Unsupported Spark data type: " + dataType.typeName());
    }

    private static Integer optionalInteger(Metadata metadata, String key, Integer fallback) {
        if (metadata.contains(key)) {
            return Math.toIntExact(metadata.getLong(key));
        }
        return fallback;
    }

    private static String optionalString(Metadata metadata, String key, String fallback) {
        return metadata.contains(key) ? metadata.getString(key) : fallback;
    }

    private static boolean optionalBoolean(Metadata metadata, String key, boolean fallback) {
        return metadata.contains(key) ? metadata.getBoolean(key) : fallback;
    }
}
