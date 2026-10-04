package cn.superhuang.superapigateway.runtime;

import cn.superhuang.superapigateway.accesslog.GatewayAccessLog;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/** Node-local diagnostics, independent of Kafka availability. Never retains request data. */
@Component
public class GatewayTelemetry {
    private final Instant since = Instant.now();
    private final LongAdder requests = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final Timer latency;
    private final ArrayDeque<RecentCall> recent = new ArrayDeque<>();
    public GatewayTelemetry(MeterRegistry registry) {
        latency = Timer.builder("super_api_gateway_request_duration")
                .publishPercentiles(.95, .99).distributionStatisticExpiry(Duration.ofMinutes(5)).register(registry);
    }
    public void record(GatewayAccessLog event) {
        requests.increment();
        int status = event.response().status();
        if (status >= 500) errors.increment();
        if (event.upstreamStatus() == null && (status == 401 || status == 403 || status == 413 || status == 429)) rejected.increment();
        latency.record(event.latencies().request(), TimeUnit.MILLISECONDS);
        synchronized (recent) {
            if (recent.size() == 100) recent.removeLast();
            recent.addFirst(new RecentCall(event.observedAt(), event.request().id(), event.service().name(),
                    event.consumer() == null ? null : event.consumer().username(), status,
                    event.latencies().request(), event.upstreamStatus()));
        }
    }
    public Snapshot snapshot() {
        List<RecentCall> calls;
        synchronized (recent) { calls = List.copyOf(recent); }
        var histogram = latency.takeSnapshot();
        Double p95 = null, p99 = null;
        if (histogram.count() > 0) for (var percentile : histogram.percentileValues()) {
            if (percentile.percentile() == .95) p95 = percentile.value(TimeUnit.MILLISECONDS);
            if (percentile.percentile() == .99) p99 = percentile.value(TimeUnit.MILLISECONDS);
        }
        var heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        var os = ManagementFactory.getOperatingSystemMXBean();
        double cpu = os instanceof com.sun.management.OperatingSystemMXBean bean ? bean.getProcessCpuLoad() : -1;
        return new Snapshot(since, requests.sum(), errors.sum(), rejected.sum(), p95, p99,
                heap.getUsed(), heap.getMax(), cpu < 0 ? null : cpu, calls);
    }
    public record RecentCall(Instant at, String requestId, String serviceCode, String consumerCode,
                             int status, long durationMs, String upstreamStatus) {}
    public record Snapshot(Instant since, long requestCount, long serverErrors, long rejected,
                           Double recentP95Ms, Double recentP99Ms, long heapUsedBytes, long heapMaxBytes,
                           Double processCpuLoad, List<RecentCall> recentCalls) {}
}
