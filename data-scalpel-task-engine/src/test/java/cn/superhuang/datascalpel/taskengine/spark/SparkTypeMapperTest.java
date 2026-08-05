package cn.superhuang.datascalpel.taskengine.spark;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class SparkTypeMapperTest {
    @Test
    void roundTripsEverySupportedPlatformTypeAndStableMetadata() {
        List<CanvasColumnSchema> columns = new ArrayList<>();
        for (PlatformDataType type : PlatformDataType.values()) {
            columns.add(column(type));
        }

        StructType structType = SparkTypeMapper.toStructType(columns);
        List<CanvasColumnSchema> result = SparkTypeMapper.fromStructType(structType, List.of());

        assertEquals(columns, result);
    }

    @Test
    void mapsGeometryToSedonaGeometryUdtAndRestoresItsStableDefinition() {
        CanvasColumnSchema geometry = column(PlatformDataType.GEOMETRY);
        StructType structType = SparkTypeMapper.toStructType(List.of(geometry));

        assertInstanceOf(GeometryUDT.class, structType.fields()[0].dataType());
        assertEquals(
                List.of(geometry),
                SparkTypeMapper.fromStructType(structType, List.of())
        );
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
                "column-" + type.name(),
                type == PlatformDataType.GEOMETRY
                        ? new GeometryTypeDefinition(
                                GeometryKind.POINT,
                                new CrsReference("EPSG", 4326),
                                CoordinateDimension.XY
                        )
                        : null
        );
    }
}
