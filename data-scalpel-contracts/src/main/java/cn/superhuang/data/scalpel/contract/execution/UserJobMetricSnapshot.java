package cn.superhuang.data.scalpel.contract.execution;

public record UserJobMetricSnapshot(
        String name,
        UserJobMetricKind kind,
        Long counterValue,
        Double gaugeValue,
        Long count,
        Long lastDurationMillis,
        Long totalDurationMillis,
        Long maxDurationMillis
) {
    public UserJobMetricSnapshot {
        name = ExecutionContractValidation.required(name, 100, "用户作业指标名称");
        if (!name.matches("[A-Za-z][A-Za-z0-9._-]{0,99}") || kind == null) {
            throw new IllegalArgumentException("用户作业指标无效");
        }
        switch (kind) {
            case COUNTER -> {
                if (counterValue == null || counterValue < 0 || gaugeValue != null || count != null
                        || lastDurationMillis != null || totalDurationMillis != null || maxDurationMillis != null) {
                    throw new IllegalArgumentException("Counter 指标载荷无效");
                }
            }
            case GAUGE -> {
                if (gaugeValue == null || !Double.isFinite(gaugeValue) || counterValue != null || count != null
                        || lastDurationMillis != null || totalDurationMillis != null || maxDurationMillis != null) {
                    throw new IllegalArgumentException("Gauge 指标载荷无效");
                }
            }
            case TIMER -> {
                if (counterValue != null || gaugeValue != null || count == null || count < 1
                        || lastDurationMillis == null || lastDurationMillis < 0
                        || totalDurationMillis == null || totalDurationMillis < lastDurationMillis
                        || maxDurationMillis == null || maxDurationMillis < lastDurationMillis
                        || maxDurationMillis > totalDurationMillis) {
                    throw new IllegalArgumentException("Timer 指标载荷无效");
                }
            }
        }
    }

    public static UserJobMetricSnapshot counter(String name, long value) {
        return new UserJobMetricSnapshot(name, UserJobMetricKind.COUNTER, value, null,
                null, null, null, null);
    }

    public static UserJobMetricSnapshot gauge(String name, double value) {
        return new UserJobMetricSnapshot(name, UserJobMetricKind.GAUGE, null, value,
                null, null, null, null);
    }

    public static UserJobMetricSnapshot timer(
            String name, long count, long lastDurationMillis,
            long totalDurationMillis, long maxDurationMillis
    ) {
        return new UserJobMetricSnapshot(name, UserJobMetricKind.TIMER, null, null,
                count, lastDurationMillis, totalDurationMillis, maxDurationMillis);
    }
}
