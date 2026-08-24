package cn.superhuang.data.scalpel.dialect;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlServiceQueryDialectTest {

    @Test
    void postgresAndClickHouseAdvertiseSqlServiceQueries() {
        var registry = BuiltInDialects.registry();
        assertTrue(registry.require("POSTGRESQL").definition().capabilities()
                .contains(DatabaseCapability.SQL_SERVICE_QUERY));
        assertTrue(registry.require("CLICKHOUSE").definition().capabilities()
                .contains(DatabaseCapability.SQL_SERVICE_QUERY));
        registry.all().stream()
                .filter(dialect -> !Set.of("POSTGRESQL", "CLICKHOUSE").contains(dialect.definition().id()))
                .forEach(dialect -> assertFalse(
                        dialect.definition().capabilities().contains(DatabaseCapability.SQL_SERVICE_QUERY)
                ));
    }

    @Test
    void postgresWrapsDataAndCountQueriesWithStableBindingOrder() {
        var postgres = BuiltInDialects.registry().require("POSTGRESQL");
        List<SqlQueryParameter> base = List.of(new SqlQueryParameter(
                1001L, PlatformTypeDefinition.of(PlatformDataType.LONG)
        ));

        var query = postgres.compileSqlServiceQuery(
                "SELECT id FROM customer WHERE department_id = ?", base, 40, 20, true
        );

        assertEquals(
                "SELECT * FROM (SELECT id FROM customer WHERE department_id = ?) ds_query LIMIT ? OFFSET ?",
                query.dataQuery().sql()
        );
        assertEquals(List.of(1001L, 20, 40), query.dataQuery().parameters().stream()
                .map(SqlQueryParameter::value).toList());
        assertEquals(
                "SELECT COUNT(*) FROM (SELECT id FROM customer WHERE department_id = ?) ds_count",
                query.countQuery().sql()
        );
        assertEquals(List.of(1001L), query.countQuery().parameters().stream()
                .map(SqlQueryParameter::value).toList());

        assertThrows(UnsupportedOperationException.class, () -> BuiltInDialects.registry().require("MYSQL")
                .compileSqlServiceQuery("SELECT 1", List.of(), 0, 20, false));
    }

    @Test
    void clickHouseWrapsDataAndCountQueriesWithStableBindingOrder() {
        var clickHouse = BuiltInDialects.registry().require("CLICKHOUSE");
        List<SqlQueryParameter> base = List.of(new SqlQueryParameter(
                "paid", PlatformTypeDefinition.of(PlatformDataType.STRING)
        ));

        var query = clickHouse.compileSqlServiceQuery(
                "SELECT order_id FROM orders WHERE status = ?", base, 20, 10, true
        );

        assertEquals(
                "SELECT * FROM (SELECT order_id FROM orders WHERE status = ?) ds_query LIMIT ? OFFSET ?",
                query.dataQuery().sql()
        );
        assertEquals(List.of("paid", 10, 20), query.dataQuery().parameters().stream()
                .map(SqlQueryParameter::value).toList());
        assertEquals(
                "SELECT COUNT(*) FROM (SELECT order_id FROM orders WHERE status = ?) ds_count",
                query.countQuery().sql()
        );
        assertEquals(List.of("paid"), query.countQuery().parameters().stream()
                .map(SqlQueryParameter::value).toList());
    }
}
