package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableStructureDifferenceType;
import cn.superhuang.data.scalpel.dialect.model.TableSummary;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgreSqlTimestampComparisonTest {

    @ParameterizedTest
    @CsvSource({
            "TIMESTAMP, 93, timestamptz, true",
            "TIMESTAMP, 93, timestamp with time zone, true",
            "TIMESTAMP, 2014, timestamptz, true",
            "TIMESTAMP, 93, TIMESTAMPTZ, true",
            "TIMESTAMP_NTZ, 93, timestamp, true",
            "TIMESTAMP_NTZ, 93, timestamp without time zone, true",
            "DATETIME, 93, timestamp, true",
            "DATETIME, 93, timestamp without time zone, true",
            "TIMESTAMP, 93, timestamp, false",
            "TIMESTAMP, 2014, timestamp without time zone, false",
            "TIMESTAMP_NTZ, 93, timestamptz, false",
            "TIMESTAMP_NTZ, 2014, timestamp with time zone, false",
            "DATETIME, 93, timestamptz, false",
            "DATETIME, 2014, timestamp with time zone, false",
            "TIMESTAMP, 12, varchar, false",
            "TIMESTAMP_NTZ, 91, date, false",
            "LONG, -5, int8, true",
            "LONG, 4, int4, false"
    })
    void comparesTimestampSemanticsBeforeJdbcTypeCode(
            TableColumnType expectedType, int jdbcType, String nativeType, boolean compatible
    ) {
        var table = new TableIdentifier(null, "public", "timestamp_comparison");
        var expected = new TableDefinition(table,
                List.of(new TableColumnDefinition("updated_at", expectedType, null, null, null, false)),
                List.of());
        var actual = new TableMetadata(new TableSummary(table, "TABLE", null),
                List.of(new ColumnMetadata("updated_at", 1, jdbcType, nativeType, LogicalType.OTHER,
                        null, null, null, false, null, false, false, null)),
                null, List.of());

        var comparison = BuiltInDialects.registry().require("POSTGRESQL").compareTable(expected, actual);

        assertEquals(compatible, comparison.compatible());
        if (!compatible) {
            assertEquals(1, comparison.differences().size());
            assertEquals(TableStructureDifferenceType.TYPE_MISMATCH, comparison.differences().getFirst().type());
        }
    }
}
