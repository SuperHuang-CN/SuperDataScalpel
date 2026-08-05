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
    private final ArrayBlockingQueue<GatewayAccessLog> queue;
    private final Counter dropped;
    private final Counter sendFailures;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicLong nextWarningAtMillis = new AtomicLong();
    private Thread worker;

    public AccessLogPublisher(
            SuperApiGatewayProperties properties,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry registry
    ) {
        this.properties = properties.accessLog();
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
        if (!properties.enabled()) return;
        if (!queue.offer(event)) {
            dropped.increment();
            warnRateLimited("access-log queue is full", null);
        }
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (worker != null) worker.interrupt();
    }

    private void sendLoop() {
        while (running.get() || !queue.isEmpty()) {
            try {
                GatewayAccessLog event = queue.poll(1, TimeUnit.SECONDS);
                if (event == null) continue;
                String json = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(properties.topic(), event.requestId(), json)
                        .whenComplete((result, failure) -> {
                            if (failure != null) recordSendFailure(failure);
                        });
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
            log.warn("Gateway access logs are being dropped: {}", reason);
        } else {
            log.warn("Gateway access logs are being dropped: {} ({})",
                    reason, failure.getClass().getSimpleName());
        }
    }
}
