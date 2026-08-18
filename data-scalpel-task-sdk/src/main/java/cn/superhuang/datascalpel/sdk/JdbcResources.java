package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public interface JdbcResources {
    Dataset<Row> readTable(String bindingName, JdbcTableIdentifier table);

    Dataset<Row> readQuery(String bindingName, String sql);

    JdbcWriteOperation write(String bindingName, Dataset<Row> source);
}
