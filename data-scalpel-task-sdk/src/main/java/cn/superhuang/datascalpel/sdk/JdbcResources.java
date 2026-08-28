package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public interface JdbcResources {
    Dataset<Row> readTable(String bindingName, JdbcTableIdentifier table, JdbcReadOptions options);

    default Dataset<Row> readTable(String bindingName, JdbcTableIdentifier table) {
        return readTable(bindingName, table, JdbcReadOptions.defaults());
    }

    default Dataset<Row> readTable(String bindingName, String table, JdbcReadOptions options) {
        return readTable(bindingName, JdbcTableIdentifier.table(table), options);
    }

    default Dataset<Row> readTable(String bindingName, String table) {
        return readTable(bindingName, JdbcTableIdentifier.table(table));
    }

    default Dataset<Row> readTable(String bindingName, String schema, String table, JdbcReadOptions options) {
        return readTable(bindingName, JdbcTableIdentifier.schemaTable(schema, table), options);
    }

    default Dataset<Row> readTable(String bindingName, String schema, String table) {
        return readTable(bindingName, JdbcTableIdentifier.schemaTable(schema, table));
    }

    Dataset<Row> readQuery(String bindingName, String sql, JdbcReadOptions options);

    default Dataset<Row> readQuery(String bindingName, String sql) {
        return readQuery(bindingName, sql, JdbcReadOptions.defaults());
    }

    JdbcWriteOperation write(String bindingName, Dataset<Row> source);
}
