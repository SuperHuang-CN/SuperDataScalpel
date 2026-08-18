package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.UserJobMetricSnapshot;
import cn.superhuang.data.scalpel.contract.execution.UserJobObservabilitySnapshot;
import cn.superhuang.data.scalpel.contract.execution.UserJobStatus;
import cn.superhuang.datascalpel.sdk.JobObservability;
import cn.superhuang.datascalpel.sdk.JobOperation;
import cn.superhuang.datascalpel.sdk.SparkJobIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Attempt-scoped, driver-side implementation of the public observability API. */
final class UserJobObservabilityRuntime implements JobObservability, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserJobObservabilityRuntime.class);
    private static final Pattern NAME = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,99}");
    private static final String PLATFORM_PREFIX = "datascalpel.";
    private static final int MAX_USER_METRICS = 100;
    private static final int MAX_ATTRIBUTES = 20;
    private static final long PUBLISH_INTERVAL_SECONDS = 5;

    private final SparkSession spark;
    private final SparkJobIdentity identity;
    private final ObjectMapper objectMapper;
    private final Consumer<UserJobObservabilitySnapshot> publisher;
    private final Object lock = new Object();
    private final Map<String, MutableMetric> metrics = new ConcurrentHashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final ScheduledExecutorService scheduler;
    private UserJobStatus status;
    private int userMetricCount;

    UserJobObservabilityRuntime(
            SparkSession spark,
            SparkJobIdentity identity,
            ObjectMapper objectMapper,
            Consumer<UserJobObservabilitySnapshot> publisher
    ) {
        this.spark = Objects.requireNonNull(spark, "spark");
        this.identity = Objects.requireNonNull(identity, "identity");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.publisher = publisher;
        if (publisher == null) {
            scheduler = null;
        } else {
            scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "datascalpel-user-observability");
                thread.setDaemon(true);
                return thread;
            });
            scheduler.scheduleWithFixedDelay(
                    this::publishDirtyBestEffort,
                    PUBLISH_INTERVAL_SECONDS,
                    PUBLISH_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
        }
    }

    @Override
    public void info(String eventName, String message) {
        info(eventName, message, Map.of());
    }

    @Override
    public void info(String eventName, String message, Map<String, String> attributes) {
        emit(Level.INFO, eventName, message, attributes);
    }

    @Override
    public void warn(String eventName, String message) {
        warn(eventName, message, Map.of());
    }

    @Override
    public void warn(String eventName, String message, Map<String, String> attributes) {
        emit(Level.WARN, eventName, message, attributes);
    }

    @Override
    public void error(String eventName, String message) {
        error(eventName, message, Map.of());
    }

    @Override
    public void error(String eventName, String message, Map<String, String> attributes) {
        emit(Level.ERROR, eventName, message, attributes);
    }

    @Override
    public void status(String phase, String message) {
        setStatus(phase, message);
    }

    void platformStatus(String phase, String message) {
        setStatus(phase, message);
    }

    @Override
    public void addCounter(String name, long delta) {
        requireOpen();
        String normalized = userMetricName(name);
        if (delta < 0) throw new IllegalArgumentException("Counter 增量不能为负数");
        synchronized (lock) {
            MutableCounter counter = metric(normalized, MutableCounter.class, false, MutableCounter::new);
            try {
                counter.value = Math.addExact(counter.value, delta);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Counter 数值溢出", exception);
            }
            dirty.set(true);
        }
    }

    void addPlatformCounter(String name, long delta) {
        requireOpen();
        if (delta < 0) throw new IllegalArgumentException("平台 Counter 增量不能为负数");
        synchronized (lock) {
            MutableCounter counter = metric(platformMetricName(name), MutableCounter.class, true, MutableCounter::new);
            counter.value = Math.addExact(counter.value, delta);
            dirty.set(true);
        }
    }

    @Override
    public void setGauge(String name, double value) {
        requireOpen();
        setGaugeInternal(userMetricName(name), value, false);
    }

    void setPlatformGauge(String name, double value) {
        setGaugeInternal(platformMetricName(name), value, true);
    }

    @Override
    public JobOperation operation(String name) {
        requireOpen();
        String normalized = userMetricName(name);
        synchronized (lock) {
            metric(normalized, MutableTimer.class, false, MutableTimer::new);
        }
        Thread owner = Thread.currentThread();
        String previousDescription = spark.sparkContext().getLocalProperty("spark.job.description");
        spark.sparkContext().setJobDescription(normalized);
        long startedNanos = System.nanoTime();
        return new JobOperation() {
            private final AtomicBoolean operationClosed = new AtomicBoolean();

            @Override
            public void close() {
                if (Thread.currentThread() != owner) {
                    throw new IllegalStateException("JobOperation 必须在创建它的线程关闭");
                }
                if (!operationClosed.compareAndSet(false, true)) return;
                long durationMillis = TimeUnit.NANOSECONDS.toMillis(
                        Math.max(0, System.nanoTime() - startedNanos));
                try {
                    recordTimer(normalized, durationMillis, false);
                } finally {
                    spark.sparkContext().setJobDescription(previousDescription);
                }
            }
        };
    }

    void recordPlatformTimer(String name, long durationMillis) {
        recordTimer(platformMetricName(name), durationMillis, true);
    }

    void platformInfo(String eventName, String message, Map<String, String> attributes) {
        emit(Level.INFO, platformEventName(eventName), message, attributes);
    }

    void platformError(String eventName, String message, Map<String, String> attributes) {
        emit(Level.ERROR, platformEventName(eventName), message, attributes);
    }

    UserJobObservabilitySnapshot snapshot() {
        synchronized (lock) {
            if (status == null && metrics.isEmpty()) return null;
            List<UserJobMetricSnapshot> values = new ArrayList<>(metrics.size());
            metrics.entrySet().stream()
                    .filter(entry -> !(entry.getValue() instanceof MutableTimer timer) || timer.count > 0)
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> values.add(entry.getValue().snapshot(entry.getKey())));
            if (status == null && values.isEmpty()) return null;
            return new UserJobObservabilitySnapshot(Instant.now(), status, values);
        }
    }

    void flushBestEffort() {
        if (publisher == null) return;
        publish(true);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (scheduler != null) scheduler.shutdownNow();
        flushBestEffort();
    }

    private void setStatus(String phase, String message) {
        requireOpen();
        String normalizedPhase = requiredName(phase, "用户作业阶段");
        String normalizedMessage = requiredMessage(message);
        synchronized (lock) {
            status = new UserJobStatus(normalizedPhase, normalizedMessage, Instant.now());
            dirty.set(true);
        }
        emit(Level.INFO, PLATFORM_PREFIX + "status.changed", normalizedMessage,
                Map.of("phase", normalizedPhase));
    }

    private void emit(Level level, String eventName, String message, Map<String, String> attributes) {
        requireOpen();
        String normalizedName = requiredName(eventName, "事件名称");
        String normalizedMessage = requiredMessage(message);
        Map<String, String> safeAttributes = attributes(attributes);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now());
        payload.put("level", level.name());
        payload.put("event", normalizedName);
        payload.put("message", normalizedMessage);
        payload.put("attributes", safeAttributes);
        payload.put("taskId", identity.taskId());
        payload.put("runId", identity.runId());
        payload.put("attempt", identity.attempt());
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("用户作业结构化事件序列化失败", exception);
        }
        switch (level) {
            case INFO -> LOGGER.info("DATASCALPEL_USER_EVENT {}", json);
            case WARN -> LOGGER.warn("DATASCALPEL_USER_EVENT {}", json);
            case ERROR -> LOGGER.error("DATASCALPEL_USER_EVENT {}", json);
        }
    }

    private void setGaugeInternal(String name, double value, boolean platform) {
        requireOpen();
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Gauge 必须是有限数值");
        synchronized (lock) {
            MutableGauge gauge = metric(name, MutableGauge.class, platform, MutableGauge::new);
            gauge.value = value;
            dirty.set(true);
        }
    }

    private void recordTimer(String name, long durationMillis, boolean platform) {
        requireOpen();
        if (durationMillis < 0) throw new IllegalArgumentException("Timer 耗时不能为负数");
        synchronized (lock) {
            MutableTimer timer = metric(name, MutableTimer.class, platform, MutableTimer::new);
            timer.count = Math.addExact(timer.count, 1);
            timer.last = durationMillis;
            timer.total = Math.addExact(timer.total, durationMillis);
            timer.max = Math.max(timer.max, durationMillis);
            dirty.set(true);
        }
    }

    private <T extends MutableMetric> T metric(
            String name,
            Class<T> type,
            boolean platform,
            java.util.function.Supplier<T> factory
    ) {
        MutableMetric existing = metrics.get(name);
        if (existing != null) {
            if (!type.isInstance(existing)) {
                throw new IllegalArgumentException("同名指标不能切换类型：" + name);
            }
            return type.cast(existing);
        }
        if (!platform && userMetricCount >= MAX_USER_METRICS) {
            throw new IllegalArgumentException("用户自定义指标不能超过 " + MAX_USER_METRICS + " 个");
        }
        T created = factory.get();
        metrics.put(name, created);
        if (!platform) userMetricCount++;
        return created;
    }

    private void publishDirtyBestEffort() {
        publish(false);
    }

    private synchronized void publish(boolean force) {
        if (publisher == null) return;
        boolean changed = dirty.getAndSet(false);
        if (!force && !changed) return;
        UserJobObservabilitySnapshot value = snapshot();
        if (value == null) return;
        try {
            publisher.accept(value);
        } catch (RuntimeException exception) {
            dirty.set(true);
            LOGGER.warn("event=USER_OBSERVABILITY_DELIVERY_FAILED runId={} attempt={} message={}",
                    identity.runId(), identity.attempt(), safeLogMessage(exception));
        }
    }

    private String userMetricName(String value) {
        String normalized = requiredName(value, "指标名称");
        if (normalized.startsWith(PLATFORM_PREFIX)) {
            throw new IllegalArgumentException("datascalpel. 前缀保留给平台指标");
        }
        return normalized;
    }

    private static String platformMetricName(String value) {
        String normalized = requiredName(value, "平台指标名称");
        if (!normalized.startsWith(PLATFORM_PREFIX)) {
            throw new IllegalArgumentException("平台指标必须使用 datascalpel. 前缀");
        }
        return normalized;
    }

    private static String platformEventName(String value) {
        String normalized = requiredName(value, "平台事件名称");
        return normalized.startsWith(PLATFORM_PREFIX) ? normalized : PLATFORM_PREFIX + normalized;
    }

    private static String requiredName(String value, String label) {
        if (value == null || !NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(label + "必须匹配 " + NAME.pattern());
        }
        return value;
    }

    private static String requiredMessage(String value) {
        if (value == null || value.isBlank() || value.length() > 1000) {
            throw new IllegalArgumentException("事件或状态消息必须为 1～1000 个字符");
        }
        return value;
    }

    private static Map<String, String> attributes(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        if (values.size() > MAX_ATTRIBUTES) {
            throw new IllegalArgumentException("单个事件属性不能超过 " + MAX_ATTRIBUTES + " 个");
        }
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String normalizedKey = requiredAttributeName(key);
            if (value == null || value.length() > 1000) {
                throw new IllegalArgumentException("事件属性值长度不能超过1000字符");
            }
            result.put(normalizedKey, value);
        });
        return Collections.unmodifiableMap(result);
    }

    private static String requiredAttributeName(String value) {
        if (value == null || value.isBlank() || value.length() > 100) {
            throw new IllegalArgumentException("事件属性名必须为 1～100 个字符");
        }
        return value;
    }

    private void requireOpen() {
        if (closed.get()) throw new IllegalStateException("用户作业观测上下文已经关闭");
    }

    private static String safeLogMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.replace('\n', ' ').replace('\r', ' ').substring(0, Math.min(300, message.length()));
    }

    private enum Level { INFO, WARN, ERROR }

    private sealed interface MutableMetric permits MutableCounter, MutableGauge, MutableTimer {
        UserJobMetricSnapshot snapshot(String name);
    }

    private static final class MutableCounter implements MutableMetric {
        private long value;
        @Override public UserJobMetricSnapshot snapshot(String name) {
            return UserJobMetricSnapshot.counter(name, value);
        }
    }

    private static final class MutableGauge implements MutableMetric {
        private double value;
        @Override public UserJobMetricSnapshot snapshot(String name) {
            return UserJobMetricSnapshot.gauge(name, value);
        }
    }

    private static final class MutableTimer implements MutableMetric {
        private long count;
        private long last;
        private long total;
        private long max;
        @Override public UserJobMetricSnapshot snapshot(String name) {
            return UserJobMetricSnapshot.timer(name, count, last, total, max);
        }
    }
}
