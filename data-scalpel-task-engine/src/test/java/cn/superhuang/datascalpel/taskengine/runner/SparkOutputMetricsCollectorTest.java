package cn.superhuang.datascalpel.taskengine.runner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SparkOutputMetricsCollectorTest {
    private SparkSession spark;

    @BeforeAll
    void startSpark() {
        spark = SparkSession.builder()
                .master("local[1]")
                .appName("spark-output-metrics-collector-test")
                .config("spark.ui.enabled", "false")
                .config("spark.driver.host", "127.0.0.1")
                .config("spark.driver.bindAddress", "127.0.0.1")
                .getOrCreate();
    }

    @AfterAll
    void stopSpark() {
        if (spark != null) spark.stop();
    }

    @Test
    void collectsRowsFromTheSameActionAndRemovesPendingMetric() {
        try (SparkOutputMetricsCollector collector = new SparkOutputMetricsCollector(spark)) {
            Dataset<Row> dataset = spark.range(0, 5).toDF();
            SparkOutputMetricsCollector.ObservedOutput observed = collector.observe(
                    UUID.randomUUID(), 1, UUID.randomUUID().toString(), dataset);

            assertEquals(5, observed.dataset().collectAsList().size());
            SparkOutputMetricsCollector.OutputWriteMetrics metrics = collector.completeSuccess(observed);

            assertTrue(metrics.metricAvailable());
            assertEquals(5L, metrics.rowsWritten());
            assertTrue(metrics.durationMs() >= 0);
            assertEquals(0, collector.pendingMetricCount());
        }
    }

    @Test
    void reportsZeroForAnEmptyDataset() {
        try (SparkOutputMetricsCollector collector = new SparkOutputMetricsCollector(spark)) {
            SparkOutputMetricsCollector.ObservedOutput observed = collector.observe(
                    UUID.randomUUID(), 1, UUID.randomUUID().toString(), spark.range(0).toDF());

            assertTrue(observed.dataset().collectAsList().isEmpty());
            SparkOutputMetricsCollector.OutputWriteMetrics metrics = collector.completeSuccess(observed);

            assertTrue(metrics.metricAvailable());
            assertEquals(0L, metrics.rowsWritten());
        }
    }

    @Test
    void discardsPendingMetricWhenWriteFails() {
        try (SparkOutputMetricsCollector collector = new SparkOutputMetricsCollector(spark)) {
            SparkOutputMetricsCollector.ObservedOutput observed = collector.observe(
                    UUID.randomUUID(), 1, UUID.randomUUID().toString(), spark.range(1).toDF());

            collector.completeFailure(observed);

            assertEquals(0, collector.pendingMetricCount());
        }
    }

    @Test
    void keepsMetricsIsolatedAndDegradesToUnknownAfterListenerTimeout() {
        try (SparkOutputMetricsCollector collector = new SparkOutputMetricsCollector(
                spark, Duration.ofMillis(25))) {
            UUID executionId = UUID.randomUUID();
            String nodeId = UUID.randomUUID().toString();
            SparkOutputMetricsCollector.ObservedOutput first = collector.observe(
                    executionId, 1, nodeId, spark.range(1).toDF());
            SparkOutputMetricsCollector.ObservedOutput second = collector.observe(
                    executionId, 1, nodeId, spark.range(1).toDF());

            assertNotEquals(first.metricName(), second.metricName());
            spark.range(1).collectAsList();

            SparkOutputMetricsCollector.OutputWriteMetrics metrics = collector.completeSuccess(first);
            assertFalse(metrics.metricAvailable());
            assertNull(metrics.rowsWritten());
            assertEquals(1, collector.pendingMetricCount());
            collector.completeFailure(second);
            assertEquals(0, collector.pendingMetricCount());
        }
    }

    @Test
    void closeCompletesAndClearsPendingMetrics() {
        SparkOutputMetricsCollector collector = new SparkOutputMetricsCollector(
                spark, Duration.ofMillis(25));
        SparkOutputMetricsCollector.ObservedOutput observed = collector.observe(
                UUID.randomUUID(), 1, UUID.randomUUID().toString(), spark.range(1).toDF());

        collector.close();

        assertEquals(0, collector.pendingMetricCount());
        SparkOutputMetricsCollector.OutputWriteMetrics metrics = collector.completeSuccess(observed);
        assertFalse(metrics.metricAvailable());
        assertNull(metrics.rowsWritten());
    }
}
