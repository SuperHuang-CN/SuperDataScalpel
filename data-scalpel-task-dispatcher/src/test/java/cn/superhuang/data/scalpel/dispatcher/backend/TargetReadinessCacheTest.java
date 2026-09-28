package cn.superhuang.data.scalpel.dispatcher.backend;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class TargetReadinessCacheTest {
    @Test void aBlockedTargetDoesNotBlockAnotherAndProbesAreSingleFlight() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var cache = new TargetReadinessCache(2)) {
            java.util.function.Supplier<BackendReadiness> slow = () -> {
                calls.incrementAndGet(); started.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return BackendReadiness.up();
            };
            var pending = cache.get("slow", slow);
            assertThat(pending.ready()).isFalse();
            assertThat(pending.checking()).isTrue();
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                while (!cache.get("fast", BackendReadiness::up).ready()) Thread.onSpinWait();
                assertThat(cache.get("fast", BackendReadiness::up).checking()).isFalse();
                for (int i = 0; i < 100; i++) assertThat(cache.get("slow", slow).ready()).isFalse();
            });
            assertThat(calls).hasValue(1);
        } finally { release.countDown(); }
    }

    @Test void failedProbeIsNotReportedAsStillChecking() {
        try (var cache = new TargetReadinessCache(1)) {
            java.util.function.Supplier<BackendReadiness> fail = () -> BackendReadiness.down("不可连接");
            assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
                while (cache.get("failed", fail).checking()) Thread.onSpinWait();
            });
            assertThat(cache.get("failed", fail).ready()).isFalse();
            assertThat(cache.get("failed", fail).issues()).containsExactly("不可连接");
        }
    }
}
