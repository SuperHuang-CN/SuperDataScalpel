package cn.superhuang.datascalpel.sdk.testkit;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record TestObservabilityEvent(
        Instant timestamp,
        TestObservabilityLevel level,
        String eventName,
        String message,
    Map<String, String> attributes
) {
    public TestObservabilityEvent {
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
