package cn.superhuang.datascalpel.taskengine.runner;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.execution.QueryExecution;
import org.apache.spark.sql.util.QueryExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;
import scala.collection.Iterator;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.lit;

/** Collects per-output row counts from the same Spark SQL action that performs the write. */
final class SparkOutputMetricsCollector implements QueryExecutionListener, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SparkOutputMetricsCollector.class);
    private static final String METRIC_PREFIX = "datascalpel.write.";
    private static final String ROWS_WRITTEN = "rowsWritten";
    private static final Duration LISTENER_WAIT = Duration.ofSeconds(5);

    private final SparkSession spark;
    private final Duration listenerWait;
    private final Map<String, PendingMetric> pending = new ConcurrentHashMap<>();

    SparkOutputMetricsCollector(SparkSession spark) {
        this(spark, LISTENER_WAIT);
    }

    SparkOutputMetricsCollector(SparkSession spark, Duration listenerWait) {
        if (spark == null) throw new IllegalArgumentException("SparkSession 不能为空");
        if (listenerWait == null || listenerWait.isZero() || listenerWait.isNegative()) {
            throw new IllegalArgumentException("Spark 输出指标等待时间必须大于 0");
        }
        this.spark = spark;
        this.listenerWait = listenerWait;
        spark.listenerManager().register(this);
    }

    ObservedOutput observe(
            UUID executionId,
            int attempt,
            String nodeId,
            Dataset<Row> dataset
    ) {
        if (executionId == null || attempt < 1 || nodeId == null || nodeId.isBlank() || dataset == null) {
            throw new IllegalArgumentException("Spark 输出指标上下文无效");
        }
        String metricName = METRIC_PREFIX + executionId + "." + attempt + "." + nodeId + "." + UUID.randomUUID();
        PendingMetric metric = new PendingMetric(Instant.now(), new CompletableFuture<>());
        if (pending.putIfAbsent(metricName, metric) != null) {
            throw new IllegalStateException("Spark 输出指标名称冲突");
        }
        Dataset<Row> observed = dataset.observe(metricName, count(lit(1)).alias(ROWS_WRITTEN));
        return new ObservedOutput(metricName, nodeId, observed);
    }

    OutputWriteMetrics completeSuccess(ObservedOutput output) {
        PendingMetric metric = find(output);
        if (metric == null) return OutputWriteMetrics.unavailable(0L);
        try {
            ListenerMetric listenerMetric = metric.result().get(listenerWait.toMillis(), TimeUnit.MILLISECONDS);
            if (listenerMetric.rowsWritten() == null) {
                log.warn("Spark JDBC output metric unavailable for node {}", output.nodeId());
                return OutputWriteMetrics.unavailable(listenerMetric.durationMs());
            }
            return OutputWriteMetrics.available(listenerMetric.rowsWritten(), listenerMetric.durationMs());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for Spark JDBC output metric for node {}", output.nodeId());
        } catch (TimeoutException exception) {
            log.warn("Timed out waiting for Spark JDBC output metric for node {}", output.nodeId());
        } catch (Exception exception) {
            log.warn("Could not collect Spark JDBC output metric for node {}", output.nodeId());
        } finally {
            pending.remove(output.metricName(), metric);
        }
        return OutputWriteMetrics.unavailable(Duration.between(metric.startedAt(), Instant.now()).toMillis());
    }

    void completeFailure(ObservedOutput output) {
        PendingMetric metric = remove(output);
        if (metric != null) metric.result().complete(ListenerMetric.unavailable(0L));
    }

    @Override
    public void onSuccess(String functionName, QueryExecution queryExecution, long durationNanos) {
        Iterator<Tuple2<String, Row>> metrics = queryExecution.observedMetrics().iterator();
        while (metrics.hasNext()) {
            Tuple2<String, Row> entry = metrics.next();
            PendingMetric metric = pending.get(entry._1());
            if (metric == null) continue;
            metric.result().complete(new ListenerMetric(
                    rowsWritten(entry._2()), TimeUnit.NANOSECONDS.toMillis(durationNanos)));
        }
    }

    @Override
    public void onFailure(String functionName, QueryExecution queryExecution, Exception exception) {
        Iterator<Tuple2<String, Row>> metrics = queryExecution.observedMetrics().iterator();
        while (metrics.hasNext()) {
            Tuple2<String, Row> entry = metrics.next();
            PendingMetric metric = pending.get(entry._1());
            if (metric != null) metric.result().complete(ListenerMetric.unavailable(0L));
        }
    }

    @Override
    public void close() {
        try {
            spark.listenerManager().unregister(this);
        } finally {
            pending.values().forEach(metric -> metric.result().complete(ListenerMetric.unavailable(0L)));
            pending.clear();
        }
    }

    int pendingMetricCount() {
        return pending.size();
    }

    private PendingMetric remove(ObservedOutput output) {
        if (output == null) return null;
        return pending.remove(output.metricName());
    }

    private PendingMetric find(ObservedOutput output) {
        if (output == null) return null;
        return pending.get(output.metricName());
    }

    private static Long rowsWritten(Row metrics) {
        if (metrics == null) return null;
        Object value;
        try {
            value = metrics.getAs(ROWS_WRITTEN);
        } catch (RuntimeException exception) {
            return null;
        }
        if (!(value instanceof Number number)) return null;
        long rows = number.longValue();
        return rows < 0 ? null : rows;
    }

    record ObservedOutput(String metricName, String nodeId, Dataset<Row> dataset) {
    }

    record OutputWriteMetrics(Long rowsWritten, long durationMs, boolean metricAvailable) {
        private static OutputWriteMetrics available(long rowsWritten, long durationMs) {
            return new OutputWriteMetrics(rowsWritten, Math.max(0L, durationMs), true);
        }

        private static OutputWriteMetrics unavailable(long durationMs) {
            return new OutputWriteMetrics(null, Math.max(0L, durationMs), false);
        }
    }

    private record PendingMetric(
            Instant startedAt,
            CompletableFuture<ListenerMetric> result
    ) {
    }

    private record ListenerMetric(Long rowsWritten, long durationMs) {
        private static ListenerMetric unavailable(long durationMs) {
            return new ListenerMetric(null, Math.max(0L, durationMs));
        }
    }
}
