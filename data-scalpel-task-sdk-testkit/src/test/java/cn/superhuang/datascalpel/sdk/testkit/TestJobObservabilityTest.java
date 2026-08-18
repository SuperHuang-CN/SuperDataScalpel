package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.JobOperation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestJobObservabilityTest {

    @Test
    void capturesEventsStatusMetricsAndNestedOperations() throws Exception {
        try (SparkJobTestContext context = SparkJobTestContext.builder().build()) {
            context.observability().info("orders.started", "started", Map.of("bizDate", "2026-08-14"));
            context.observability().info("orders.localized", "started", Map.of("业务日期", "2026-08-14"));
            context.observability().status("TRANSFORM", "joining dimensions");
            context.observability().setGauge("orders.lag", 1.5);

            try (var executor = Executors.newFixedThreadPool(4)) {
                for (int index = 0; index < 100; index++) {
                    executor.submit(() -> context.observability().addCounter("orders.rows", 1));
                }
                executor.shutdown();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }

            String previous = context.spark().sparkContext().getLocalProperty("spark.job.description");
            try (JobOperation outer = context.observability().operation("orders.outer")) {
                assertEquals("orders.outer", context.spark().sparkContext()
                        .getLocalProperty("spark.job.description"));
                try (JobOperation inner = context.observability().operation("orders.inner")) {
                    assertEquals("orders.inner", context.spark().sparkContext()
                            .getLocalProperty("spark.job.description"));
                }
                assertEquals("orders.outer", context.spark().sparkContext()
                        .getLocalProperty("spark.job.description"));
            }
            assertEquals(previous, context.spark().sparkContext().getLocalProperty("spark.job.description"));

            assertEquals("TRANSFORM", context.latestStatus().orElseThrow().phase());
            context.assertCounter("orders.rows", 100);
            context.assertGauge("orders.lag", 1.5);
            context.assertTimerRecorded("orders.outer");
            context.assertTimerRecorded("orders.inner");
            assertTrue(context.observabilityEvents().stream()
                    .anyMatch(event -> event.eventName().equals("orders.started")));

            assertThrows(IllegalArgumentException.class,
                    () -> context.observability().addCounter("orders.rows", -1));
            assertThrows(IllegalArgumentException.class,
                    () -> context.observability().setGauge("orders.rows", 1));
            assertThrows(IllegalArgumentException.class,
                    () -> context.observability().addCounter("datascalpel.custom", 1));
            assertThrows(IllegalArgumentException.class,
                    () -> context.observability().setGauge("orders.invalid", Double.NaN));
        }
    }

    @Test
    void rejectsMoreThanOneHundredUserMetricsAndCrossThreadClose() throws Exception {
        try (SparkJobTestContext context = SparkJobTestContext.builder().build()) {
            for (int index = 0; index < 99; index++) {
                context.observability().addCounter("metric." + index, 1);
            }
            JobOperation operation = context.observability().operation("operation.thread_bound");
            assertThrows(IllegalArgumentException.class,
                    () -> context.observability().addCounter("metric.overflow", 1));
            try (var executor = Executors.newSingleThreadExecutor()) {
                var failure = executor.submit(() -> assertThrows(
                        IllegalStateException.class, operation::close));
                failure.get(10, TimeUnit.SECONDS);
            }
            operation.close();
            context.assertTimerRecorded("operation.thread_bound");
        }
    }
}
