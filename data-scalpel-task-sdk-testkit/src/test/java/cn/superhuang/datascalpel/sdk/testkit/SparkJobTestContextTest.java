package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.JdbcTableIdentifier;
import cn.superhuang.datascalpel.sdk.JdbcReadOptions;
import cn.superhuang.datascalpel.sdk.JdbcWriteMode;
import cn.superhuang.datascalpel.sdk.ModelWriteMode;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkJobTestContextTest {
    @Test
    void mapsSameNamedColumnsAndChecksSchemaWithoutRequiringOrderOrNullability() {
        StructType sourceSchema = new StructType()
                .add("amount", DataTypes.createDecimalType(10, 2), true)
                .add("id", DataTypes.StringType, true);
        StructType targetSchema = new StructType()
                .add("id", DataTypes.StringType, false)
                .add("amount", DataTypes.createDecimalType(10, 2), false);

        try (SparkJobTestContext context = SparkJobTestContext.builder()
                .modelInput("source", sourceSchema, List.of(RowFactory.create(new BigDecimal("12.50"), "order-1")))
                .modelOutput("target", TestModelTarget.builder(targetSchema).build())
                .build()) {
            context.models().write("target", context.models().read("source"))
                    .mapSameName()
                    .checkSchema()
                    .execute();

            CapturedModelWrite write = context.modelWrites("target").getFirst();
            assertEquals("amount", write.columnMappings().get("amount"));
            assertEquals("id", write.columnMappings().get("id"));
            assertEquals(new BigDecimal("12.50"), write.rows().getFirst().getDecimal(0));
        }
    }

    @Test
    void reportsMissingAndUnexpectedColumnsFromLocalSchemaCheck() {
        StructType sourceSchema = new StructType()
                .add("id", DataTypes.StringType, true)
                .add("temporary", DataTypes.StringType, true);
        StructType targetSchema = new StructType()
                .add("id", DataTypes.StringType, false)
                .add("amount", DataTypes.createDecimalType(10, 2), true);

        try (SparkJobTestContext context = SparkJobTestContext.builder()
                .modelInput("source", sourceSchema, List.of(RowFactory.create("order-1", "debug")))
                .modelOutput("target", TestModelTarget.builder(targetSchema).build())
                .build()) {
            SparkJobTestException exception = assertThrows(SparkJobTestException.class, () ->
                    context.models().write("target", context.models().read("source")).checkSchema());
            assertEquals("TESTKIT_MODEL_SCHEMA_MISMATCH", exception.code());
            assertTrue(exception.getMessage().contains("missing target columns: [amount]"));
            assertTrue(exception.getMessage().contains("unexpected source columns: [temporary]"));
        }
    }

    @Test
    void reportsIncompatibleTypesFromLocalSchemaCheck() {
        StructType sourceSchema = new StructType()
                .add("id", DataTypes.StringType, true)
                .add("amount", DataTypes.StringType, true);
        StructType targetSchema = new StructType()
                .add("id", DataTypes.StringType, false)
                .add("amount", DataTypes.createDecimalType(10, 2), true);

        try (SparkJobTestContext context = SparkJobTestContext.builder()
                .modelInput("source", sourceSchema, List.of(RowFactory.create("order-1", "12.50")))
                .modelOutput("target", TestModelTarget.builder(targetSchema).build())
                .build()) {
            SparkJobTestException exception = assertThrows(SparkJobTestException.class, () ->
                    context.models().write("target", context.models().read("source")).checkSchema());
            assertEquals("TESTKIT_MODEL_SCHEMA_MISMATCH", exception.code());
            assertTrue(exception.getMessage().contains("incompatible columns: [amount"));
        }
    }

    @Test
    void rejectsSameNameMappingAfterExplicitMapping() {
        StructType schema = new StructType().add("id", DataTypes.StringType, false);
        try (SparkJobTestContext context = SparkJobTestContext.builder()
                .modelInput("source", schema, List.of(RowFactory.create("order-1")))
                .modelOutput("target", TestModelTarget.builder(schema).build())
                .build()) {
            SparkJobTestException exception = assertThrows(SparkJobTestException.class, () ->
                    context.models().write("target", context.models().read("source"))
                            .map("id", "id")
                            .mapSameName());
            assertEquals("TESTKIT_SAME_NAME_MAPPING_AFTER_EXPLICIT_MAPPING", exception.code());
        }
    }

    @Test
    void capturesProjectedModelAndJdbcWrites() {
        StructType inputSchema = new StructType()
                .add("orderId", DataTypes.StringType, false)
                .add("amountText", DataTypes.StringType, true);
        StructType targetSchema = new StructType()
                .add("order_id", DataTypes.StringType, false)
                .add("amount", DataTypes.createDecimalType(10, 2), true);
        List<Row> rows = List.of(RowFactory.create("order-1", "12.50"));
        JdbcTableIdentifier targetTable = JdbcTableIdentifier.of(null, "public", "orders");

        try (SparkJobTestContext context = SparkJobTestContext.builder()
                .parameter("bizDate", "2026-08-14")
                .modelInput("source", inputSchema, rows)
                .modelOutput("target", TestModelTarget.builder(targetSchema)
                        .primaryKeyColumns("order_id")
                        .build())
                .jdbcQuery("sourceJdbc", " SELECT orderId, amountText FROM orders ", inputSchema, rows)
                .jdbcOutput("distribution", targetTable, targetSchema)
                .build()) {
            var input = context.models().read("source");
            context.jdbc().readQuery("sourceJdbc", "SELECT orderId, amountText FROM orders");
            context.models().write("target", input)
                    .mode(ModelWriteMode.UPSERT)
                    .map("order_id", "orderId")
                    .map("amount", "amountText")
                    .execute();
            context.jdbc().write("distribution", input)
                    .table(targetTable)
                    .mode(JdbcWriteMode.UPSERT)
                    .upsertKeyColumns("order_id")
                    .map("order_id", "orderId")
                    .map("amount", "amountText")
                    .execute();

            assertEquals("2026-08-14", context.parameters().require("bizDate"));
            assertEquals(2, context.affectedRows());
            assertEquals("order-1", context.modelWrites("target").getFirst().rows().getFirst().getString(0));
            assertEquals("order_id", context.jdbcWrites("distribution").getFirst().upsertKeyColumns().getFirst());
            assertEquals(DataTypes.createDecimalType(10, 2),
                    context.jdbcWrites("distribution").getFirst().schema().apply("amount").dataType());
            assertEquals(List.of("SELECT orderId, amountText FROM orders"), context.jdbcQueryCalls("sourceJdbc"));
        }
    }

    @Test
    void rejectsCaptureBeyondConfiguredLimit() {
        StructType schema = new StructType().add("id", DataTypes.IntegerType, false);
        try (SparkJobTestContext context = SparkJobTestContext.builder()
                .captureRowLimit(1)
                .modelOutput("target", TestModelTarget.builder(schema).build())
                .build()) {
            var source = context.spark().createDataFrame(
                    List.of(RowFactory.create(1), RowFactory.create(2)), schema);
            SparkJobTestException exception = assertThrows(SparkJobTestException.class, () ->
                    context.models().write("target", source).map("id", "id").execute());
            assertEquals("TESTKIT_CAPTURE_LIMIT_EXCEEDED", exception.code());
        }
    }

    @Test
    void returnsMocksAndRecordsJdbcReadOptions() {
        StructType schema = new StructType().add("id", DataTypes.LongType, false);
        JdbcTableIdentifier orders = JdbcTableIdentifier.schemaTable("public", "orders");
        String query = "SELECT id FROM public.orders";
        JdbcReadOptions options = JdbcReadOptions.builder()
                .partitionBy("id", "1", "1000", 16)
                .fetchSize(10_000)
                .queryTimeoutSeconds(60)
                .option("pushDownPredicate", "true")
                .build();

        try (SparkJobTestContext context = SparkJobTestContext.builder()
                .modelInput("source", schema, List.of(RowFactory.create(1L)))
                .jdbcTable("erp", orders, schema, List.of(RowFactory.create(2L)))
                .jdbcQuery("erp", query, schema, List.of(RowFactory.create(3L)))
                .build()) {
            assertEquals(1L, context.models().read("source", options).first().getLong(0));
            assertEquals(2L, context.jdbc().readTable("erp", orders, options).first().getLong(0));
            assertEquals(3L, context.jdbc().readQuery("erp", query, options).first().getLong(0));
            assertEquals(options, context.modelReadCalls("source").getFirst().options());
            assertEquals(orders, context.jdbcTableReadCalls("erp").getFirst().table());
            assertEquals(16, context.jdbcTableReadCalls("erp").getFirst().options()
                    .partitioning().orElseThrow().numPartitions());
            assertEquals(query, context.jdbcQueryReadCalls("erp").getFirst().sql());
            assertEquals(options, context.jdbcQueryReadCalls("erp").getFirst().options());
        }
    }

    @Test
    void rejectsPlatformControlledOrInvalidJdbcReadOptions() {
        assertThrows(IllegalArgumentException.class, () -> JdbcReadOptions.builder().option("url", "jdbc:test"));
        assertThrows(IllegalArgumentException.class, () -> JdbcReadOptions.builder()
                .partitionBy("id", "1", "100", 257));
        assertThrows(IllegalArgumentException.class, () -> JdbcReadOptions.builder()
                .option("pushDownPredicate", "yes"));
    }
}
