package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsertSelectDialectTest {

    private final DialectRegistry registry = BuiltInDialects.registry();
    private final TableIdentifier target = new TableIdentifier("warehouse", "public", "order_summary");

    @Test
    void declaresInsertSelectAndQueryMetadataForRegularJdbcDialects() {
        assertTrue(registry.all().stream()
                .filter(dialect -> !dialect.definition().id().startsWith("TDENGINE_"))
                .allMatch(dialect ->
                dialect.definition().capabilities().contains(DatabaseCapability.INSERT_SELECT)
                        && dialect.definition().capabilities().contains(DatabaseCapability.QUERY_METADATA)));
        for (String id : List.of("POSTGRESQL", "HIGHGO", "OPENGAUSS", "KINGBASE")) {
            assertTrue(registry.require(id).definition().capabilities()
                    .contains(DatabaseCapability.OVERWRITE_INSERT_SELECT), id);
        }
        for (String id : List.of("POSTGRESQL", "HIGHGO", "MYSQL", "OPENGAUSS", "KINGBASE")) {
            assertTrue(registry.require(id).definition().capabilities()
                    .contains(DatabaseCapability.JDBC_QUERY_INPUT), id);
        }
        assertFalse(registry.require("CLICKHOUSE").definition().capabilities()
                .contains(DatabaseCapability.JDBC_QUERY_INPUT));
        assertFalse(registry.require("CLICKHOUSE").definition().capabilities().contains(DatabaseCapability.OVERWRITE_INSERT_SELECT));
        assertFalse(registry.require("TDENGINE_WEBSOCKET").definition().capabilities()
                .contains(DatabaseCapability.INSERT_SELECT));
        assertFalse(registry.require("TDENGINE_RESTFUL").definition().capabilities()
                .contains(DatabaseCapability.INSERT_SELECT));
    }

    @Test
    void rendersCteInThePositionRequiredByEachDialect() {
        InsertSelectQuery query = ReadOnlySelectQueryParser.parse(
                "WITH source AS (SELECT customer_id, amount FROM orders) SELECT customer_id, amount FROM source"
        );

        assertEquals(
                "INSERT INTO \"public\".\"order_summary\" (\"customer_id\", \"amount\") WITH source AS (SELECT customer_id, amount FROM orders) SELECT customer_id, amount FROM source",
                registry.require("POSTGRESQL").renderInsertSelect(target, List.of("customer_id", "amount"), query)
        );
        assertEquals(
                "INSERT INTO `warehouse`.`order_summary` (`customer_id`, `amount`) WITH source AS (SELECT customer_id, amount FROM orders) SELECT customer_id, amount FROM source",
                registry.require("CLICKHOUSE").renderInsertSelect(target, List.of("customer_id", "amount"), query)
        );
        assertEquals(
                "WITH source AS (SELECT customer_id, amount FROM orders) INSERT INTO [warehouse].[public].[order_summary] ([customer_id], [amount]) SELECT customer_id, amount FROM source",
                registry.require("SQL_SERVER").renderInsertSelect(target, List.of("customer_id", "amount"), query)
        );
    }

    @Test
    void rendersEveryBuiltInDialectForPlainAndCteQueries() {
        InsertSelectQuery plain = ReadOnlySelectQueryParser.parse("SELECT customer_id, amount FROM orders");
        InsertSelectQuery cte = ReadOnlySelectQueryParser.parse(
                "WITH source AS (SELECT customer_id, amount FROM orders) SELECT customer_id, amount FROM source"
        );

        registry.all().stream()
                .filter(dialect -> dialect.definition().capabilities().contains(DatabaseCapability.INSERT_SELECT))
                .forEach(dialect -> {
            assertTrue(dialect.renderInsertSelect(target, List.of("customer_id", "amount"), plain)
                    .startsWith("INSERT INTO "));
            String renderedCte = dialect.renderInsertSelect(target, List.of("customer_id", "amount"), cte);
            if ("SQL_SERVER".equals(dialect.definition().id())) {
                assertTrue(renderedCte.startsWith("WITH source AS"));
                assertTrue(renderedCte.contains(" INSERT INTO "));
            } else {
                assertTrue(renderedCte.startsWith("INSERT INTO "));
                assertTrue(renderedCte.contains(" WITH source AS"));
            }
                });
    }
}
