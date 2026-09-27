package cn.superhuang.data.scalpel.dispatcher.backend;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** One bounded probe per configured target; HTTP requests never wait for backend commands. */
final class TargetReadinessCache implements AutoCloseable {
    private static final long TTL_NANOS = Duration.ofSeconds(30).toNanos();
    private final ExecutorService workers;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    TargetReadinessCache(int targetCount) {
        workers = Executors.newFixedThreadPool(Math.max(1, targetCount),
                Thread.ofPlatform().name("dispatcher-target-health-", 0).daemon(true).factory());
    }

    BackendReadiness get(String key, Supplier<BackendReadiness> probe) {
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry());
        long age = System.nanoTime() - entry.checkedAt;
        if (entry.value != null && age < TTL_NANOS) return entry.value;
        if (entry.running.compareAndSet(false, true)) workers.execute(() -> {
            try { entry.value = probe.get(); }
            catch (RuntimeException failure) { entry.value = BackendReadiness.down("目标检查失败，请检查部署配置与服务状态"); }
            finally { entry.checkedAt = System.nanoTime(); entry.running.set(false); }
        });
        return BackendReadiness.pending();
    }

    @Override public void close() { workers.shutdownNow(); }
    private static final class Entry {
        private final AtomicBoolean running = new AtomicBoolean();
        private volatile BackendReadiness value;
        private volatile long checkedAt;
    }
}
