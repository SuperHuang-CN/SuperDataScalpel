package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("用户 Spark JAR 作业上报的一个指标快照；由 kind 决定 COUNTER、GAUGE 或 TIMER 对应的有效数值字段。")
public record UserJobMetricSnapshot(
        @JsonPropertyDescription("用户作业指标名，匹配字母开头的英文标识符规则；同一次上报中用于区分指标。")
        String name,
        @JsonPropertyDescription("指标种类：COUNTER 使用累计整数，GAUGE 使用当前数值，TIMER 使用次数与累计耗时。")
        UserJobMetricKind kind,
        @JsonPropertyDescription("COUNTER 指标累计值；其他指标种类为空。")
        Long counterValue,
        @JsonPropertyDescription("GAUGE 指标当前值；其他指标种类为空。")
        Double gaugeValue,
        @JsonPropertyDescription("TIMER 指标记录次数；其他指标种类为空。")
        Long count,
        @JsonPropertyDescription("TIMER 最近一次耗时，单位毫秒；其他指标种类为空。")
        Long lastDurationMillis,
        @JsonPropertyDescription("TIMER 累计耗时，单位毫秒；其他指标种类为空。")
        Long totalDurationMillis,
        @JsonPropertyDescription("TIMER 单次最大耗时，单位毫秒；其他指标种类为空。")
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
