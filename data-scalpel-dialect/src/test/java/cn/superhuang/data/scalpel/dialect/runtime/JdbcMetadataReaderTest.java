package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableQuery;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JdbcMetadataReaderTest {

    @Test
    void keepsColumnSizeOnlyForBoundedCharacterColumns() throws Exception {
        JdbcMetadataReader reader = new JdbcMetadataReader(BuiltInDialects.registry().require("POSTGRESQL"));
        var table = reader.readTable(connection(), new TableIdentifier("warehouse", "public", "spatial_asset"));

        assertNull(table.columns().get(0).length());
        assertEquals(128, table.columns().get(1).length());
        assertNull(table.columns().get(2).length());
    }

    @Test
    void normalizesSchemaReportedByCatalogOnlyDriver() throws Exception {
        JdbcMetadataReader reader = new JdbcMetadataReader(BuiltInDialects.registry().require("CLICKHOUSE"));
        DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getTables" -> resultSet(List.of(Map.of(
                    "TABLE_CAT", "",
                    "TABLE_SCHEM", "4a",
                    "TABLE_NAME", "jxsl_sys_dept",
                    "TABLE_TYPE", "MergeTree",
                    "REMARKS", ""
            )));
            default -> defaultValue(method.getReturnType());
        });
        Connection connection = proxy(Connection.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getMetaData" -> metadata;
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        });

        var tables = reader.listTables(
                connection,
                new JdbcConnectionConfig("localhost", 8123, "4a", null, "default", null, Map.of()),
                new TableQuery(null, null, null, true, 10)
        );

        assertEquals(new TableIdentifier("4a", null, "jxsl_sys_dept"), tables.tables().getFirst().identifier());
    }

    private static Connection connection() {
        DatabaseMetaData metadata = proxy(DatabaseMetaData.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getTables" -> resultSet(List.of(Map.of(
                    "TABLE_CAT", "warehouse",
                    "TABLE_SCHEM", "public",
                    "TABLE_NAME", "spatial_asset",
                    "TABLE_TYPE", "TABLE",
                    "REMARKS", ""
            )));
            case "getColumns" -> resultSet(List.of(
                    column("shape", 1, Types.OTHER, "\"public\".\"geometry\"", Integer.MAX_VALUE),
                    column("name", 2, Types.VARCHAR, "varchar", 128),
                    column("created_at", 3, Types.DATE, "date", 10)
            ));
            case "getPrimaryKeys", "getIndexInfo" -> resultSet(List.of());
            default -> defaultValue(method.getReturnType());
        });
        return proxy(Connection.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getMetaData" -> metadata;
            case "createStatement" -> statement();
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Map<String, Object> column(
            String name,
            int ordinal,
            int jdbcType,
            String nativeType,
            int columnSize
    ) {
        return Map.ofEntries(
                Map.entry("TABLE_NAME", "spatial_asset"),
                Map.entry("COLUMN_NAME", name),
                Map.entry("ORDINAL_POSITION", ordinal),
                Map.entry("DATA_TYPE", jdbcType),
                Map.entry("TYPE_NAME", nativeType),
                Map.entry("COLUMN_SIZE", columnSize),
                Map.entry("DECIMAL_DIGITS", 0),
                Map.entry("NULLABLE", DatabaseMetaData.columnNullable),
                Map.entry("COLUMN_DEF", ""),
                Map.entry("IS_AUTOINCREMENT", "NO"),
                Map.entry("IS_GENERATEDCOLUMN", "NO"),
                Map.entry("REMARKS", "")
        );
    }

    private static Statement statement() {
        return proxy(Statement.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "executeQuery" -> resultSet(List.of());
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static ResultSet resultSet(List<Map<String, Object>> rows) {
        class Cursor {
            private int index = -1;
            private Object lastValue;
        }
        Cursor cursor = new Cursor();
        return proxy(ResultSet.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "next" -> ++cursor.index < rows.size();
            case "getString" -> {
                Object value = value(rows, cursor.index, arguments[0]);
                cursor.lastValue = value;
                yield value == null ? null : String.valueOf(value);
            }
            case "getInt" -> {
                Object value = value(rows, cursor.index, arguments[0]);
                cursor.lastValue = value;
                yield value == null ? 0 : ((Number) value).intValue();
            }
            case "getShort" -> {
                Object value = value(rows, cursor.index, arguments[0]);
                cursor.lastValue = value;
                yield value == null ? (short) 0 : ((Number) value).shortValue();
            }
            case "wasNull" -> cursor.lastValue == null;
            case "close" -> null;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Object value(List<Map<String, Object>> rows, int index, Object key) {
        if (index < 0 || index >= rows.size()) {
            return null;
        }
        return rows.get(index).get(String.valueOf(key));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
