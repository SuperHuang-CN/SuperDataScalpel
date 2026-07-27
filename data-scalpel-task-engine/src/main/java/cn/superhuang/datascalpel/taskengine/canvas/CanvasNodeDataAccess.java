package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public interface CanvasNodeDataAccess extends AutoCloseable {

    Dataset<Row> readJdbcInput(JdbcInputNodeDefinition node, CanvasTableSchema expectedSchema);

    Dataset<Row> readFileDatasetInput(
            FileDatasetInputNodeDefinition node,
            MetadataIndex.FileDatasetTableEntry table,
            CanvasTableSchema expectedSchema
    );

    Dataset<Row> readHttpApiInput(HttpApiInputNodeDefinition node, CanvasTableSchema expectedSchema);

    Dataset<Row> readKafkaInput(KafkaInputNodeDefinition node, CanvasTableSchema expectedSchema);

    Dataset<Row> readModelInput(
            ModelInputNodeDefinition node,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema expectedSchema
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
            Dataset<Row> dataset
    );

    CanvasPreparedKafkaOutput prepareKafkaOutput(
            KafkaOutputNodeDefinition node,
            Dataset<Row> dataset
    );

    @Override
    default void close() {
    }
}
