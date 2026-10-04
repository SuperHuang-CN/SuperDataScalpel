package cn.superhuang.superapigateway.dataplane;

import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Per-node token buckets and concurrency leases. No network, JDBC or scheduler waits. */
@Component
public class TrafficGuard {
    private static final int MAX_BUCKETS = 100_000;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Lease acquire(UUID serviceId, UUID consumerId, int serviceRate, int consumerRate, int concurrency) {
        if (serviceRate == 0 && consumerRate == 0 && concurrency == 0) return new Lease(null);
        Bucket service = bucket("s:" + serviceId);
        if (service == null || !service.acquire(serviceRate, concurrency, true)) return null;
        if (consumerId != null && consumerRate > 0) {
            Bucket consumer = bucket("c:" + serviceId + ":" + consumerId);
            if (consumer == null || !consumer.acquire(consumerRate, 0, false)) {
                service.release();
                return null;
            }
        }
        return new Lease(service);
    }

    private Bucket bucket(String key) {
        Bucket existing = buckets.get(key);
        if (existing != null) return existing;
        // Serialize only creation, never normal request admission. Keep the cardinality strictly bounded.
        synchronized (buckets) {
            existing = buckets.get(key);
            if (existing != null) return existing;
            if (buckets.size() >= MAX_BUCKETS) return null;
            var created = new Bucket();
            buckets.put(key, created);
            return created;
        }
    }

    public static final class Lease implements AutoCloseable {
        private final Bucket bucket;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Lease(Bucket bucket) { this.bucket = bucket; }
        public void close() { if (bucket != null && closed.compareAndSet(false, true)) bucket.release(); }
    }

    private static final class Bucket {
        private int inFlight;
        private double tokens;
        private int previousRate;
        private long lastRefill = System.nanoTime();
        synchronized boolean acquire(int rate, int concurrency, boolean lease) {
            long now = System.nanoTime();
            if (concurrency > 0 && inFlight >= concurrency) return false;
            if (rate > 0) {
                if (previousRate == 0) tokens = rate;
                tokens = Math.min(rate, tokens + Math.max(0, now - lastRefill) / 1_000_000_000D * rate);
                lastRefill = now;
                previousRate = rate;
                if (tokens < 1) return false;
                tokens--;
            } else { previousRate = 0; lastRefill = now; }
            if (lease) inFlight++;
            return true;
        }
        synchronized void release() { inFlight--; }
    }
}
