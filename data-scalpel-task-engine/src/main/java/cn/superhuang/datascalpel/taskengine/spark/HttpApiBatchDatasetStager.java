package cn.superhuang.datascalpel.taskengine.spark;

import cn.superhuang.datascalpel.taskengine.httpapi.HttpApiPullBatch;
import cn.superhuang.datascalpel.taskengine.httpapi.HttpApiPullBatchConsumer;
import org.apache.spark.rdd.RDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.execution.LogicalRDD;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.storage.StorageLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Materializes bounded HTTP API batches before they enter the downstream Spark plan. */
public final class HttpApiBatchDatasetStager implements HttpApiPullBatchConsumer, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpApiBatchDatasetStager.class);

    private final SparkSession spark;
    private final StructType schema;
    private final List<StagedBatch> staged = new ArrayList<>();
    private Dataset<Row> finalDataset;
    private int nextBatchNumber = 1;
    private boolean finalized;
    private boolean closed;

    public HttpApiBatchDatasetStager(SparkSession spark, StructType schema) {
        this.spark = Objects.requireNonNull(spark, "spark");
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    @Override
    public void accept(HttpApiPullBatch batch) {
        ensureOpen();
        if (finalized) throw new IllegalStateException("HTTP API batch staging is already finalized");
        Objects.requireNonNull(batch, "batch");
        if (batch.batchNumber() != nextBatchNumber) {
            throw new IllegalArgumentException("HTTP API batch numbers must be contiguous");
        }
        try {
            List<Row> rows = batch.rows().stream()
                    .map(values -> RowFactory.create(values.toArray()))
                    .toList();
            Dataset<Row> checkpointed = spark.createDataFrame(rows, schema)
                    .localCheckpoint(true, StorageLevel.DISK_ONLY());
            if (!(checkpointed.queryExecution().logical() instanceof LogicalRDD logicalRDD)) {
                throw new IllegalStateException("Local checkpoint did not produce a LogicalRDD");
            }
            staged.add(new StagedBatch(checkpointed, logicalRDD.rdd()));
            nextBatchNumber++;
        } catch (RuntimeException exception) {
            throw new HttpApiBatchStagingException("HTTP API batch materialization failed", exception);
        }
    }

    public Dataset<Row> dataset() {
        ensureOpen();
        if (finalized) return finalDataset;
        finalized = true;
        try {
            finalDataset = staged.isEmpty()
                    ? spark.createDataFrame(List.of(), schema)
                    : balancedUnion(staged.stream().map(StagedBatch::dataset).toList());
            return finalDataset;
        } catch (RuntimeException exception) {
            throw new HttpApiBatchStagingException("HTTP API batch union failed", exception);
        }
    }

    int stagedBatchCount() {
        return staged.size();
    }

    RDD<InternalRow> stagedCheckpointRdd(int index) {
        return staged.get(index).checkpointRdd();
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (int index = 0; index < staged.size(); index++) {
            try {
                staged.get(index).checkpointRdd().unpersist(false);
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "event=HTTP_API_BATCH_CLEANUP_FAILED batchNumber={} exceptionType={}",
                        index + 1,
                        exception.getClass().getName()
                );
            }
        }
        staged.clear();
        finalDataset = null;
    }

    private static Dataset<Row> balancedUnion(List<Dataset<Row>> datasets) {
        List<Dataset<Row>> level = new ArrayList<>(datasets);
        while (level.size() > 1) {
            List<Dataset<Row>> next = new ArrayList<>((level.size() + 1) / 2);
            for (int index = 0; index < level.size(); index += 2) {
                Dataset<Row> left = level.get(index);
                next.add(index + 1 < level.size() ? left.unionByName(level.get(index + 1)) : left);
            }
            level = next;
        }
        return level.getFirst();
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("HTTP API batch staging is closed");
    }

    private record StagedBatch(Dataset<Row> dataset, RDD<InternalRow> checkpointRdd) {
    }
}
