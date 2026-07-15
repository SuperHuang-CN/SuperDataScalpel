package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingQuality;
import org.junit.jupiter.api.Test;

import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformTypeMappingTest {

    @Test
    void mapsPostgreSqlTypesInBothDirectionsWithoutNameBasedEnumConversion() {
        DatabaseDialect dialect = BuiltInDialects.registry().require("POSTGRESQL");

        var text = dialect.mapToPlatformType(jdbc(Types.LONGVARCHAR, "text", null, null, null));
        assertEquals(TypeMappingQuality.NORMALIZED, text.quality());
        assertEquals(PlatformTypeDefinition.string(null), text.definition());

        var smallint = dialect.mapToPlatformType(jdbc(Types.SMALLINT, "int2", null, null, null));
        assertEquals(PlatformDataType.SHORT, smallint.definition().type());

        var timestamptz = dialect.mapToPlatformType(jdbc(
                Types.TIMESTAMP_WITH_TIMEZONE, "timestamptz", null, null, null
        ));
        assertEquals(PlatformDataType.TIMESTAMP, timestamptz.definition().type());

        var byteType = dialect.mapToPhysicalType(PlatformTypeDefinition.of(PlatformDataType.BYTE));
        assertEquals(TypeMappingQuality.NORMALIZED, byteType.quality());
        assertEquals(TableColumnType.SHORT, byteType.definition().type());

        var timestampNtz = dialect.mapToPhysicalType(PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP_NTZ));
        assertEquals(TableColumnType.TIMESTAMP_NTZ, timestampNtz.definition().type());
    }

    @Test
    void rejectsDecimalMetadataThatWouldExceedSparkPrecision() {
        DatabaseDialect dialect = BuiltInDialects.registry().require("POSTGRESQL");

        var mapping = dialect.mapToPlatformType(jdbc(Types.NUMERIC, "numeric", null, 39, 2));

        assertEquals(TypeMappingQuality.UNSUPPORTED, mapping.quality());
        assertFalse(mapping.acceptable());
        assertNull(mapping.definition());
    }

    @Test
    void mapsDamengByteAndTimestampUsingControlledPhysicalFamilies() {
        DatabaseDialect dialect = BuiltInDialects.registry().require("DAMENG");

        var byteType = dialect.mapToPhysicalType(PlatformTypeDefinition.of(PlatformDataType.BYTE));
        var timelineTimestamp = dialect.mapToPhysicalType(PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP));

        assertEquals(TypeMappingQuality.NORMALIZED, byteType.quality());
        assertEquals(TableColumnType.SHORT, byteType.definition().type());
        assertEquals(TableColumnType.TIMESTAMP, timelineTimestamp.definition().type());
    }

    @Test
    void mapsClickHouseUnsignedTypesAndBlocksSemanticLossOnWrites() {
        DatabaseDialect dialect = BuiltInDialects.registry().require("CLICKHOUSE");

        var uint64 = dialect.mapToPlatformType(jdbc(Types.BIGINT, "UInt64", null, null, null));
        assertEquals(TypeMappingQuality.NORMALIZED, uint64.quality());
        assertEquals(PlatformTypeDefinition.decimal(20, 0), uint64.definition());

        var datetime = dialect.mapToPlatformType(jdbc(Types.TIMESTAMP, "DateTime64(6, 'UTC')", null, null, null));
        assertEquals(PlatformDataType.TIMESTAMP, datetime.definition().type());

        var boundedString = dialect.mapToPhysicalType(PlatformTypeDefinition.string(64));
        assertEquals(TypeMappingQuality.LOSSY, boundedString.quality());
        assertFalse(boundedString.acceptable());

        var unboundedString = dialect.mapToPhysicalType(PlatformTypeDefinition.string(null));
        assertTrue(unboundedString.acceptable());
        assertEquals(TableColumnType.TEXT, unboundedString.definition().type());

        var localTimestamp = dialect.mapToPhysicalType(PlatformTypeDefinition.of(PlatformDataType.TIMESTAMP_NTZ));
        assertEquals(TypeMappingQuality.UNSUPPORTED, localTimestamp.quality());
    }

    private static JdbcTypeDescriptor jdbc(
            int jdbcType,
            String nativeType,
            Integer length,
            Integer precision,
            Integer scale
    ) {
        return new JdbcTypeDescriptor(jdbcType, nativeType, length, precision, scale, null);
    }
}
