package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.MetadataSnapshot;
import cn.superhuang.data.scalpel.contract.task.SqlTransformConfiguration;
import cn.superhuang.data.scalpel.contract.task.SqlTransformNodeDefinition;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlTransformNodeOperatorSparkTest {
    private static final CanvasNodeLayout LAYOUT = new CanvasNodeLayout(0d, 0d, 240d, 120d);

    private final SqlTransformNodeOperator operator = new SqlTransformNodeOperator();
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("canvas-sql-transform-operator-test")
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
    void executesCteAgainstSpecialCharacterTableCodeWithoutLeakingViewsAcrossInvocations() {
        SparkCanvasTable orders = table(
                "order.items",
                List.of(
                        column("order_id", PlatformDataType.LONG, false, "订单 ID"),
                        column("amount", PlatformDataType.INTEGER, false, "金额")
                ),
                List.of(RowFactory.create(1L, 10), RowFactory.create(2L, 20))
        );
        Map<String, SparkCanvasTable> inputs = new LinkedHashMap<>();
        inputs.put(orders.name(), orders);
        String sql = """
                WITH base AS (
                  SELECT `order_id`, `amount` FROM `order.items`
                )
                SELECT `order_id`, `amount` + 1 AS `amount_plus_one` FROM base
                """;

        RecordingIssueSink firstIssues = new RecordingIssueSink();
        CanvasNodeOperationResult first = apply(inputs, "enriched_orders", sql, firstIssues);
        RecordingIssueSink secondIssues = new RecordingIssueSink();
        CanvasNodeOperationResult second = apply(inputs, "enriched_orders_again", sql, secondIssues);

        assertFalse(firstIssues.hasErrors(), () -> firstIssues.codes.toString());
        assertFalse(secondIssues.hasErrors(), () -> secondIssues.codes.toString());
        assertEquals(List.of("order.items", "enriched_orders"),
                List.copyOf(first.propagatedTables().keySet()));
        assertEquals(List.of("order_id", "amount_plus_one"), first.propagatedTables()
                .get("enriched_orders").schema().columns().stream().map(CanvasColumnSchema::name).toList());
        assertEquals(List.of(11, 21), first.propagatedTables().get("enriched_orders").dataset()
                .orderBy("order_id").collectAsList().stream()
                .map(row -> row.<Integer>getAs("amount_plus_one")).toList());
        assertEquals(List.of(11, 21), second.propagatedTables().get("enriched_orders_again").dataset()
                .orderBy("order_id").collectAsList().stream()
                .map(row -> row.<Integer>getAs("amount_plus_one")).toList());
        assertSame(orders, first.propagatedTables().get("order.items"));
        assertEquals(List.of("order.items"), List.copyOf(inputs.keySet()));
        assertFalse(spark.catalog().tableExists("order.items"),
                "a SQL node must not register its views in the parent session");
    }

    @Test
    void rejectsWritesExternalRelationsAndTableValuedFunctionsBeforeAnalysis() {
        SparkCanvasTable orders = table(
                "orders",
                List.of(column("order_id", PlatformDataType.LONG, false, null)),
                List.of(RowFactory.create(1L))
        );
        Map<String, SparkCanvasTable> inputs = Map.of("orders", orders);

        assertRejected(inputs, "DELETE FROM `orders`", "SQL_TRANSFORM_STATEMENT_NOT_ALLOWED");
        assertRejected(inputs, "SELECT * FROM external_catalog.public.orders", "SQL_TRANSFORM_RELATION_NOT_ALLOWED");
        assertRejected(inputs, "SELECT * FROM range(2)", "SQL_TRANSFORM_RELATION_NOT_ALLOWED");
    }

    private void assertRejected(Map<String, SparkCanvasTable> inputs, String sql, String expectedCode) {
        RecordingIssueSink issues = new RecordingIssueSink();
        CanvasNodeOperationResult result = apply(inputs, "result", sql, issues);
        assertTrue(issues.codes.contains(expectedCode), () -> issues.codes.toString());
        assertTrue(result.propagatedTables().isEmpty());
    }

    private CanvasNodeOperationResult apply(
            Map<String, SparkCanvasTable> inputs,
            String outputTableName,
            String sql,
            RecordingIssueSink issues
    ) {
        SqlTransformNodeDefinition definition = new SqlTransformNodeDefinition(
                UUID.randomUUID().toString(),
                "SQL 处理",
                LAYOUT,
                new SqlTransformConfiguration(outputTableName, sql)
        );
        return operator.apply(
                definition,
                inputs,
                new CanvasNodeOperationContext(
                        spark,
                        MetadataIndex.create(new MetadataSnapshot(List.of(), List.of())),
                        issues,
                        new SchemaOnlyCanvasNodeDataAccess(spark)
                )
        );
    }

    private SparkCanvasTable table(
            String name,
            List<CanvasColumnSchema> columns,
            List<Row> rows
    ) {
        Dataset<Row> dataset = spark.createDataFrame(rows, SparkTypeMapper.toStructType(columns));
        return new SparkCanvasTable(
                new CanvasTableSchema(name, null, columns, CanvasDatasetKind.BOUNDED, null, null),
                dataset
        );
    }

    private static CanvasColumnSchema column(
            String name,
            PlatformDataType type,
            boolean nullable,
            String comment
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
                comment
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
