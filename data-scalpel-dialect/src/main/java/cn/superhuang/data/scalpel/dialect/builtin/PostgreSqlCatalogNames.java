package cn.superhuang.data.scalpel.dialect.builtin;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;

/** System-catalog vocabulary used by PostgreSQL-family databases. */
record PostgreSqlCatalogNames(String schema, String objectPrefix) {

    private static final PostgreSqlCatalogNames POSTGRESQL =
            new PostgreSqlCatalogNames("pg_catalog", "pg_");
    private static final PostgreSqlCatalogNames KINGBASE =
            new PostgreSqlCatalogNames("sys_catalog", "sys_");

    static PostgreSqlCatalogNames postgresql() {
        return POSTGRESQL;
    }

    static PostgreSqlCatalogNames detectKingbase(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        if (containsRelation(metadata, catalog, KINGBASE.relationName("class"), KINGBASE.schema())) {
            return KINGBASE;
        }
        if (containsRelation(metadata, catalog, POSTGRESQL.relationName("class"), POSTGRESQL.schema())) {
            return POSTGRESQL;
        }
        String product = metadata.getDatabaseProductName();
        return product != null && product.toLowerCase(Locale.ROOT).contains("kingbase")
                ? KINGBASE : POSTGRESQL;
    }

    String relation(String baseName) {
        return schema + "." + relationName(baseName);
    }

    String function(String baseName) {
        return schema + "." + relationName(baseName);
    }

    private String relationName(String baseName) {
        return objectPrefix + baseName;
    }

    private static boolean containsRelation(
            DatabaseMetaData metadata,
            String catalog,
            String relation,
            String schema
    ) throws SQLException {
        for (String schemaPattern : List.of(schema, schema.toUpperCase(Locale.ROOT))) {
            for (String relationPattern : List.of(relation, relation.toUpperCase(Locale.ROOT))) {
                try (ResultSet resultSet = metadata.getTables(
                        catalog, schemaPattern, relationPattern,
                        new String[]{"TABLE", "SYSTEM TABLE", "VIEW"}
                )) {
                    if (resultSet.next()) {
                        return true;
                    }
                } catch (SQLException ignored) {
                    // Some Kingbase releases reject catalog filters they do not expose. Try the other vocabulary.
                }
            }
        }
        return false;
    }
}
