package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.FileOutputFormatOptions;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Objects;

public record CanvasPreparedFileOutput(
        FileOutputNodeDefinition node,
        String writeId,
        String sourceTableName,
        RuntimeDataSource runtimeDataSource,
        String targetDisplayName,
        String targetUri,
        FileOutputConflictPolicy conflictPolicy,
        FileOutputFormatOptions formatOptions,
        CanvasTableSchema sourceSchema,
        Dataset<Row> dataset
) {
    public CanvasPreparedFileOutput {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(writeId, "writeId");
        Objects.requireNonNull(sourceTableName, "sourceTableName");
        Objects.requireNonNull(runtimeDataSource, "runtimeDataSource");
        Objects.requireNonNull(targetDisplayName, "targetDisplayName");
        Objects.requireNonNull(targetUri, "targetUri");
        Objects.requireNonNull(conflictPolicy, "conflictPolicy");
        Objects.requireNonNull(formatOptions, "formatOptions");
        Objects.requireNonNull(sourceSchema, "sourceSchema");
        Objects.requireNonNull(dataset, "dataset");
    }
}
