package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
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

class ControlledRelationalTableChangePlanTest {

    @Test
    void mysqlCombinesTheApprovedChangesIntoOneAtomicAlterStatement() {
        DatabaseDialect dialect = new MySqlDialect();
        UUID id = UUID.randomUUID();
        TableIdentifier table = new TableIdentifier("warehouse", null, "orders");
        TableDefinition before = definition(
                table,
                List.of(column("title", TableColumnType.STRING, 32, true, id)),
                List.of()
        );
        TableDefinition target = definition(
                table,
                List.of(column("name", TableColumnType.STRING, 128, false, id)),
                List.of()
        );

        var plan = dialect.planTableChange(before, target, metadata(
                table, actualColumn("title", Types.VARCHAR, "VARCHAR", 32, true)
        ));

        assertEquals(TableChangeStrategy.IN_PLACE, plan.strategy());
        assertTrue(plan.allowsInPlaceExecution());
        assertTrue(plan.checks().stream().anyMatch(check ->
                check.type() == TableChangeCheckType.COLUMNS_HAVE_NO_NULLS));
        assertEquals(List.of(
                "ALTER TABLE `warehouse`.`orders` CHANGE COLUMN `title` `name` varchar(128) NOT NULL"
        ), plan.requireExecutionOption(TableChangeExecutionMode.IN_PLACE).statements());
    }

    @Test
    void postgresqlFamilyDialectsUseTheSharedTransactionalInPlacePlanner() {
        for (DatabaseDialect dialect : List.of(
                new PostgreSqlDialect(), new HighGoDialect(), new OpenGaussDialect(), new KingbaseDialect())) {
            UUID countId = UUID.randomUUID();
            UUID titleId = UUID.randomUUID();
            TableIdentifier table = new TableIdentifier("warehouse", "public", "orders");
            TableDefinition before = definition(
                    table,
                    List.of(
                            column("count", TableColumnType.INTEGER, null, true, countId),
                            column("title", TableColumnType.STRING, 32, true, titleId)
                    ),
                    List.of()
            );
            TableDefinition target = definition(
                    table,
                    List.of(
                            column("total", TableColumnType.LONG, null, false, countId),
                            column("title", TableColumnType.STRING, 32, true, titleId),
                            column("remark", TableColumnType.STRING, 100, true, UUID.randomUUID())
                    ),
                    List.of()
            );

            var plan = dialect.planTableChange(before, target, metadata(
                    table,
                    actualColumn("count", Types.INTEGER, "integer", null, true),
                    actualColumn("title", Types.VARCHAR, "varchar", 32, true)
            ));

            assertEquals(TableChangeStrategy.IN_PLACE, plan.strategy());
            assertTrue(plan.allowsInPlaceExecution());
            assertEquals(List.of(
                    "ALTER TABLE \"public\".\"orders\" RENAME COLUMN \"count\" TO \"total\"",
                    "ALTER TABLE \"public\".\"orders\" ALTER COLUMN \"total\" TYPE bigint",
                    "ALTER TABLE \"public\".\"orders\" ALTER COLUMN \"total\" SET NOT NULL",
                    "ALTER TABLE \"public\".\"orders\" ADD COLUMN \"remark\" varchar(100)"
            ), plan.requireExecutionOption(TableChangeExecutionMode.IN_PLACE).statements());
        }
    }

    @Test
    void postgresqlFamilyDialectsShareControlledRebuildPlanning() {
        for (DatabaseDialect dialect : List.of(
                new PostgreSqlDialect(), new HighGoDialect(), new OpenGaussDialect(), new KingbaseDialect())) {
            UUID id = UUID.randomUUID();
            TableIdentifier table = new TableIdentifier("warehouse", "public", "orders");
            TableDefinition before = definition(
                    table,
                    List.of(column("event_text", TableColumnType.STRING, 64, true, id)),
                    List.of()
            );
            TableDefinition target = definition(
                    table,
                    List.of(column("event_date", TableColumnType.DATE, null, true, id)),
                    List.of()
            );

            var plan = dialect.planTableChange(before, target, metadata(
                    table, actualColumn("event_text", Types.VARCHAR, "varchar", 64, true)
            ));

            assertEquals(TableChangeStrategy.REBUILD_REQUIRED, plan.strategy(), dialect.definition().id());
            assertTrue(plan.allowsRebuildExecution(), dialect.definition().id());
            assertTrue(plan.checks().stream().anyMatch(check ->
                    check.type() == TableChangeCheckType.NO_REBUILD_DEPENDENCIES), dialect.definition().id());
            assertEquals(6,
                    plan.requireExecutionOption(TableChangeExecutionMode.REBUILD).statements().size(),
                    dialect.definition().id());
        }
    }

    @Test
    void refusesShrinkingColumnsAndChangingPrimaryKeys() {
        DatabaseDialect dialect = new MySqlDialect();
        UUID id = UUID.randomUUID();
        TableIdentifier table = new TableIdentifier("warehouse", null, "orders");
        TableDefinition before = definition(
                table,
                List.of(column("title", TableColumnType.STRING, 100, false, id)),
                List.of()
        );
        TableDefinition target = definition(
                table,
                List.of(column("title", TableColumnType.STRING, 32, false, id)),
                List.of("title")
        );

        var plan = dialect.planTableChange(before, target, metadata(
                table, actualColumn("title", Types.VARCHAR, "VARCHAR", 100, false)
        ));

        assertEquals(TableChangeStrategy.UNSUPPORTED, plan.strategy());
        assertFalse(plan.allowsInPlaceExecution());
        assertTrue(plan.executionOptions().isEmpty());
    }

    private static TableDefinition definition(
            TableIdentifier table,
            List<TableColumnDefinition> columns,
            List<String> primaryKey
    ) {
        return new TableDefinition(table, columns, primaryKey);
    }

    private static TableColumnDefinition column(
            String name,
            TableColumnType type,
            Integer length,
            boolean nullable,
            UUID id
    ) {
        return new TableColumnDefinition(name, type, length, null, null, nullable, id);
    }

    private static TableMetadata metadata(TableIdentifier table, ColumnMetadata... columns) {
        return new TableMetadata(
                new TableSummary(table, "TABLE", null),
                List.of(columns),
                new PrimaryKeyMetadata(null, List.of()),
                List.of()
        );
    }

    private static ColumnMetadata actualColumn(
            String name,
            int jdbcType,
            String nativeType,
            Integer length,
            boolean nullable
    ) {
        return new ColumnMetadata(
                name, 1, jdbcType, nativeType, LogicalType.OTHER, length,
                null, null, nullable, null, false, false, null
        );
    }
}
