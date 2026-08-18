package cn.superhuang.datascalpel.sdk;

import java.util.Map;

/**
 * Driver-side observability API for user Spark jobs.
 *
 * <p>This object is thread-safe but must not be captured by an Executor closure. Invalid names,
 * messages, attributes, values, metric-kind changes, or metric-limit overflows are rejected with
 * {@link IllegalArgumentException}.</p>
 */
public interface JobObservability {
    void info(String eventName, String message);

    void info(String eventName, String message, Map<String, String> attributes);

    void warn(String eventName, String message);

    void warn(String eventName, String message, Map<String, String> attributes);

    void error(String eventName, String message);

    void error(String eventName, String message, Map<String, String> attributes);

    void status(String phase, String message);

    void addCounter(String name, long delta);

    void setGauge(String name, double value);

    JobOperation operation(String name);
}
