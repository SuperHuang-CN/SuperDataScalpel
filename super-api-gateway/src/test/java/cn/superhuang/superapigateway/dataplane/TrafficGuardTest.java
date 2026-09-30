package cn.superhuang.superapigateway.dataplane;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

class TrafficGuardTest {
    @Test void limitsRatesWithoutResettingOnEveryRequest() {
        var guard = new TrafficGuard(); var service = UUID.randomUUID();
        try (var first = guard.acquire(service, null, 1, 0, 0)) { assertThat(first).isNotNull(); }
        assertThat(guard.acquire(service, null, 1, 0, 0)).isNull();
    }
    @Test void releasesConcurrencyExactlyOnce() {
        var guard = new TrafficGuard(); var service = UUID.randomUUID();
        var first = guard.acquire(service, null, 0, 0, 1);
        assertThat(guard.acquire(service, null, 0, 0, 1)).isNull();
        first.close(); first.close();
        var next = guard.acquire(service, null, 0, 0, 1);
        assertThat(next).isNotNull();
        assertThat(guard.acquire(service, null, 0, 0, 1)).isNull();
        next.close();
    }
    @Test void consumerLimitsAreIndependentAndRejectionReleasesServiceLease() {
        var guard = new TrafficGuard(); var service = UUID.randomUUID(); var consumer = UUID.randomUUID();
        guard.acquire(service, consumer, 0, 1, 1).close();
        assertThat(guard.acquire(service, consumer, 0, 1, 1)).isNull();
        try (var lease = guard.acquire(service, UUID.randomUUID(), 0, 1, 1)) { assertThat(lease).isNotNull(); }
    }
    @Test void concurrentAdmissionNeverExceedsCapacity() throws Exception {
        var guard = new TrafficGuard(); var service = UUID.randomUUID();
        var active = new AtomicInteger(); var peak = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(16)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 2000; i++) futures.add(pool.submit(() -> {
                var lease = guard.acquire(service, null, 0, 0, 3);
                if (lease != null) try {
                    peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                    Thread.yield(); active.decrementAndGet();
                } finally { lease.close(); }
            }));
            for (var future : futures) future.get();
        }
        assertThat(peak.get()).isBetween(1, 3);
        assertThat(active.get()).isZero();
    }
}
