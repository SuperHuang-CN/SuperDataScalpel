package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.model.*;
import org.junit.jupiter.api.Test;
import java.sql.Types;
import static org.junit.jupiter.api.Assertions.*;

class MySqlTimestampStructureTest {
    @Test void nativeTimestampAndDatetimeRemainDistinctDespiteSharedJdbcCode() {
        var dialect = new MySqlDialect();
        for (String nativeType : new String[]{"TIMESTAMP", "DATETIME"}) {
            var actual = new ColumnMetadata("event_time", 1, Types.TIMESTAMP, nativeType,
                    LogicalType.DATETIME, null, null, null, true, null, false, false, null);
            var expectedType = nativeType.equals("TIMESTAMP") ? TableColumnType.TIMESTAMP : TableColumnType.TIMESTAMP_NTZ;
            var oppositeType = nativeType.equals("TIMESTAMP") ? TableColumnType.TIMESTAMP_NTZ : TableColumnType.TIMESTAMP;
            assertTrue(dialect.matchesColumnType(new TableColumnDefinition("event_time", expectedType, null, null, null, true), actual));
            assertFalse(dialect.matchesColumnType(new TableColumnDefinition("event_time", oppositeType, null, null, null, true), actual));
            assertEquals(expectedType, dialect.tableColumnType(actual));
            assertEquals(nativeType.equals("TIMESTAMP") ? PlatformDataType.TIMESTAMP : PlatformDataType.TIMESTAMP_NTZ,
                    dialect.mapToPlatformType(JdbcTypeDescriptor.from(actual)).definition().type());
        }
    }
}
