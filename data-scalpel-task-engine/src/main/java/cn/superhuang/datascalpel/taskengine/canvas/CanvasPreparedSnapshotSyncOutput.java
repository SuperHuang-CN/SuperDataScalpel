package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SnapshotDeletePolicy;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runtime-only plan shared by JDBC and model snapshot synchronization outputs. */
public record CanvasPreparedSnapshotSyncOutput(
        CanvasNodeDefinition node,
        RuntimeDataSource runtimeDataSource,
        TableIdentifier targetTable,
        String displayTarget,
        Dataset<Row> dataset,
        CanvasTableSchema targetSchema,
        List<String> keyColumns,
        SnapshotDeletePolicy deletePolicy,
        Map<String, Integer> geometryLocalSrids
) {
    public CanvasPreparedSnapshotSyncOutput {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(runtimeDataSource, "runtimeDataSource");
        Objects.requireNonNull(targetTable, "targetTable");
        Objects.requireNonNull(displayTarget, "displayTarget");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(targetSchema, "targetSchema");
        keyColumns = List.copyOf(keyColumns);
        Objects.requireNonNull(deletePolicy, "deletePolicy");
        geometryLocalSrids = geometryLocalSrids == null ? Map.of() : Map.copyOf(geometryLocalSrids);
    }
}
