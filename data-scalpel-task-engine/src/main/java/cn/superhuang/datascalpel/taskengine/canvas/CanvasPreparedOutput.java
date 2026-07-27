package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Objects;

public record CanvasPreparedOutput(
        CanvasNodeDefinition node,
        RuntimeDataSource runtimeDataSource,
        String qualifiedTableName,
        String displayTarget,
        JdbcWriteMode writeMode,
        Dataset<Row> dataset
) {
    public CanvasPreparedOutput {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(runtimeDataSource, "runtimeDataSource");
        Objects.requireNonNull(qualifiedTableName, "qualifiedTableName");
        Objects.requireNonNull(displayTarget, "displayTarget");
        Objects.requireNonNull(writeMode, "writeMode");
        Objects.requireNonNull(dataset, "dataset");
    }
}
