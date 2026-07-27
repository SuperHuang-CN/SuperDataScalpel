package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.datascalpel.taskengine.contract.FileDatasetCompression;
import cn.superhuang.datascalpel.taskengine.contract.FileDatasetFormat;
import cn.superhuang.datascalpel.taskengine.contract.FileDatasetStorageKind;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeFileInput;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeFileParsingOptions;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeFileSource;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileDatasetBatchReaderRegistryTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("file-dataset-multi-source-reader-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .getOrCreate();
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) {
            spark.stop();
        }
    }

    @Test
    void unionsEverySourceByManifestOrderUsingTheSameSchema() {
        RuntimeFileSource first = source("first.jsonl");
        RuntimeFileSource second = source("second.jsonl");
        RuntimeFileInput input = new RuntimeFileInput(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "a".repeat(64),
                new RuntimeFileParsingOptions.JsonLines("UTF-8", null),
                List.of(first, second)
        );
        StructType schema = new StructType()
                .add("source_order", DataTypes.IntegerType, false)
                .add("source_name", DataTypes.StringType, false);

        Dataset<Row> combined = FileDatasetBatchReaderRegistry.unionSources(input, sourceInput -> {
            RuntimeFileSource source = sourceInput.source();
            int order = source.tableSourceId().equals(first.tableSourceId()) ? 0 : 1;
            return spark.createDataFrame(
                    List.of(RowFactory.create(order, source.objectKey())),
                    schema
            );
        });

        assertEquals(
                List.of("0:first.jsonl", "1:second.jsonl"),
                combined.collectAsList().stream()
                        .map(row -> row.getInt(0) + ":" + row.getString(1))
                        .toList()
        );
        assertEquals(schema, combined.schema());
    }

    private static RuntimeFileSource source(String objectKey) {
        return new RuntimeFileSource(
                UUID.randomUUID(),
                UUID.randomUUID(),
                FileDatasetFormat.JSONL,
                FileDatasetCompression.NONE,
                FileDatasetStorageKind.SINGLE_OBJECT,
                objectKey,
                null,
                "FILE"
        );
    }
}
