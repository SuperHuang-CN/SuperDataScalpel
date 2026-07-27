package cn.superhuang.datascalpel.taskengine.spark;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkTypeMapperTest {
    @Test
    void roundTripsEverySupportedPlatformTypeAndStableMetadata() {
        List<CanvasColumnSchema> columns = new ArrayList<>();
        for (PlatformDataType type : PlatformDataType.values()) {
            if (type != PlatformDataType.GEOMETRY) {
                columns.add(column(type));
            }
        }

        StructType structType = SparkTypeMapper.toStructType(columns);
        List<CanvasColumnSchema> result = SparkTypeMapper.fromStructType(structType, List.of());

        assertEquals(columns, result);
    }

    @Test
    void doesNotMapGeometryToAStringOrBinarySparkType() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SparkTypeMapper.toStructType(List.of(column(PlatformDataType.GEOMETRY)))
        );

        assertTrue(exception.getMessage().contains("SPATIAL_FIELD_UNSUPPORTED"));
    }

    private static CanvasColumnSchema column(PlatformDataType type) {
        return new CanvasColumnSchema(
                type.name().toLowerCase(),
                type,
                type == PlatformDataType.STRING ? 128 : null,
                type == PlatformDataType.DECIMAL ? 20 : null,
                type == PlatformDataType.DECIMAL ? 4 : null,
                type != PlatformDataType.LONG,
                "default-value",
                type == PlatformDataType.LONG,
                type == PlatformDataType.TIMESTAMP,
                "column-" + type.name()
        );
    }
}
