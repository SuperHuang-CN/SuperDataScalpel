package cn.superhuang.datascalpel.taskengine.spark;

import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Objects;

public record SparkCanvasTable(CanvasTableSchema schema, Dataset<Row> dataset) {
    public SparkCanvasTable {
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(dataset, "dataset");
    }

    public String name() {
        return schema.name();
    }
}
