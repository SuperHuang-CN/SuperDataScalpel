package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.FileDatasetInputTableSelection;
import cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputResourceSelection;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceInputResourceSelection;
import cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcInputTableSelection;
import cn.superhuang.data.scalpel.contract.task.JdbcIncrementalInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.JdbcSnapshotSyncOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelInputSelection;
import cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.ModelSnapshotSyncOutputNodeDefinition;
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
    public Dataset<Row> readJdbcInput(
            JdbcInputNodeDefinition node,
            JdbcInputTableSelection table,
            CanvasTableSchema logicalSchema
    ) {
        return empty(logicalSchema);
    }

    @Override
    public Dataset<Row> readJdbcIncrementalInput(
            JdbcIncrementalInputNodeDefinition node,
            CanvasTableSchema logicalSchema
    ) {
        return readKafkaInput(null, logicalSchema);
    }

    @Override
    public Dataset<Row> readJdbcQueryInput(
            JdbcQueryInputNodeDefinition node,
            CanvasTableSchema logicalSchema
    ) {
        return empty(logicalSchema);
    }

    @Override
    public Dataset<Row> readFileDatasetInput(
            FileDatasetInputNodeDefinition node,
            FileDatasetInputTableSelection selection,
            MetadataIndex.FileDatasetTableEntry table,
            CanvasTableSchema logicalSchema
    ) {
        return empty(logicalSchema);
    }

    @Override
    public Dataset<Row> readHttpApiInput(
            HttpApiInputNodeDefinition node,
            HttpApiInputResourceSelection selection,
            CanvasTableSchema logicalSchema
    ) {
        return empty(logicalSchema);
    }

    @Override
    public Dataset<Row> readSpatialServiceInput(
            SpatialServiceInputNodeDefinition node,
            SpatialServiceInputResourceSelection selection,
            CanvasTableSchema logicalSchema
    ) {
        return empty(logicalSchema);
    }

    @Override
    public Dataset<Row> readKafkaInput(KafkaInputNodeDefinition node, CanvasTableSchema logicalSchema) {
        Dataset<Row> rate = sparkSession.readStream()
                .format("rate")
                .option("rowsPerSecond", 1)
                .load();
        Column[] columns = logicalSchema.columns().stream()
                .map(column -> functions.lit(null)
                        .cast(SparkTypeMapper.toDataType(column))
                        .alias(column.name()))
                .toArray(Column[]::new);
        return rate.select(columns);
    }

    @Override
    public Dataset<Row> readTdEngineTmqInput(
            TdEngineTmqInputNodeDefinition node,
            CanvasTableSchema logicalSchema
    ) {
        return readKafkaInput(null, logicalSchema);
    }

    @Override
    public Dataset<Row> readModelInput(
            ModelInputNodeDefinition node,
            ModelInputSelection selection,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema logicalSchema
    ) {
        return empty(logicalSchema);
    }

    @Override
    public CanvasPreparedOutput prepareJdbcOutput(
            JdbcOutputNodeDefinition node,
            cn.superhuang.data.scalpel.contract.task.JdbcOutputWrite write,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
    ) {
        dataset.schema();
        return null;
    }

    @Override
    public CanvasPreparedOutput prepareModelOutput(
            ModelOutputNodeDefinition node,
            cn.superhuang.data.scalpel.contract.task.ModelOutputWrite write,
            MetadataIndex.ModelEntry model,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset,
            List<String> upsertKeyColumns
    ) {
        dataset.schema();
        return null;
    }

    @Override
    public CanvasPreparedSnapshotSyncOutput prepareJdbcSnapshotSyncOutput(
            JdbcSnapshotSyncOutputNodeDefinition node,
            CanvasTableSchema targetSchema,
            Dataset<Row> dataset
    ) {
        dataset.schema();
        return null;
    }

    @Override
    public CanvasPreparedSnapshotSyncOutput prepareModelSnapshotSyncOutput(
            ModelSnapshotSyncOutputNodeDefinition node,
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
            cn.superhuang.data.scalpel.contract.task.KafkaOutputWrite write,
            Dataset<Row> dataset
    ) {
        dataset.schema();
        return null;
    }

    @Override
    public CanvasPreparedFileOutput prepareFileOutput(
            FileOutputNodeDefinition node,
            cn.superhuang.data.scalpel.contract.task.FileOutputWrite write,
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
