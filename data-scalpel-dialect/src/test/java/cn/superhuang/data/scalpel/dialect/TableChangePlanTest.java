package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.model.TableChangeCheck;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheckType;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionMode;
import cn.superhuang.data.scalpel.dialect.model.TableChangeExecutionOption;
import cn.superhuang.data.scalpel.dialect.model.TableChangeOperation;
import cn.superhuang.data.scalpel.dialect.model.TableChangeOperationType;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableChangeRisk;
import cn.superhuang.data.scalpel.dialect.model.TableChangeStrategy;
import cn.superhuang.data.scalpel.dialect.model.TableColumnDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableColumnType;
import cn.superhuang.data.scalpel.dialect.model.TableDdlAtomicity;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableChangePlanTest {

    @Test
    void exposesExecutableCapabilitiesWithoutLettingAPlanUnderstateAnOperation() {
        TableChangeOperation operation = new TableChangeOperation(
                TableChangeOperationType.DROP_COLUMN,
                column("legacy_note", true),
                null,
                List.of(),
                List.of(),
                TableChangeStrategy.REBUILD_RECOMMENDED,
                TableChangeRisk.DESTRUCTIVE,
                List.of(),
                List.of()
        );
        TableChangePlan plan = new TableChangePlan(
                definition(),
                targetDefinition(),
                TableChangeStrategy.REBUILD_RECOMMENDED,
                TableChangeRisk.DESTRUCTIVE,
                TableDdlAtomicity.TRANSACTIONAL_BATCH,
                List.of(operation),
                List.of(),
                List.of(),
                List.of(
                        new TableChangeExecutionOption(
                                TableChangeExecutionMode.IN_PLACE,
                                TableDdlAtomicity.TRANSACTIONAL_BATCH,
                                List.of("ALTER TABLE \"public\".\"orders\" DROP COLUMN \"legacy_note\"")
                        ),
                        new TableChangeExecutionOption(
                                TableChangeExecutionMode.REBUILD,
                                TableDdlAtomicity.TRANSACTIONAL_BATCH,
                                List.of("CREATE TABLE \"orders__rebuild\" AS SELECT * FROM \"public\".\"orders\"")
                        )
                )
        );

        assertTrue(plan.allowsInPlaceExecution());
        assertTrue(plan.allowsRebuildExecution());
        assertThrows(IllegalArgumentException.class, () -> new TableChangePlan(
                definition(), targetDefinition(), TableChangeStrategy.IN_PLACE, TableChangeRisk.CAUTION,
                TableDdlAtomicity.TRANSACTIONAL_BATCH, List.of(operation), List.of(), List.of()
        ));
    }

    @Test
    void validatesTheStructuredParametersOfPreconditions() {
        assertThrows(IllegalArgumentException.class, () -> new TableChangeCheck(
                TableChangeCheckType.MAX_STRING_LENGTH,
                List.of("title"),
                0,
                null,
                null,
                null,
                "检查长度"
        ));
        assertThrows(IllegalArgumentException.class, () -> new TableChangeCheck(
                TableChangeCheckType.COLUMNS_ARE_UNIQUE,
                List.of(),
                null,
                null,
                null,
                null,
                "检查唯一性"
        ));

        TableChangeCheck check = new TableChangeCheck(
                TableChangeCheckType.STRUCTURE_FINGERPRINT_MATCH,
                List.of(),
                null,
                null,
                null,
                definition().structureFingerprint(),
                "执行前必须确认原表结构未发生漂移"
        );
        assertFalse(check.expectedFingerprint().value().isBlank());
    }

    @Test
    void preventsMixingColumnAndPrimaryKeyOperationPayloads() {
        assertThrows(IllegalArgumentException.class, () -> new TableChangeOperation(
                TableChangeOperationType.ADD_PRIMARY_KEY,
                column("order_id", false),
                null,
                List.of(),
                List.of("order_id"),
                TableChangeStrategy.IN_PLACE,
                TableChangeRisk.CAUTION,
                List.of(),
                List.of()
        ));
    }

    private static TableDefinition definition() {
        return new TableDefinition(
                new TableIdentifier(null, "public", "orders"),
                List.of(
                        new TableColumnDefinition("order_id", TableColumnType.LONG, null, null, null, false),
                        column("legacy_note", true)
                ),
                List.of("order_id")
        );
    }

    private static TableDefinition targetDefinition() {
        return new TableDefinition(
                new TableIdentifier(null, "public", "orders"),
                List.of(new TableColumnDefinition("order_id", TableColumnType.LONG, null, null, null, false)),
                List.of("order_id")
        );
    }

    private static TableColumnDefinition column(String name, boolean nullable) {
        return new TableColumnDefinition(name, TableColumnType.STRING, 100, null, null, nullable);
    }
}
