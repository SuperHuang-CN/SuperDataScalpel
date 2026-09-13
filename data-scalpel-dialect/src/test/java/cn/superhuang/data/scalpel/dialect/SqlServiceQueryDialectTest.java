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
    void configuredDatabasesAdvertiseSqlServiceQueries() {
        var registry = BuiltInDialects.registry();
        Set<String> supported = Set.of(
                "POSTGRESQL", "HIGHGO", "MYSQL", "OPENGAUSS", "KINGBASE", "DAMENG",
                "ORACLE", "SQL_SERVER", "CLICKHOUSE");
        supported.forEach(id -> assertTrue(registry.require(id).definition().capabilities()
                .contains(DatabaseCapability.SQL_SERVICE_QUERY), id));
        registry.all().stream()
                .filter(dialect -> !supported.contains(dialect.definition().id()))
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

        assertThrows(UnsupportedOperationException.class, () -> BuiltInDialects.registry().require("TDENGINE_RESTFUL")
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

    @Test
    void damengUsesOffsetFetchWithStableBindingOrder() {
        var dameng = BuiltInDialects.registry().require("DAMENG");
        List<SqlQueryParameter> base = List.of(new SqlQueryParameter(
                1001L, PlatformTypeDefinition.of(PlatformDataType.LONG)
        ));

        var query = dameng.compileSqlServiceQuery(
                "SELECT id FROM customer WHERE department_id = ?", base, 40, 20, true
        );

        assertEquals(
                "SELECT * FROM (SELECT id FROM customer WHERE department_id = ?) ds_query "
                        + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                query.dataQuery().sql()
        );
        assertEquals(List.of(1001L, 40, 20), query.dataQuery().parameters().stream()
                .map(SqlQueryParameter::value).toList());
        assertEquals(List.of(1001L), query.countQuery().parameters().stream()
                .map(SqlQueryParameter::value).toList());
    }

    @Test
    void oracleAndSqlServerUseTheirNativeOffsetFetchWrappers() {
        List<SqlQueryParameter> base = List.of(new SqlQueryParameter(
                1001L, PlatformTypeDefinition.of(PlatformDataType.LONG)
        ));
        var oracle = BuiltInDialects.registry().require("ORACLE").compileSqlServiceQuery(
                "SELECT id FROM customer WHERE department_id = ?", base, 40, 20, false
        );
        var sqlServer = BuiltInDialects.registry().require("SQL_SERVER").compileSqlServiceQuery(
                "SELECT id FROM customer WHERE department_id = ?", base, 40, 20, false
        );

        assertEquals(
                "SELECT * FROM (SELECT id FROM customer WHERE department_id = ?) ds_query "
                        + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                oracle.dataQuery().sql()
        );
        assertEquals(
                "SELECT * FROM (SELECT id FROM customer WHERE department_id = ?) ds_query "
                        + "ORDER BY (SELECT NULL) OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                sqlServer.dataQuery().sql()
        );
        assertEquals(List.of(1001L, 40, 20), sqlServer.dataQuery().parameters().stream()
                .map(SqlQueryParameter::value).toList());
    }
}
