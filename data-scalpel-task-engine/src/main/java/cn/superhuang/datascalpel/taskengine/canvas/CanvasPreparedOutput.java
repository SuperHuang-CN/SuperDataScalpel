package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JdbcWriteMode;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.Map;
import java.util.List;
import java.util.Objects;

public record CanvasPreparedOutput(
        CanvasNodeDefinition node,
        RuntimeDataSource runtimeDataSource,
        String qualifiedTableName,
        String displayTarget,
        JdbcWriteMode writeMode,
        Dataset<Row> dataset,
        CanvasTableSchema targetSchema,
        Map<String, Integer> geometryLocalSrids,
        List<String> upsertKeyColumns
) {
    public CanvasPreparedOutput {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(runtimeDataSource, "runtimeDataSource");
        Objects.requireNonNull(qualifiedTableName, "qualifiedTableName");
        Objects.requireNonNull(displayTarget, "displayTarget");
        Objects.requireNonNull(writeMode, "writeMode");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(targetSchema, "targetSchema");
        geometryLocalSrids = geometryLocalSrids == null ? Map.of() : Map.copyOf(geometryLocalSrids);
        upsertKeyColumns = upsertKeyColumns == null ? List.of() : List.copyOf(upsertKeyColumns);
    }
}
