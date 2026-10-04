package cn.superhuang.superapigateway.accesslog;

import cn.superhuang.superapigateway.configuration.SuperApiGatewayProperties;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AccessLogPublisher {

    private static final Logger log = LoggerFactory.getLogger(AccessLogPublisher.class);

    private final SuperApiGatewayProperties.AccessLog properties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final ArrayBlockingQueue<PendingLog> queue;
    private final Counter dropped;
    private final Counter sendFailures;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong nextWarningAtMillis = new AtomicLong();
    private Thread worker;
    private final cn.superhuang.superapigateway.runtime.GatewayTelemetry telemetry;
    private final AtomicLong delivered = new AtomicLong();
    private volatile java.time.Instant lastDeliveredAt;

    public AccessLogPublisher(
            SuperApiGatewayProperties properties,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry registry,
            cn.superhuang.superapigateway.runtime.GatewayTelemetry telemetry
    ) {
        this.properties = properties.accessLog();
        this.telemetry = telemetry;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.queue = new ArrayBlockingQueue<>(properties.accessLog().queueCapacity());
        this.dropped = registry.counter("super_api_gateway_access_log_dropped_total");
        this.sendFailures = registry.counter("super_api_gateway_access_log_send_failures_total");
    }

    @PostConstruct
    void start() {
        if (!properties.enabled()) return;
        running.set(true);
        worker = Thread.ofPlatform().name("gateway-access-log").daemon(true).start(this::sendLoop);
    }

    public void publish(GatewayAccessLog event) {
        telemetry.record(event);
        if (!properties.enabled()) return;
        if (!queue.offer(new PendingLog(event, 0))) {
            dropped.increment();
            warnRateLimited("access-log queue is full", null);
        }
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (worker != null) {
            try { worker.join(5000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            if (worker.isAlive()) worker.interrupt();
        }
    }

    private void sendLoop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                PendingLog pending = queue.poll(1, TimeUnit.SECONDS);
                if (pending == null) continue;
                try {
                    String json = objectMapper.writeValueAsString(pending.event());
                    kafkaTemplate.send(properties.topic(), pending.event().eventId(), json)
                            .whenComplete((result, failure) -> {
                                if (failure != null) retryOrDrop(pending, failure);
                                else { delivered.incrementAndGet(); lastDeliveredAt = java.time.Instant.now(); }
                            });
                } catch (Exception exception) { retryOrDrop(pending, exception); }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                recordSendFailure(exception);
            }
        }
    }

    private void recordSendFailure(Throwable failure) {
        sendFailures.increment();
        warnRateLimited("Kafka send failed", failure);
        log.debug("Unable to publish gateway access log", failure);
    }

    private void retryOrDrop(PendingLog pending, Throwable failure) {
        recordSendFailure(failure);
        // The same event ID is reused, so ambiguous delivery is deduplicated by the receiver.
        if (running.get() && pending.attempt() < 2
                && queue.offer(new PendingLog(pending.event(), pending.attempt() + 1))) return;
        dropped.increment();
    }

    private record PendingLog(GatewayAccessLog event, int attempt) {}

    public DeliveryStatus deliveryStatus() {
        return new DeliveryStatus(properties.enabled(), properties.topic(), queue.size(), properties.queueCapacity(),
                (long) dropped.count(), (long) sendFailures.count(), delivered.get(), lastDeliveredAt);
    }
    public record DeliveryStatus(boolean enabled, String topic, int queued, int capacity,
                                 long dropped, long failures, long delivered, java.time.Instant lastDeliveredAt) {}

    private void warnRateLimited(String reason, Throwable failure) {
        long now = System.currentTimeMillis();
        long next = nextWarningAtMillis.get();
        if (now < next
                || !nextWarningAtMillis.compareAndSet(
                        next,
                        now + TimeUnit.MINUTES.toMillis(1)
                )) {
            return;
        }
        if (failure == null) {
            log.warn("Gateway access-log delivery problem: {}", reason);
        } else {
            log.warn("Gateway access-log delivery problem: {} ({})",
                    reason, failure.getClass().getSimpleName());
        }
    }
}
