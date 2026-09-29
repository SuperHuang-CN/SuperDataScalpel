package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Map;
import java.util.List;
import java.util.Objects;

public record CanvasPreparedOutput(
        CanvasNodeDefinition node,
        String writeId,
        String sourceTableName,
        RuntimeDataSource runtimeDataSource,
        TableIdentifier targetTable,
        String qualifiedTableName,
        String displayTarget,
        JdbcWriteMode writeMode,
        Dataset<Row> dataset,
        CanvasTableSchema targetSchema,
        Map<String, Integer> geometryWriteSrids,
        List<String> upsertKeyColumns,
        cn.superhuang.data.scalpel.contract.task.BatchWriteOptions batchWrite
) {
    public CanvasPreparedOutput(CanvasNodeDefinition node, String writeId, String sourceTableName,
            RuntimeDataSource runtimeDataSource, TableIdentifier targetTable, String qualifiedTableName,
            String displayTarget, JdbcWriteMode writeMode, Dataset<Row> dataset, CanvasTableSchema targetSchema,
            Map<String, Integer> geometryWriteSrids, List<String> upsertKeyColumns) {
        this(node, writeId, sourceTableName, runtimeDataSource, targetTable, qualifiedTableName,
                displayTarget, writeMode, dataset, targetSchema, geometryWriteSrids, upsertKeyColumns, null);
    }
    public CanvasPreparedOutput {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(writeId, "writeId");
        Objects.requireNonNull(sourceTableName, "sourceTableName");
        Objects.requireNonNull(runtimeDataSource, "runtimeDataSource");
        Objects.requireNonNull(targetTable, "targetTable");
        Objects.requireNonNull(qualifiedTableName, "qualifiedTableName");
        Objects.requireNonNull(displayTarget, "displayTarget");
        Objects.requireNonNull(writeMode, "writeMode");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(targetSchema, "targetSchema");
        geometryWriteSrids = geometryWriteSrids == null ? Map.of() : Map.copyOf(geometryWriteSrids);
        upsertKeyColumns = upsertKeyColumns == null ? List.of() : List.copyOf(upsertKeyColumns);
    }
}
