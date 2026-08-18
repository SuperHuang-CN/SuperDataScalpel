package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.JdbcTableIdentifier;
import cn.superhuang.datascalpel.sdk.JdbcWriteMode;
import cn.superhuang.datascalpel.sdk.ModelWriteMode;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SparkJobTestContextTest {
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
}
