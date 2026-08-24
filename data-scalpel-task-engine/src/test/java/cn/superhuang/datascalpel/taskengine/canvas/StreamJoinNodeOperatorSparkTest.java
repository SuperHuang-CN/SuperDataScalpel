package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JoinCondition;
import cn.superhuang.data.scalpel.contract.task.JoinOperator;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumn;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.StreamJoinConfiguration;
import cn.superhuang.data.scalpel.contract.task.StreamJoinNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.StreamJoinType;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StreamJoinNodeOperatorSparkTest {
    private static final CanvasNodeLayout LAYOUT = new CanvasNodeLayout(0d, 0d, 360d, 216d);

    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("stream-join-node-operator-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void projectsConfiguredFieldsAndRenamesEventTime() {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = apply(
                List.of(
                        output(JoinOutputColumnSource.LEFT, "event_time", "occurred_at", true),
                        output(JoinOutputColumnSource.LEFT, "id", "id", true),
                        output(JoinOutputColumnSource.RIGHT, "id", "dim_customer_id", true),
                        output(JoinOutputColumnSource.RIGHT, "name", "name", false),
                        output(JoinOutputColumnSource.RIGHT, "segment", "segment", true)
                ),
                issues
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        SparkCanvasTable joined = result.propagatedTables().get("enriched_events");
        assertArrayEquals(
                new String[]{"occurred_at", "id", "dim_customer_id", "segment"},
                joined.dataset().columns()
        );
        assertEquals("occurred_at", joined.schema().eventTimeColumn());
        assertEquals("10 minutes", joined.schema().watermarkDelay());
        Row row = joined.dataset().first();
        assertEquals(1L, row.<Long>getAs("id"));
        assertEquals(1L, row.<Long>getAs("dim_customer_id"));
        assertEquals("A", row.<String>getAs("segment"));
    }

    @Test
    void clearsWatermarkWhenEventTimeIsExcluded() {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = apply(
                List.of(
                        output(JoinOutputColumnSource.LEFT, "event_time", "event_time", false),
                        output(JoinOutputColumnSource.LEFT, "id", "id", true),
                        output(JoinOutputColumnSource.RIGHT, "id", "dim_customer_id", true)
                ),
                issues
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        CanvasTableSchema schema = result.propagatedTables().get("enriched_events").schema();
        assertNull(schema.eventTimeColumn());
        assertNull(schema.watermarkDelay());
    }

    @Test
    void preservesWatermarkWhenEventTimeKeepsItsName() {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = apply(
                List.of(
                        output(JoinOutputColumnSource.LEFT, "event_time", "event_time", true),
                        output(JoinOutputColumnSource.LEFT, "id", "id", true),
                        output(JoinOutputColumnSource.RIGHT, "id", "dim_customer_id", true)
                ),
                issues
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        CanvasTableSchema schema = result.propagatedTables().get("enriched_events").schema();
        assertEquals("event_time", schema.eventTimeColumn());
        assertEquals("10 minutes", schema.watermarkDelay());
    }

    @Test
    void rejectsDuplicateOutputNamesAndDuplicateSources() {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = apply(
                List.of(
                        output(JoinOutputColumnSource.LEFT, "id", "id", true),
                        output(JoinOutputColumnSource.LEFT, "id", "left_id", true),
                        output(JoinOutputColumnSource.RIGHT, "id", "ID", true)
                ),
                issues
        );

        assertTrue(issues.codes.contains("DUPLICATE_JOIN_OUTPUT_SOURCE"));
        assertTrue(issues.codes.contains("DUPLICATE_COLUMN_NAME"));
        assertTrue(result.propagatedTables().isEmpty());
    }

    private CanvasNodeOperationResult apply(
            List<JoinOutputColumn> outputColumns,
            RecordingIssueSink issues
    ) {
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("order_events", streamTable());
        inputs.put("dim_customer", dimensionTable());
        return new StreamJoinNodeOperator().apply(
                new StreamJoinNodeDefinition(
                        UUID.randomUUID().toString(),
                        "流维关联",
                        LAYOUT,
                        new StreamJoinConfiguration(
                                "order_events",
                                "dim_customer",
                                "enriched_events",
                                StreamJoinType.LEFT,
                                List.of(new JoinCondition("id", JoinOperator.EQUALS, "id")),
                                outputColumns
                        )
                ),
                inputs,
                new CanvasNodeOperationContext(
                        spark,
                        MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                        issues,
                        new SchemaOnlyCanvasNodeDataAccess(spark),
                        CanvasExecutionMode.STREAMING,
                        CanvasRuntimeValues.forPreview()
                )
        );
    }

    private SparkCanvasTable streamTable() {
        List<CanvasColumnSchema> columns = List.of(
                column("id", PlatformDataType.LONG, false),
                column("name", PlatformDataType.STRING, true),
                column("event_time", PlatformDataType.TIMESTAMP, false)
        );
        Dataset<Row> dataset = spark.createDataFrame(
                List.of(RowFactory.create(1L, "order", Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")))),
                SparkTypeMapper.toStructType(columns)
        );
        CanvasTableSchema schema = new CanvasTableSchema(
                "order_events", null, columns, CanvasDatasetKind.UNBOUNDED,
                "event_time", "10 minutes"
        );
        return new SparkCanvasTable(schema, dataset);
    }

    private SparkCanvasTable dimensionTable() {
        List<CanvasColumnSchema> columns = List.of(
                column("id", PlatformDataType.LONG, false),
                column("name", PlatformDataType.STRING, false),
                column("segment", PlatformDataType.STRING, true)
        );
        Dataset<Row> dataset = spark.createDataFrame(
                List.of(RowFactory.create(1L, "customer", "A")),
                SparkTypeMapper.toStructType(columns)
        );
        CanvasTableSchema schema = new CanvasTableSchema(
                "dim_customer", null, columns, CanvasDatasetKind.BOUNDED, null, null
        );
        return new SparkCanvasTable(schema, dataset);
    }

    private static JoinOutputColumn output(
            JoinOutputColumnSource source,
            String sourceColumn,
            String outputColumn,
            boolean included
    ) {
        return new JoinOutputColumn(source, sourceColumn, outputColumn, included);
    }

    private static CanvasColumnSchema column(
            String name,
            PlatformDataType type,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name,
                type,
                type == PlatformDataType.STRING ? 128 : null,
                null,
                null,
                nullable,
                null,
                false,
                false,
                null
        );
    }

    private static final class RecordingIssueSink implements CanvasNodeIssueSink {
        private final List<String> codes = new ArrayList<>();
        private boolean errors;

        @Override
        public void error(String code, String message, String path) {
            codes.add(code);
            errors = true;
        }

        @Override
        public void warning(String code, String message, String path) {
            codes.add(code);
        }

        @Override
        public boolean hasErrors() {
            return errors;
        }
    }
}
