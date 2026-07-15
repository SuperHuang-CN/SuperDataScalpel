package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.PrimaryKeyMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheckType;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableSummary;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgreSqlTableChangePlanTest {

    private final DatabaseDialect dialect = BuiltInDialects.registry().require("POSTGRESQL");

    @Test
    void rendersATransactionalInPlacePlanForRenameLengthReductionAndNotNull() {
        UUID id = UUID.randomUUID();
        TableIdentifier table = new TableIdentifier("warehouse", "public", "orders");
        TableDefinition before = new TableDefinition(
                table,
                List.of(new TableColumnDefinition("remark", TableColumnType.STRING, 200, null, null, true, id)),
                List.of()
        );
        TableDefinition target = new TableDefinition(
                table,
                List.of(new TableColumnDefinition("note", TableColumnType.STRING, 120, null, null, false, id)),
                List.of()
        );

        var plan = dialect.planTableChange(before, target, metadata(table, column("remark", Types.VARCHAR, "varchar", 200, null, null, true)));

        assertEquals(TableChangeStrategy.IN_PLACE, plan.strategy());
        assertTrue(plan.allowsInPlaceExecution());
        assertFalse(plan.allowsRebuildExecution());
        assertTrue(plan.checks().stream().anyMatch(check -> check.type() == TableChangeCheckType.MAX_STRING_LENGTH));
        assertTrue(plan.checks().stream().anyMatch(check -> check.type() == TableChangeCheckType.COLUMNS_HAVE_NO_NULLS));
        assertEquals(List.of(
                "ALTER TABLE \"public\".\"orders\" RENAME COLUMN \"remark\" TO \"note\"",
                "ALTER TABLE \"public\".\"orders\" ALTER COLUMN \"note\" TYPE varchar(120)",
                "ALTER TABLE \"public\".\"orders\" ALTER COLUMN \"note\" SET NOT NULL"
        ), plan.requireExecutionOption(TableChangeExecutionMode.IN_PLACE).statements());
    }

    @Test
    void reportsUnsupportedConversionsWithoutProvidingAnExecutionOption() {
        UUID id = UUID.randomUUID();
        TableIdentifier table = new TableIdentifier("warehouse", "public", "orders");
        TableDefinition before = new TableDefinition(
                table,
                List.of(new TableColumnDefinition("active", TableColumnType.BOOLEAN, null, null, null, false, id)),
                List.of()
        );
        TableDefinition target = new TableDefinition(
                table,
                List.of(new TableColumnDefinition("active", TableColumnType.DATE, null, null, null, false, id)),
                List.of()
        );

        var plan = dialect.planTableChange(before, target, metadata(table, column("active", Types.BOOLEAN, "bool", null, null, null, false)));

        assertEquals(TableChangeStrategy.UNSUPPORTED, plan.strategy());
        assertFalse(plan.allowsInPlaceExecution());
        assertFalse(plan.allowsRebuildExecution());
    }

    @Test
    void rendersAControlledRebuildPlanForStringToDateConversion() {
        UUID id = UUID.randomUUID();
        TableIdentifier table = new TableIdentifier("warehouse", "public", "orders");
        TableDefinition before = new TableDefinition(
                table,
                List.of(new TableColumnDefinition("event_text", TableColumnType.STRING, 64, null, null, true, id)),
                List.of()
        );
        TableDefinition target = new TableDefinition(
                table,
                List.of(new TableColumnDefinition("event_date", TableColumnType.DATE, null, null, null, true, id)),
                List.of()
        );

        var plan = dialect.planTableChange(before, target, metadata(table, column("event_text", Types.VARCHAR, "varchar", 64, null, null, true)));

        assertEquals(TableChangeStrategy.REBUILD_REQUIRED, plan.strategy());
        assertFalse(plan.allowsInPlaceExecution());
        assertTrue(plan.allowsRebuildExecution());
        assertTrue(plan.checks().stream().anyMatch(check -> check.type() == TableChangeCheckType.NO_REBUILD_DEPENDENCIES));
        List<String> statements = plan.requireExecutionOption(TableChangeExecutionMode.REBUILD).statements();
        assertEquals(6, statements.size());
        assertEquals("LOCK TABLE \"public\".\"orders\" IN ACCESS EXCLUSIVE MODE", statements.getFirst());
        assertTrue(statements.get(1).startsWith("CREATE TABLE \"public\".\"__dsc_new_"));
        assertTrue(statements.get(2).contains("SELECT CAST(\"event_text\" AS date) FROM \"public\".\"orders\""));
        assertTrue(statements.get(3).startsWith("ALTER TABLE \"public\".\"orders\" RENAME TO \"__dsc_old_"));
        assertTrue(statements.get(4).startsWith("ALTER TABLE \"public\".\"__dsc_new_"));
        assertTrue(statements.get(4).endsWith(" RENAME TO \"orders\""));
    }

    @Test
    void leavesBinaryConversionsUnsupportedEvenForRebuild() {
        UUID id = UUID.randomUUID();
        TableIdentifier table = new TableIdentifier("warehouse", "public", "orders");
        TableDefinition before = new TableDefinition(
                table,
                List.of(new TableColumnDefinition("payload", TableColumnType.BINARY, null, null, null, true, id)),
                List.of()
        );
        TableDefinition target = new TableDefinition(
                table,
                List.of(new TableColumnDefinition("payload", TableColumnType.STRING, 64, null, null, true, id)),
                List.of()
        );

        var plan = dialect.planTableChange(before, target, metadata(table, column("payload", Types.BINARY, "bytea", null, null, null, true)));

        assertEquals(TableChangeStrategy.UNSUPPORTED, plan.strategy());
        assertFalse(plan.allowsRebuildExecution());
    }

    private static TableMetadata metadata(TableIdentifier table, ColumnMetadata column) {
        return new TableMetadata(
                new TableSummary(table, "TABLE", null), List.of(column), new PrimaryKeyMetadata(null, List.of()), List.of()
        );
    }

    private static ColumnMetadata column(
            String name,
            int jdbcType,
            String nativeType,
            Integer length,
            Integer precision,
            Integer scale,
            boolean nullable
    ) {
        return new ColumnMetadata(name, 1, jdbcType, nativeType, LogicalType.OTHER, length, precision, scale,
                nullable, null, false, false, null);
    }
}
