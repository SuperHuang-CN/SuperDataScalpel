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
    void rowNumberResolvesPartitionAndOrderDependenciesWithoutActions() {
        UUID dataSourceId = UUID.randomUUID();
        SparkJarLineageRuntime runtime = new SparkJarLineageRuntime(true);
        Dataset<Row> source = runtime.jdbcTableInput("source", dataSourceId,
                JdbcTableIdentifier.schemaTable("public", "scores"),
                spark.sql("select 'A' category, cast(10 as bigint) score"));
        Dataset<Row> ranked = source.withColumn("position", org.apache.spark.sql.functions.row_number()
                .over(org.apache.spark.sql.expressions.Window.partitionBy(col("category")).orderBy(col("score").desc())))
                .filter(col("position").equalTo(1)).select("position");
        var flow = runtime.analyzeJdbcWrite("target", dataSourceId,
                JdbcTableIdentifier.schemaTable("report", "positions"), "APPEND", ranked).flow();

        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE, flow.coverage());
        assertEquals(2, flow.fieldEdges().size());
        assertTrue(flow.fieldEdges().stream().allMatch(edge ->
                edge.derivationType() == TaskLineageEvidence.DerivationType.CALCULATED));
        assertTrue(flow.fieldUsages().stream().anyMatch(usage -> usage.usageType() == TaskLineageEvidence.UsageType.PARTITION_KEY));
        assertTrue(flow.fieldUsages().stream().anyMatch(usage -> usage.usageType() == TaskLineageEvidence.UsageType.SORT_KEY));
    }

    @Test void arraySortAndTransformBindOnlyTheirOwnElementsAndPreserveUnknownSources() {
        UUID dataSourceId = UUID.randomUUID();
        SparkJarLineageRuntime runtime = new SparkJarLineageRuntime(true);
        var input = runtime.jdbcTableInput("source",dataSourceId,JdbcTableIdentifier.schemaTable("public","items"),
                spark.sql("select cast(1 as bigint) id, 'A' label"));
        var arrays = input.select(org.apache.spark.sql.functions.expr("array(named_struct('id', id, 'label', label)) as items"));
        var mapped = arrays.select(org.apache.spark.sql.functions.expr(
                "transform(array_sort(items, (a,b) -> case when a.id < b.id then -1 when a.id > b.id then 1 else 0 end), (item,i) -> concat(item.label, cast(i as string))) as labels"));
        var flow = runtime.analyzeJdbcWrite("target",dataSourceId,JdbcTableIdentifier.table("labels"),"APPEND",mapped).flow();
        assertEquals(TaskLineageEvidence.Coverage.FIELD_COMPLETE,flow.coverage());
        assertEquals(2,flow.fieldEdges().size());
        var withUnknown = arrays.crossJoin(spark.range(1).select(col("id").as("unknown_value")));
        var unknown = withUnknown.select(org.apache.spark.sql.functions.expr("transform(items, item -> item.id + unknown_value) as mixed"));
        assertEquals(TaskLineageEvidence.Coverage.FIELD_PARTIAL,runtime.analyzeJdbcWrite("target2",dataSourceId,
                JdbcTableIdentifier.table("mixed"),"APPEND",unknown).flow().coverage());
    }

    @Test
    void rowNumberWithUnknownOrConstantOrderingDoesNotClaimCompleteLineage() {
        UUID dataSourceId = UUID.randomUUID();
        SparkJarLineageRuntime runtime = new SparkJarLineageRuntime(true);
        Dataset<Row> source = runtime.jdbcTableInput("source", dataSourceId,
                JdbcTableIdentifier.schemaTable("public", "scores"), spark.sql("select 'A' category"));
        Dataset<Row> unknown = spark.range(1).select(col("id").as("unknown_score"));
        Dataset<Row> combined = source.crossJoin(unknown);
        for (var window : java.util.List.of(
                org.apache.spark.sql.expressions.Window.partitionBy(col("category")).orderBy(col("unknown_score")),
                org.apache.spark.sql.expressions.Window.orderBy(org.apache.spark.sql.functions.lit(1)))) {
            Dataset<Row> ranked = combined.select(org.apache.spark.sql.functions.row_number().over(window).as("position"));
            var flow = runtime.analyzeJdbcWrite("target", dataSourceId,
                    JdbcTableIdentifier.schemaTable("report", "positions"), "APPEND", ranked).flow();
            assertEquals(TaskLineageEvidence.Coverage.FIELD_PARTIAL, flow.coverage());
            assertTrue(flow.fieldEdges().isEmpty());
        }
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
