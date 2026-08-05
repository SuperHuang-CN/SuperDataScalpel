package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.functions;

import java.util.List;
import java.util.Objects;

public final class SchemaOnlyCanvasNodeDataAccess implements CanvasNodeDataAccess {
    private final SparkSession sparkSession;

    public SchemaOnlyCanvasNodeDataAccess(SparkSession sparkSession) {
        this.sparkSession = Objects.requireNonNull(sparkSession, "sparkSession");
    }

    @Override
    public Dataset<Row> readJdbcInput(JdbcInputNodeDefinition node, CanvasTableSchema expectedSchema) {
        return empty(expectedSchema);
    }

    @Override
    public Dataset<Row> readJdbcQueryInput(
            JdbcQueryInputNodeDefinition node,
            CanvasTableSchema expectedSchema
    ) {
        return empty(expectedSchema);
    }

    @Override
    public Dataset<Row> readFileDatasetInput(
            FileDatasetInputNodeDefinition node,
            MetadataIndex.FileDatasetTableEntry table,
            CanvasTableSchema expectedSchema
    ) {
        return empty(expectedSchema);
    }

    @Override
    public Dataset<Row> readHttpApiInput(HttpApiInputNodeDefinition node, CanvasTableSchema expectedSchema) {
        return empty(expectedSchema);
    }

    @Override
    public Dataset<Row> readSpatialServiceInput(SpatialServiceInputNodeDefinition node, CanvasTableSchema expectedSchema) {
        return empty(expectedSchema);
    }

    @Override
    public Dataset<Row> readKafkaInput(KafkaInputNodeDefinition node, CanvasTableSchema expectedSchema) {
        Dataset<Row> rate = sparkSession.readStream()
                .format("rate")
                .option("rowsPerSecond", 1)
                .load();
        Column[] columns = expectedSchema.columns().stream()
                .map(column -> functions.lit(null)
                        .cast(SparkTypeMapper.toDataType(column))
                        .alias(column.name()))
                .toArray(Column[]::new);
        return rate.select(columns);
    }

    @Override
    public Dataset<Row> readModelInput(
            ModelInputNodeDefinition node,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema expectedSchema
    ) {
        return empty(expectedSchema);
    }

    @Override
    public CanvasPreparedOutput prepareJdbcOutput(
            JdbcOutputNodeDefinition node,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
    ) {
        dataset.schema();
        return null;
    }

    @Override
    public CanvasPreparedOutput prepareModelOutput(
            ModelOutputNodeDefinition node,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
    ) {
        dataset.schema();
        return null;
    }

    @Override
    public CanvasPreparedKafkaOutput prepareKafkaOutput(
            KafkaOutputNodeDefinition node,
            Dataset<Row> dataset
    ) {
        dataset.schema();
        return null;
    }

    @Override
    public CanvasPreparedFileOutput prepareFileOutput(
            FileOutputNodeDefinition node,
            CanvasTableSchema sourceSchema,
            Dataset<Row> dataset
    ) {
        dataset.schema();
        return null;
    }

    private Dataset<Row> empty(CanvasTableSchema schema) {
        return sparkSession.createDataFrame(
                List.<Row>of(),
                SparkTypeMapper.toStructType(schema.columns())
        );
    }
}
