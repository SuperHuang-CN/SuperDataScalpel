package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasLiteral;
import cn.superhuang.data.scalpel.contract.task.ColumnExpression;
import cn.superhuang.data.scalpel.contract.task.ColumnDerivation;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsConfiguration;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.DeriveColumnsOperation;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.ProcessorOutput;
import cn.superhuang.data.scalpel.contract.task.RuntimeValueExpression;
import cn.superhuang.data.scalpel.contract.task.LiteralExpression;
import cn.superhuang.data.scalpel.contract.task.CanvasRuntimeValue;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeriveColumnsRuntimeValueSparkTest {
    private static final UUID EXECUTION_ID = UUID.fromString("cda21f5a-7e46-4dd2-9ad8-8602582ab04b");
    private static final Instant STARTED_AT = Instant.parse("2026-08-20T01:02:03Z");
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("canvas-derive-runtime-values-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void appliesAttemptScopedValuesAsNonNullSparkLiterals() {
        List<CanvasColumnSchema> columns = List.of(new CanvasColumnSchema(
                "id", PlatformDataType.LONG, null, null, null, false,
                null, false, false, null));
        Dataset<Row> dataset = spark.createDataFrame(
                List.of(RowFactory.create(1L), RowFactory.create(2L)),
                SparkTypeMapper.toStructType(columns));
        SparkCanvasTable source = new SparkCanvasTable(
                new CanvasTableSchema("source", null, columns, CanvasDatasetKind.BOUNDED, null, null),
                dataset);
        DeriveColumnsNodeDefinition node = new DeriveColumnsNodeDefinition(
                UUID.randomUUID().toString(), "派生运行时字段", new CanvasNodeLayout(0D, 0D, 240D, 120D),
                new DeriveColumnsConfiguration(List.of(), List.of(new DeriveColumnsOperation(
                        UUID.randomUUID().toString(), "source", new ProcessorOutput.CreateNewTable("derived"),
                        List.of(
                                new ColumnDerivation("etl_batch_id", new RuntimeValueExpression(
                                        CanvasRuntimeValue.EXECUTION_ID)),
                                new ColumnDerivation("etl_loaded_at", new RuntimeValueExpression(
                                        CanvasRuntimeValue.EXECUTION_STARTED_AT))
                        )))));
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new DeriveColumnsNodeOperator().apply(
                node,
                Map.of("source", source),
                new CanvasNodeOperationContext(
                        spark,
                        MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                        issues,
                        new SchemaOnlyCanvasNodeDataAccess(spark),
                        cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode.BATCH,
                        CanvasRuntimeValues.execution(EXECUTION_ID, STARTED_AT)
                ));

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        SparkCanvasTable derived = result.propagatedTables().get("derived");
        assertEquals(PlatformDataType.STRING, column(derived, "etl_batch_id").fieldType());
        assertFalse(column(derived, "etl_batch_id").nullable());
        assertEquals(PlatformDataType.TIMESTAMP, column(derived, "etl_loaded_at").fieldType());
        assertFalse(column(derived, "etl_loaded_at").nullable());
        for (Row row : derived.dataset().collectAsList()) {
            assertEquals(EXECUTION_ID.toString(), row.getAs("etl_batch_id"));
            assertEquals(Timestamp.from(STARTED_AT), row.getAs("etl_loaded_at"));
        }
    }

    @Test
    void automaticallyReplacesExistingFieldsAndAppendsMissingFieldsPerTable() {
        List<CanvasColumnSchema> ordersColumns = List.of(
                new CanvasColumnSchema("id", PlatformDataType.LONG, null, null, null, false,
                        null, false, false, null),
                new CanvasColumnSchema("source_record_hash", PlatformDataType.STRING, null, null,
                        null, true, null, false, false, null)
        );
        List<CanvasColumnSchema> customersColumns = List.of(new CanvasColumnSchema(
                "id", PlatformDataType.LONG, null, null, null, false,
                null, false, false, null));
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put("orders", table("orders", ordersColumns, List.of(RowFactory.create(1L, "old"))));
        inputs.put("customers", table("customers", customersColumns, List.of(RowFactory.create(2L))));

        DeriveColumnsNodeDefinition node = new DeriveColumnsNodeDefinition(
                UUID.randomUUID().toString(), "自动派生字段", new CanvasNodeLayout(0D, 0D, 240D, 120D),
                new DeriveColumnsConfiguration(
                        List.of(new ColumnDerivation(
                                "source_record_hash",
                                new LiteralExpression(new CanvasLiteral(PlatformDataType.STRING, "generated"))
                        )),
                        List.of(
                                new DeriveColumnsOperation(
                                        UUID.randomUUID().toString(), "orders",
                                        new ProcessorOutput.ReplaceSource(null),
                                        List.of(new ColumnDerivation("id", new ColumnExpression("id")))
                                ),
                                new DeriveColumnsOperation(
                                        UUID.randomUUID().toString(), "customers",
                                        new ProcessorOutput.ReplaceSource(null),
                                        List.of()
                                )
                        )
                )
        );
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = new DeriveColumnsNodeOperator().apply(
                node,
                inputs,
                new CanvasNodeOperationContext(
                        spark,
                        MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                        issues,
                        new SchemaOnlyCanvasNodeDataAccess(spark),
                        cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode.BATCH,
                        CanvasRuntimeValues.forPreview()
                )
        );

        assertFalse(issues.hasErrors(), () -> issues.codes.toString());
        SparkCanvasTable orders = result.propagatedTables().get("orders");
        SparkCanvasTable customers = result.propagatedTables().get("customers");
        assertEquals(List.of("id", "source_record_hash"),
                orders.schema().columns().stream().map(CanvasColumnSchema::name).toList());
        assertEquals(List.of("id", "source_record_hash"),
                customers.schema().columns().stream().map(CanvasColumnSchema::name).toList());
        assertEquals("generated", orders.dataset().head().getAs("source_record_hash"));
        assertEquals("generated", customers.dataset().head().getAs("source_record_hash"));
        assertTrue(result.propagatedTables().containsKey("orders"));
        assertTrue(result.propagatedTables().containsKey("customers"));
    }

    private SparkCanvasTable table(
            String name,
            List<CanvasColumnSchema> columns,
            List<Row> rows
    ) {
        return new SparkCanvasTable(
                new CanvasTableSchema(name, null, columns, CanvasDatasetKind.BOUNDED, null, null),
                spark.createDataFrame(rows, SparkTypeMapper.toStructType(columns))
        );
    }

    private static CanvasColumnSchema column(SparkCanvasTable table, String name) {
        return table.schema().columns().stream()
                .filter(column -> column.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static final class RecordingIssueSink implements CanvasNodeIssueSink {
        private final List<String> codes = new java.util.ArrayList<>();

        @Override
        public void error(String code, String message, String path) {
            codes.add(code);
        }

        @Override
        public void warning(String code, String message, String path) {
        }

        @Override
        public boolean hasErrors() {
            return !codes.isEmpty();
        }
    }
}
