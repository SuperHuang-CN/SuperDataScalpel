package cn.superhuang.datascalpel.sdk.testkit;

import cn.superhuang.datascalpel.sdk.JobObservability;
import cn.superhuang.datascalpel.sdk.JobOperation;
import org.apache.spark.sql.SparkSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

final class TestJobObservability implements JobObservability {
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,99}");
    private static final String PLATFORM_PREFIX = "datascalpel.";
    private final SparkSession spark;
    private final List<TestObservabilityEvent> events = new ArrayList<>();
    private final Map<String, MutableMetric> metrics = new TreeMap<>();
    private TestJobStatus status;
    private int userMetricCount;

    TestJobObservability(SparkSession spark) {
        this.spark = spark;
    }

    @Override public void info(String eventName, String message) { info(eventName, message, Map.of()); }
    @Override public void info(String eventName, String message, Map<String, String> attributes) {
        emit(TestObservabilityLevel.INFO, eventName, message, attributes);
    }
    @Override public void warn(String eventName, String message) { warn(eventName, message, Map.of()); }
    @Override public void warn(String eventName, String message, Map<String, String> attributes) {
        emit(TestObservabilityLevel.WARN, eventName, message, attributes);
    }
    @Override public void error(String eventName, String message) { error(eventName, message, Map.of()); }
    @Override public void error(String eventName, String message, Map<String, String> attributes) {
        emit(TestObservabilityLevel.ERROR, eventName, message, attributes);
    }

    @Override
    public synchronized void status(String phase, String message) {
        status = new TestJobStatus(name(phase), message(message), Instant.now());
        emit(TestObservabilityLevel.INFO, "datascalpel.status.changed", status.message(),
                Map.of("phase", status.phase()));
    }

    @Override
    public synchronized void addCounter(String name, long delta) {
        if (delta < 0) throw new IllegalArgumentException("Counter delta must not be negative");
        MutableCounter counter = metric(userName(name), MutableCounter.class, false, MutableCounter::new);
        counter.value = Math.addExact(counter.value, delta);
    }

    synchronized void addPlatformCounter(String name, long delta) {
        if (delta < 0) throw new IllegalArgumentException("Counter delta must not be negative");
        MutableCounter counter = metric(platformName(name), MutableCounter.class, true, MutableCounter::new);
        counter.value = Math.addExact(counter.value, delta);
    }

    @Override
    public synchronized void setGauge(String name, double value) {
        setGauge(userName(name), value, false);
    }

    synchronized void setPlatformGauge(String name, double value) {
        setGauge(platformName(name), value, true);
    }

    @Override
    public synchronized JobOperation operation(String name) {
        String metricName = userName(name);
        metric(metricName, MutableTimer.class, false, MutableTimer::new);
        Thread owner = Thread.currentThread();
        String previous = spark.sparkContext().getLocalProperty("spark.job.description");
        spark.sparkContext().setJobDescription(metricName);
        long started = System.nanoTime();
        return new JobOperation() {
            private final AtomicBoolean closed = new AtomicBoolean();
            @Override public void close() {
                if (Thread.currentThread() != owner) {
                    throw new IllegalStateException("JobOperation must close on its creating thread");
                }
                if (!closed.compareAndSet(false, true)) return;
                try {
                    recordTimer(metricName, TimeUnit.NANOSECONDS.toMillis(
                            Math.max(0, System.nanoTime() - started)), false);
                } finally {
                    spark.sparkContext().setJobDescription(previous);
                }
            }
        };
    }

    synchronized void recordPlatformTimer(String name, long durationMillis) {
        recordTimer(platformName(name), durationMillis, true);
    }

    void platformInfo(String name, String message, Map<String, String> attributes) {
        emit(TestObservabilityLevel.INFO, platformName(name), message, attributes);
    }

    void platformError(String name, String message, Map<String, String> attributes) {
        emit(TestObservabilityLevel.ERROR, platformName(name), message, attributes);
    }

    synchronized List<TestObservabilityEvent> events() { return List.copyOf(events); }
    synchronized Optional<TestJobStatus> latestStatus() { return Optional.ofNullable(status); }
    synchronized Map<String, TestMetricSnapshot> snapshot() {
        LinkedHashMap<String, TestMetricSnapshot> result = new LinkedHashMap<>();
        metrics.forEach((name, metric) -> {
            if (!(metric instanceof MutableTimer timer) || timer.count > 0) {
                result.put(name, metric.snapshot(name));
            }
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private synchronized void emit(
            TestObservabilityLevel level, String eventName, String message,
            Map<String, String> attributes
    ) {
        String normalizedName = name(eventName);
        String normalizedMessage = message(message);
        if (attributes == null || attributes.size() > 20) {
            throw new IllegalArgumentException("Event attributes must contain at most 20 entries");
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            String normalizedKey = attributeName(key);
            if (value == null || value.length() > 1000) {
                throw new IllegalArgumentException("Event attribute value is invalid");
            }
            values.put(normalizedKey, value);
        });
        events.add(new TestObservabilityEvent(
                Instant.now(), level, normalizedName, normalizedMessage, values));
    }

    private void setGauge(String name, double value, boolean platform) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Gauge must be finite");
        MutableGauge gauge = metric(name, MutableGauge.class, platform, MutableGauge::new);
        gauge.value = value;
    }

    private synchronized void recordTimer(String name, long durationMillis, boolean platform) {
        MutableTimer timer = metric(name, MutableTimer.class, platform, MutableTimer::new);
        timer.count = Math.addExact(timer.count, 1);
        timer.last = durationMillis;
        timer.total = Math.addExact(timer.total, durationMillis);
        timer.max = Math.max(timer.max, durationMillis);
    }

    private <T extends MutableMetric> T metric(
            String name, Class<T> type, boolean platform, java.util.function.Supplier<T> supplier
    ) {
        MutableMetric existing = metrics.get(name);
        if (existing != null) {
            if (!type.isInstance(existing)) throw new IllegalArgumentException("Metric kind conflict: " + name);
            return type.cast(existing);
        }
        if (!platform && userMetricCount >= 100) throw new IllegalArgumentException("Too many user metrics");
        T created = supplier.get();
        metrics.put(name, created);
        if (!platform) userMetricCount++;
        return created;
    }

    private static String userName(String value) {
        String name = name(value);
        if (name.startsWith(PLATFORM_PREFIX)) throw new IllegalArgumentException("datascalpel. is reserved");
        return name;
    }

    private static String platformName(String value) {
        String name = name(value);
        return name.startsWith(PLATFORM_PREFIX) ? name : PLATFORM_PREFIX + name;
    }

    private static String name(String value) {
        if (value == null || !NAME.matcher(value).matches()) throw new IllegalArgumentException("Invalid name");
        return value;
    }

    private static String message(String value) {
        if (value == null || value.isBlank() || value.length() > 1000) {
            throw new IllegalArgumentException("Invalid message");
        }
        return value;
    }

    private static String attributeName(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException("Invalid event attribute name");
        }
        return value;
    }

    private sealed interface MutableMetric permits MutableCounter, MutableGauge, MutableTimer {
        TestMetricSnapshot snapshot(String name);
    }
    private static final class MutableCounter implements MutableMetric {
        long value;
        @Override public TestMetricSnapshot snapshot(String name) {
            return new TestMetricSnapshot(name, TestMetricKind.COUNTER, value, null, null, null, null, null);
        }
    }
    private static final class MutableGauge implements MutableMetric {
        double value;
        @Override public TestMetricSnapshot snapshot(String name) {
            return new TestMetricSnapshot(name, TestMetricKind.GAUGE, null, value, null, null, null, null);
        }
    }
    private static final class MutableTimer implements MutableMetric {
        long count; long last; long total; long max;
        @Override public TestMetricSnapshot snapshot(String name) {
            return new TestMetricSnapshot(name, TestMetricKind.TIMER, null, null, count, last, total, max);
        }
    }
}
