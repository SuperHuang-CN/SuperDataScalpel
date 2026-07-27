package cn.superhuang.datascalpel.taskengine.spark;

import cn.superhuang.datascalpel.taskengine.httpapi.HttpApiPullBatch;
import org.apache.spark.rdd.RDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.storage.StorageLevel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HttpApiBatchDatasetStagerTest {
    private final StructType schema = DataTypes.createStructType(List.of(
            DataTypes.createStructField("id", DataTypes.IntegerType, false),
            DataTypes.createStructField("name", DataTypes.StringType, true)
    ));
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("http-api-batch-dataset-stager-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .getOrCreate();
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void stagesMultipleBatchesAndPreservesSchemaRowsAndNulls() {
        try (HttpApiBatchDatasetStager stager = new HttpApiBatchDatasetStager(spark, schema)) {
            stager.accept(new HttpApiPullBatch(1, 1, List.of(
                    List.of(1, "first"),
                    Arrays.asList(2, null))));
            stager.accept(new HttpApiPullBatch(2, 2, List.of(List.of(3, "third"))));

            Dataset<Row> dataset = stager.dataset();
            List<Row> rows = dataset.orderBy("id").collectAsList();

            assertEquals(2, stager.stagedBatchCount());
            assertEquals(schema, dataset.schema());
            assertEquals(List.of(1, 2, 3), rows.stream().map(row -> row.getInt(0)).toList());
            assertEquals("first", rows.getFirst().getString(1));
            assertTrue(rows.get(1).isNullAt(1));
            assertEquals("third", rows.getLast().getString(1));
        }
    }

    @Test
    void usesDiskOnlyLocalCheckpointAndUnpersistsOnClose() {
        HttpApiBatchDatasetStager stager = new HttpApiBatchDatasetStager(spark, schema);
        stager.accept(new HttpApiPullBatch(1, 1, List.of(List.of(1, "first"))));
        stager.dataset();
        RDD<InternalRow> checkpointRdd = stager.stagedCheckpointRdd(0);

        assertEquals(StorageLevel.DISK_ONLY(), checkpointRdd.getStorageLevel());

        stager.close();

        assertEquals(StorageLevel.NONE(), checkpointRdd.getStorageLevel());
    }

    @Test
    void returnsAnEmptyDatasetWithTheDeclaredSchema() {
        try (HttpApiBatchDatasetStager stager = new HttpApiBatchDatasetStager(spark, schema)) {
            Dataset<Row> dataset = stager.dataset();

            assertEquals(schema, dataset.schema());
            assertTrue(dataset.collectAsList().isEmpty());
        }
    }

    @Test
    void rejectsAdditionalBatchesAfterTheDatasetIsExposed() {
        try (HttpApiBatchDatasetStager stager = new HttpApiBatchDatasetStager(spark, schema)) {
            stager.dataset();

            assertThrows(IllegalStateException.class, () -> stager.accept(
                    new HttpApiPullBatch(1, 1, List.of(List.of(1, "late")))));
        }
    }

    @Test
    void wrapsSparkMaterializationFailures() {
        try (HttpApiBatchDatasetStager stager = new HttpApiBatchDatasetStager(spark, schema)) {
            assertThrows(HttpApiBatchStagingException.class, () -> stager.accept(
                    new HttpApiPullBatch(1, 1, List.of(List.of("not-an-integer", "invalid")))));
        }
    }
}
