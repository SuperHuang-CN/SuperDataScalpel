package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.SparkStreamingJob;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SparkStreamingJobTestRun implements AutoCloseable {
    private final SparkStreamingJob job;
    private final SparkStreamingJobTestKit.TestStreamingContext context;
    private final Duration processingTimeout;
    private final AtomicBoolean stopped = new AtomicBoolean();

    SparkStreamingJobTestRun(
            SparkStreamingJob job,
            SparkStreamingJobTestKit.TestStreamingContext context,
            Duration processingTimeout
    ) {
        this.job = job;
        this.context = context;
        this.processingTimeout = processingTimeout;
    }

    public SparkJobTestContext context() { return context; }
    public List<String> queryNames() { return context.managedQueries().queryNames(); }
    public boolean isStopped() { return stopped.get(); }

    public List<TestKafkaRecord> kafkaOutputRecords(String bindingName) {
        return context.kafkaOutputRecords(bindingName);
    }

    public List<TestObservabilityEvent> observabilityEvents() { return context.observabilityEvents(); }
    public Optional<TestJobStatus> latestStatus() { return context.latestStatus(); }
    public Map<String, TestMetricSnapshot> metricSnapshot() { return context.metricSnapshot(); }
    public void assertCounter(String name, long expected) { context.assertCounter(name, expected); }
    public void assertGauge(String name, double expected) { context.assertGauge(name, expected); }
    public void assertTimerRecorded(String name) { context.assertTimerRecorded(name); }

    public void processAllAvailable() { processAllAvailable(processingTimeout); }

    public void processAllAvailable(Duration timeout) {
        requireRunning();
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        context.managedQueries().processAllAvailable(timeout);
    }

    public void assertHealthy() {
        requireRunning();
        context.managedQueries().validateHealthy();
    }

    public void stop() {
        if (!stopped.compareAndSet(false, true)) return;
        Throwable primary = null;
        try { context.managedQueries().stopAllNewQueries(null); }
        catch (Throwable throwable) { primary = throwable; }
        try { job.onStop(context); }
        catch (Throwable throwable) {
            if (primary == null) primary = throwable; else primary.addSuppressed(throwable);
        }
        try { context.close(); }
        catch (Throwable throwable) {
            if (primary == null) primary = throwable; else primary.addSuppressed(throwable);
        }
        if (primary instanceof RuntimeException runtime) throw runtime;
        if (primary != null) {
            throw new SparkJobTestException("TESTKIT_STREAMING_STOP_FAILED",
                    "Streaming job failed during TestKit shutdown", primary);
        }
    }

    @Override public void close() { stop(); }

    private void requireRunning() {
        if (stopped.get()) throw new SparkJobTestException("TESTKIT_STREAMING_RUN_STOPPED", "Streaming test run is stopped");
    }
}
