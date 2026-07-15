package cn.superhuang.data.scalpel.dialect.builtin;

import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
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

class DamengTableChangePlanTest {

    private final DamengDialect dialect = new DamengDialect();

    @Test
    void providesATransactionalInPlacePlanOnlyForTheApprovedRuntime() {
        UUID fieldId = UUID.randomUUID();
        TableIdentifier table = new TableIdentifier(null, "APP", "orders");
        TableDefinition before = definition(table, "title", TableColumnType.STRING, 32, true, fieldId);
        TableDefinition target = definition(table, "name", TableColumnType.STRING, 128, false, fieldId);

        var plan = dialect.planTableChange(
                before, target, metadata(table, "title", Types.VARCHAR, "VARCHAR", 32, true),
                new DamengChangeRuntime("0", "0", true)
        );

        assertEquals(TableChangeStrategy.IN_PLACE, plan.strategy());
        assertTrue(plan.allowsInPlaceExecution());
        assertTrue(plan.checks().stream().anyMatch(check -> check.type() == TableChangeCheckType.DATABASE_RUNTIME_SUPPORTED));
        assertTrue(plan.checks().stream().anyMatch(check -> check.type() == TableChangeCheckType.COLUMNS_HAVE_NO_NULLS));
        assertEquals(List.of(
                "ALTER TABLE \"APP\".\"orders\" RENAME COLUMN \"title\" TO \"name\"",
                "ALTER TABLE \"APP\".\"orders\" MODIFY \"name\" VARCHAR(128) NOT NULL"
        ), plan.requireExecutionOption(TableChangeExecutionMode.IN_PLACE).statements());
    }

    @Test
    void refusesToOfferExecutionWhenDamengWillAutoCommitDdl() {
        UUID fieldId = UUID.randomUUID();
        TableIdentifier table = new TableIdentifier(null, "APP", "orders");
        TableDefinition before = definition(table, "title", TableColumnType.STRING, 32, true, fieldId);
        TableDefinition target = definition(table, "title", TableColumnType.STRING, 64, true, fieldId);

        var plan = dialect.planTableChange(
                before, target, metadata(table, "title", Types.VARCHAR, "VARCHAR", 32, true),
                new DamengChangeRuntime("1", "0", true)
        );

        assertEquals(TableChangeStrategy.UNSUPPORTED, plan.strategy());
        assertFalse(plan.allowsInPlaceExecution());
        assertTrue(plan.reasons().stream().anyMatch(reason -> reason.message().contains("DDL_AUTO_COMMIT=1")));
    }

    @Test
    void refusesConversionsThatRequireDamengSpecificDataMigration() {
        UUID fieldId = UUID.randomUUID();
        TableIdentifier table = new TableIdentifier(null, "APP", "orders");
        TableDefinition before = definition(table, "title", TableColumnType.STRING, 32, true, fieldId);
        TableDefinition target = definition(table, "title", TableColumnType.TEXT, null, true, fieldId);

        var plan = dialect.planTableChange(
                before, target, metadata(table, "title", Types.VARCHAR, "VARCHAR", 32, true),
                new DamengChangeRuntime("0", "0", true)
        );

        assertEquals(TableChangeStrategy.UNSUPPORTED, plan.strategy());
        assertFalse(plan.allowsInPlaceExecution());
    }

    @Test
    void requiresAnEmptyTableForAddingANonNullColumn() {
        UUID firstId = UUID.randomUUID();
        TableIdentifier table = new TableIdentifier(null, "APP", "orders");
        TableDefinition before = definition(table, "title", TableColumnType.STRING, 32, true, firstId);
        TableDefinition target = new TableDefinition(
                table,
                List.of(
                        new TableColumnDefinition("title", TableColumnType.STRING, 32, null, null, true, firstId),
                        new TableColumnDefinition("sequence_no", TableColumnType.LONG, null, null, null, false, UUID.randomUUID())
                ),
                List.of()
        );

        var plan = dialect.planTableChange(
                before, target, metadata(table, "title", Types.VARCHAR, "VARCHAR", 32, true),
                new DamengChangeRuntime("0", "0", true)
        );

        assertEquals(TableChangeStrategy.IN_PLACE, plan.strategy());
        assertTrue(plan.checks().stream().anyMatch(check -> check.type() == TableChangeCheckType.TABLE_EMPTY));
        assertTrue(plan.requireExecutionOption(TableChangeExecutionMode.IN_PLACE).statements().getFirst()
                .endsWith("ADD COLUMN \"sequence_no\" BIGINT NOT NULL"));
    }

    private static TableDefinition definition(
            TableIdentifier table,
            String columnName,
            TableColumnType type,
            Integer length,
            boolean nullable,
            UUID id
    ) {
        return new TableDefinition(
                table,
                List.of(new TableColumnDefinition(columnName, type, length, null, null, nullable, id)),
                List.of()
        );
    }

    private static TableMetadata metadata(
            TableIdentifier table,
            String columnName,
            int jdbcType,
            String nativeType,
            Integer length,
            boolean nullable
    ) {
        return new TableMetadata(
                new TableSummary(table, "TABLE", null),
                List.of(new ColumnMetadata(
                        columnName, 1, jdbcType, nativeType, LogicalType.OTHER, length,
                        null, null, nullable, null, false, false, null
                )),
                null,
                List.of()
        );
    }
}
