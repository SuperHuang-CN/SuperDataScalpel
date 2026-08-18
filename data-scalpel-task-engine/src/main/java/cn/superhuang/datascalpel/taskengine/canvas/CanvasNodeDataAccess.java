package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcIncrementalInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcSnapshotSyncOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelSnapshotSyncOutputNodeDefinition;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;

public interface CanvasNodeDataAccess extends AutoCloseable {

    Dataset<Row> readJdbcInput(JdbcInputNodeDefinition node, CanvasTableSchema logicalSchema);

    Dataset<Row> readJdbcIncrementalInput(
            JdbcIncrementalInputNodeDefinition node,
            CanvasTableSchema logicalSchema
    );

    Dataset<Row> readJdbcQueryInput(JdbcQueryInputNodeDefinition node, CanvasTableSchema logicalSchema);

    Dataset<Row> readFileDatasetInput(
            FileDatasetInputNodeDefinition node,
            MetadataIndex.FileDatasetTableEntry table,
            CanvasTableSchema logicalSchema
    );

    Dataset<Row> readHttpApiInput(HttpApiInputNodeDefinition node, CanvasTableSchema logicalSchema);

    Dataset<Row> readSpatialServiceInput(SpatialServiceInputNodeDefinition node, CanvasTableSchema logicalSchema);

    Dataset<Row> readKafkaInput(KafkaInputNodeDefinition node, CanvasTableSchema logicalSchema);

    Dataset<Row> readTdEngineTmqInput(
            TdEngineTmqInputNodeDefinition node,
            CanvasTableSchema logicalSchema
    );

    Dataset<Row> readModelInput(
            ModelInputNodeDefinition node,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema logicalSchema
    );

    CanvasPreparedOutput prepareJdbcOutput(
            JdbcOutputNodeDefinition node,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
    );

    CanvasPreparedOutput prepareModelOutput(
            ModelOutputNodeDefinition node,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset,
            List<String> upsertKeyColumns
    );

    CanvasPreparedSnapshotSyncOutput prepareJdbcSnapshotSyncOutput(
            JdbcSnapshotSyncOutputNodeDefinition node,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
    );

    CanvasPreparedSnapshotSyncOutput prepareModelSnapshotSyncOutput(
            ModelSnapshotSyncOutputNodeDefinition node,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
    );

    CanvasPreparedKafkaOutput prepareKafkaOutput(
            KafkaOutputNodeDefinition node,
            Dataset<Row> dataset
    );

    CanvasPreparedFileOutput prepareFileOutput(
            FileOutputNodeDefinition node,
            CanvasTableSchema sourceSchema,
            Dataset<Row> dataset
    );

    @Override
    default void close() {
    }
}
