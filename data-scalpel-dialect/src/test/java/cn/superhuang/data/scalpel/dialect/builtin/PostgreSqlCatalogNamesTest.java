package cn.superhuang.data.scalpel.dialect.builtin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgreSqlCatalogNamesTest {

    @Test
    void detectsKingbaseSysCatalog() throws Exception {
        PostgreSqlCatalogNames names = PostgreSqlCatalogNames.detectKingbase(
                connection(Set.of("sys_catalog.sys_class"), "KingbaseES"));

        assertEquals("sys_catalog.sys_class", names.relation("class"));
        assertEquals("sys_catalog.sys_total_relation_size", names.function("total_relation_size"));
    }

    @Test
    void detectsKingbasePgCatalog() throws Exception {
        PostgreSqlCatalogNames names = PostgreSqlCatalogNames.detectKingbase(
                connection(Set.of("pg_catalog.pg_class"), "KingbaseES"));

        assertEquals("pg_catalog.pg_class", names.relation("class"));
        assertEquals("pg_catalog.pg_total_relation_size", names.function("total_relation_size"));
    }

    @Test
    void fallsBackToSysCatalogForKingbaseProductName() throws Exception {
        PostgreSqlCatalogNames names = PostgreSqlCatalogNames.detectKingbase(
                connection(Set.of(), "KingbaseES V9"));

        assertEquals("sys_catalog.sys_namespace", names.relation("namespace"));
    }

    private static Connection connection(Set<String> relations, String productName) {
        DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaData.class.getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                (proxy, method, arguments) -> switch (method.getName()) {
            case "getDatabaseProductName" -> productName;
            case "getTables" -> {
                String schema = (String) arguments[1];
                String relation = (String) arguments[2];
                boolean found = relations.stream().anyMatch(item ->
                        item.equalsIgnoreCase(schema + "." + relation));
                yield resultSet(found);
            }
            default -> defaultValue(method.getReturnType());
        });
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, arguments) -> switch (method.getName()) {
            case "getMetaData" -> metadata;
            case "getCatalog" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static ResultSet resultSet(boolean found) {
        boolean[] first = {true};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (proxy, method, arguments) -> switch (method.getName()) {
            case "next" -> found && first[0] && !(first[0] = false);
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
