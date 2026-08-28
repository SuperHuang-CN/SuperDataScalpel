package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import cn.superhuang.datascalpel.sdk.JdbcTableIdentifier;
import org.apache.spark.scheduler.SparkListener;
import org.apache.spark.scheduler.SparkListenerJobStart;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.upper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SparkJarLineageRuntimeTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("spark-jar-lineage-runtime-test")
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
    void analyzesJdbcProjectionAndUsageWithoutStartingSparkAction() {
        AtomicInteger jobsStarted = new AtomicInteger();
        spark.sparkContext().addSparkListener(new SparkListener() {
            @Override
            public void onJobStart(SparkListenerJobStart jobStart) {
                jobsStarted.incrementAndGet();
            }
        });
        UUID dataSourceId = UUID.randomUUID();
        SparkJarLineageRuntime runtime = new SparkJarLineageRuntime(true);
        Dataset<Row> input = runtime.jdbcTableInput(
                "source",
                dataSourceId,
                JdbcTableIdentifier.schemaTable("public", "customers"),
                spark.sql("select cast(1 as bigint) id, 'Alice' name"));
        Dataset<Row> projected = input
                .filter(col("id").gt(0))
                .select(col("id").as("customer_id"), upper(col("name")).as("name_upper"));

        SparkJarLineageRuntime.PreparedFlow prepared = runtime.analyzeJdbcWrite(
                "target", dataSourceId,
                JdbcTableIdentifier.schemaTable("report", "customers"),
                "APPEND", projected);
        runtime.confirm(prepared);
        TaskLineageEvidence evidence = runtime.evidence(true);

        assertEquals(TaskLineageEvidence.AnalysisStatus.COMPLETE, evidence.analysisStatus());
        assertEquals(1, evidence.flows().size());
        TaskLineageEvidence.Flow flow = evidence.flows().getFirst();
        assertEquals("customers", flow.inputAssets().getFirst().physicalTableName());
        assertEquals("customers", flow.outputAsset().physicalTableName());
        assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                edge.derivationType() == TaskLineageEvidence.DerivationType.DIRECT));
        assertTrue(flow.fieldEdges().stream().anyMatch(edge ->
                edge.derivationType() == TaskLineageEvidence.DerivationType.CALCULATED));
        assertTrue(flow.fieldUsages().stream().anyMatch(usage ->
                usage.usageType() == TaskLineageEvidence.UsageType.FILTER_CONDITION
                        && usage.nodeKey().startsWith("jar:output:")));
        assertEquals(0, jobsStarted.get());
    }

    @Test
    void usesStableOutputOperationKeyForJarJoinUsages() {
        UUID dataSourceId = UUID.randomUUID();
        SparkJarLineageRuntime runtime = new SparkJarLineageRuntime(true);
        Dataset<Row> customers = runtime.jdbcTableInput(
                "customers", dataSourceId,
                JdbcTableIdentifier.schemaTable("public", "customers"),
                spark.sql("select cast(1 as bigint) id, 'Alice' name")).alias("customers");
        Dataset<Row> orders = runtime.jdbcTableInput(
                "orders", dataSourceId,
                JdbcTableIdentifier.schemaTable("public", "orders"),
                spark.sql("select cast(10 as bigint) order_id, cast(1 as bigint) customer_id"))
                .alias("orders");
        Dataset<Row> projected = customers.join(orders,
                        col("customers.id").equalTo(col("orders.customer_id")))
                .select(col("customers.id").as("customer_id"), col("orders.order_id"));

        SparkJarLineageRuntime.PreparedFlow prepared = runtime.analyzeJdbcWrite(
                "target", dataSourceId,
                JdbcTableIdentifier.schemaTable("report", "customer_orders"),
                "APPEND", projected);

        var joinUsages = prepared.flow().fieldUsages().stream()
                .filter(usage -> usage.usageType() == TaskLineageEvidence.UsageType.JOIN_KEY)
                .toList();
        assertEquals(2, joinUsages.size());
        assertTrue(joinUsages.stream().allMatch(usage ->
                usage.nodeKey() != null && usage.nodeKey().startsWith("jar:output:")));
    }

    @Test
    void queryEvidenceStoresOnlyHashAndSafeDisplayName() {
        String sql = "select * from secret_table where token = 'must-not-leak'";
        UUID dataSourceId = UUID.randomUUID();
        SparkJarLineageRuntime runtime = new SparkJarLineageRuntime(true);
        Dataset<Row> query = runtime.jdbcQueryInput(
                "querySource", dataSourceId, sql,
                spark.sql("select cast(1 as bigint) id"));
        SparkJarLineageRuntime.PreparedFlow prepared = runtime.analyzeJdbcWrite(
                "target", dataSourceId,
                JdbcTableIdentifier.schemaTable("report", "query_result"),
                "APPEND", query);
        runtime.confirm(prepared);

        String evidence = runtime.evidence(true).toString();
        assertTrue(evidence.contains("JDBC_QUERY_RESULT"));
        assertTrue(evidence.contains("querySource 查询结果"));
        assertFalse(evidence.contains("secret_table"));
        assertFalse(evidence.contains("must-not-leak"));
    }
}
