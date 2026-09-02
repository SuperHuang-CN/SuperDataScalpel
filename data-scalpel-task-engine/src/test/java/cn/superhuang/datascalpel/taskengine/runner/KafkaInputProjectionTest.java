package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.KafkaInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.KafkaInputMetadataField;
import cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.KafkaInputValueFormat;
import cn.superhuang.data.scalpel.contract.task.KafkaStartingOffsets;
import cn.superhuang.data.scalpel.contract.task.KafkaValueColumn;
import cn.superhuang.data.scalpel.contract.task.KafkaValueSchema;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KafkaInputProjectionTest {
    private static SparkSession spark;

    @BeforeAll
    static void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("kafka-input-projection-test")
                .config("spark.ui.enabled", "false")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();
    }

    @AfterAll
    static void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void decodesJsonAndPreservesMetadataForTombstones() {
        Timestamp timestamp = Timestamp.from(Instant.parse("2026-08-30T00:00:00Z"));
        Dataset<Row> raw = rawRows(List.of(
                RowFactory.create(bytes("order-1"), bytes("{\"id\":7}"), "orders", 2, 15L, timestamp),
                RowFactory.create(bytes("order-2"), null, "orders", 2, 16L, timestamp)
        ));
        KafkaValueSchema valueSchema = new KafkaValueSchema(List.of(new KafkaValueColumn(
                "id", PlatformDataType.LONG, null, null, null, true, null
        )));
        KafkaInputNodeDefinition node = node(
                KafkaInputValueFormat.JSON,
                valueSchema,
                List.of(KafkaInputMetadataField.KEY, KafkaInputMetadataField.OFFSET)
        );
        CanvasTableSchema logicalSchema = schema(List.of(
                column("id", PlatformDataType.LONG),
                column("_kafka_key", PlatformDataType.BINARY),
                column("_kafka_offset", PlatformDataType.LONG)
        ));

        List<Row> rows = RuntimeCanvasNodeDataAccess.projectKafkaInput(raw, node, logicalSchema).collectAsList();

        assertEquals(7L, rows.getFirst().getLong(0));
        assertArrayEquals(bytes("order-1"), rows.getFirst().getAs(1));
        assertEquals(15L, rows.getFirst().getLong(2));
        assertNull(rows.get(1).get(0));
        assertArrayEquals(bytes("order-2"), rows.get(1).getAs(1));
        assertEquals(16L, rows.get(1).getLong(2));
    }

    @Test
    void decodesUtf8TextAndKeepsBinaryBytesLossless() {
        byte[] payload = bytes("中文消息");
        Dataset<Row> raw = rawRows(List.of(RowFactory.create(
                null, payload, "events", 0, 1L, Timestamp.from(Instant.EPOCH)
        )));

        Row text = RuntimeCanvasNodeDataAccess.projectKafkaInput(
                raw,
                node(KafkaInputValueFormat.TEXT, new KafkaValueSchema(List.of()), List.of()),
                schema(List.of(column("value", PlatformDataType.STRING)))
        ).head();
        Row binary = RuntimeCanvasNodeDataAccess.projectKafkaInput(
                raw,
                node(KafkaInputValueFormat.BINARY, new KafkaValueSchema(List.of()), List.of()),
                schema(List.of(column("value", PlatformDataType.BINARY)))
        ).head();

        assertEquals("中文消息", text.getString(0));
        assertArrayEquals(payload, binary.getAs(0));
    }

    private static Dataset<Row> rawRows(List<Row> rows) {
        StructType schema = new StructType()
                .add("key", DataTypes.BinaryType, true)
                .add("value", DataTypes.BinaryType, true)
                .add("topic", DataTypes.StringType, true)
                .add("partition", DataTypes.IntegerType, true)
                .add("offset", DataTypes.LongType, true)
                .add("timestamp", DataTypes.TimestampType, true);
        return spark.createDataFrame(rows, schema);
    }

    private static KafkaInputNodeDefinition node(
            KafkaInputValueFormat valueFormat,
            KafkaValueSchema valueSchema,
            List<KafkaInputMetadataField> metadataFields
    ) {
        return new KafkaInputNodeDefinition(
                UUID.randomUUID().toString(),
                "Kafka 输入",
                new CanvasNodeLayout(0D, 0D, 320D, 120D),
                new KafkaInputConfiguration(
                        UUID.randomUUID().toString(), "events", valueSchema, "events",
                        KafkaStartingOffsets.LATEST, 10, valueFormat, metadataFields
                )
        );
    }

    private static CanvasTableSchema schema(List<CanvasColumnSchema> columns) {
        return new CanvasTableSchema("events", null, columns, CanvasDatasetKind.UNBOUNDED, null, null);
    }

    private static CanvasColumnSchema column(String name, PlatformDataType type) {
        return new CanvasColumnSchema(
                name, type, null, null, null, true,
                null, false, false, null
        );
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
