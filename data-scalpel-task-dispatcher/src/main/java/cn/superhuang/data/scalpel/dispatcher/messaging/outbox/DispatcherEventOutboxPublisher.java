package cn.superhuang.data.scalpel.dispatcher.messaging.outbox;

import cn.superhuang.data.scalpel.contract.execution.ExecutionKafkaHeaders;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherProperties;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherEventOutbox;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherOutboxState;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherEventOutboxRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class DispatcherEventOutboxPublisher {
    private final DispatcherEventOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final DispatcherProperties properties;
    private final TransactionTemplate transactions;

    public DispatcherEventOutboxPublisher(
            DispatcherEventOutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            DispatcherProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(scheduler = "dispatcherOutboxScheduler", fixedDelayString = "${data-scalpel.dispatcher.outbox-poll-interval:500ms}")
    public void publishDue() {
        recoverStaleClaims();
        List<Claim> claims = transactions.execute(status -> repository.findDueForUpdate(
                List.of(DispatcherOutboxState.PENDING, DispatcherOutboxState.FAILED), Instant.now(), PageRequest.of(0, 50)
        ).stream().map(event -> {
            event.publishing(Instant.now());
            repository.save(event);
            return Claim.from(event);
        }).toList());
        if (claims == null) return;
        claims.forEach(this::publish);
    }

    private void recoverStaleClaims() {
        Instant now = Instant.now();
        transactions.executeWithoutResult(status -> repository.findStaleClaimsForUpdate(
                now.minus(properties.outboxClaimTimeout()), PageRequest.of(0, 50)
        ).forEach(event -> {
            event.recoverStaleClaim(now, properties.outboxClaimTimeout());
            repository.save(event);
        }));
    }

    private void publish(Claim claim) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    claim.topic, null, claim.executionId.toString(), claim.payload, List.of(
                    header(ExecutionKafkaHeaders.MESSAGE_VERSION, "1"),
                    header(ExecutionKafkaHeaders.MESSAGE_TYPE, claim.messageType),
                    header(ExecutionKafkaHeaders.ENGINE_ID, claim.engineId.toString())
            ));
            kafkaTemplate.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            transactions.executeWithoutResult(status -> repository.findByMessageId(claim.messageId).ifPresent(event -> {
                event.published(); repository.save(event);
            }));
        } catch (Exception exception) {
            transactions.executeWithoutResult(status -> repository.findByMessageId(claim.messageId).ifPresent(event -> {
                event.failed(exception.getMessage()); repository.save(event);
            }));
        }
    }

    private static RecordHeader header(String name, String value) {
        return new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private record Claim(UUID messageId, UUID executionId, UUID engineId, String topic, String messageType, String payload) {
        static Claim from(DispatcherEventOutbox event) {
            return new Claim(event.getMessageId(), event.getExecutionId(), event.getEngineId(),
                    event.getTopic(), event.getMessageType(), event.getPayload());
        }
    }
}
