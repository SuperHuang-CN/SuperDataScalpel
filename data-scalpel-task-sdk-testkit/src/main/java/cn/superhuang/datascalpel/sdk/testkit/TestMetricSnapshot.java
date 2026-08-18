package cn.superhuang.datascalpel.sdk.testkit;

public record TestMetricSnapshot(
        String name,
        TestMetricKind kind,
        Long counterValue,
        Double gaugeValue,
        Long count,
        Long lastDurationMillis,
        Long totalDurationMillis,
        Long maxDurationMillis
) {
}
