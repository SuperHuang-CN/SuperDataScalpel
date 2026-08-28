package cn.superhuang.datascalpel.sdk;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public interface ModelResources {
    Dataset<Row> read(String bindingName, JdbcReadOptions options);

    default Dataset<Row> read(String bindingName) {
        return read(bindingName, JdbcReadOptions.defaults());
    }

    ModelWriteOperation write(String bindingName, Dataset<Row> source);
}
