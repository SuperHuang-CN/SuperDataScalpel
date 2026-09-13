package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public record UserJobObservabilitySnapshot(
        @JsonPropertyDescription("快照采集时间。")
        Instant capturedAt,
        @JsonPropertyDescription("用户作业主动报告的阶段、说明和更新时间；作业尚未报告状态且已有指标时可为空。")
        UserJobStatus status,
        @JsonPropertyDescription("按稳定名称返回的用户作业指标快照。")
        List<UserJobMetricSnapshot> metrics
) {
    private static final int MAX_USER_METRICS = 100;
    private static final Map<String, UserJobMetricKind> PLATFORM_METRICS = Map.ofEntries(
            Map.entry("datascalpel.model.write.attempts", UserJobMetricKind.COUNTER),
            Map.entry("datascalpel.model.write.successes", UserJobMetricKind.COUNTER),
            Map.entry("datascalpel.model.write.rows", UserJobMetricKind.COUNTER),
            Map.entry("datascalpel.model.write.duration", UserJobMetricKind.TIMER),
            Map.entry("datascalpel.jdbc.write.attempts", UserJobMetricKind.COUNTER),
            Map.entry("datascalpel.jdbc.write.successes", UserJobMetricKind.COUNTER),
            Map.entry("datascalpel.jdbc.write.rows", UserJobMetricKind.COUNTER),
            Map.entry("datascalpel.jdbc.write.duration", UserJobMetricKind.TIMER),
            Map.entry("datascalpel.streaming.registered_queries", UserJobMetricKind.GAUGE)
    );

    public UserJobObservabilitySnapshot {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        if (capturedAt == null || status == null && metrics.isEmpty()
                || metrics.size() > MAX_USER_METRICS + PLATFORM_METRICS.size()) {
            throw new IllegalArgumentException("用户作业观测快照无效");
        }
        HashSet<String> names = new HashSet<>();
        String previous = null;
        int userMetrics = 0;
        for (UserJobMetricSnapshot metric : metrics) {
            if (metric == null || !names.add(metric.name())
                    || previous != null && previous.compareTo(metric.name()) >= 0) {
                throw new IllegalArgumentException("用户作业指标必须按名称排序且不能重复");
            }
            if (metric.name().startsWith("datascalpel.")) {
                UserJobMetricKind expectedKind = PLATFORM_METRICS.get(metric.name());
                if (expectedKind == null || expectedKind != metric.kind()) {
                    throw new IllegalArgumentException("平台保留指标名称或类型无效");
                }
            } else if (++userMetrics > MAX_USER_METRICS) {
                throw new IllegalArgumentException("用户自定义指标不能超过 " + MAX_USER_METRICS + " 个");
            }
            previous = metric.name();
        }
    }
}
