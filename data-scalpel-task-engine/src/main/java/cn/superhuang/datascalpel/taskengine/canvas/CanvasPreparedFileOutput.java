package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Objects;

public record CanvasPreparedFileOutput(
        FileOutputNodeDefinition node,
        RuntimeDataSource runtimeDataSource,
        String targetUri,
        CanvasTableSchema sourceSchema,
        Dataset<Row> dataset
) {
    public CanvasPreparedFileOutput {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(runtimeDataSource, "runtimeDataSource");
        Objects.requireNonNull(targetUri, "targetUri");
        Objects.requireNonNull(sourceSchema, "sourceSchema");
        Objects.requireNonNull(dataset, "dataset");
    }
}
