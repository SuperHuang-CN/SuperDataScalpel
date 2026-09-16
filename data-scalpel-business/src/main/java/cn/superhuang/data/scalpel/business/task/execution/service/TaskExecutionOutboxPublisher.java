package cn.superhuang.data.scalpel.business.task.execution.service;

import cn.superhuang.data.scalpel.business.task.execution.domain.TaskExecutionOutboxMessage;
import cn.superhuang.data.scalpel.business.task.execution.domain.TaskExecutionOutboxState;
import cn.superhuang.data.scalpel.business.task.execution.repository.TaskExecutionOutboxRepository;
import cn.superhuang.data.scalpel.contract.execution.ExecutionKafkaHeaders;
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
public class TaskExecutionOutboxPublisher {

    private final TaskExecutionOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ExecutionKafkaProperties properties;
    private final TransactionTemplate transactionTemplate;

    public TaskExecutionOutboxPublisher(
            TaskExecutionOutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            ExecutionKafkaProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(scheduler = "executionOutboxScheduler", fixedDelayString = "${data-scalpel.execution.kafka.outbox-poll-interval:500ms}")
    public void publishDue() {
        recoverStaleClaims();
        List<ClaimedMessage> claimed = transactionTemplate.execute(status -> repository.findDueForUpdate(
                        List.of(TaskExecutionOutboxState.PENDING, TaskExecutionOutboxState.FAILED),
                        Instant.now(), PageRequest.of(0, properties.outboxBatchSize())
                ).stream().map(message -> {
                    message.claim(Instant.now());
                    repository.save(message);
                    return ClaimedMessage.from(message);
                }).toList());
        if (claimed == null) return;
        for (ClaimedMessage message : claimed) {
            publish(message);
        }
    }

    private void recoverStaleClaims() {
        transactionTemplate.executeWithoutResult(status -> repository.findStaleClaimsForUpdate(
                Instant.now().minus(properties.claimTimeout()),
                PageRequest.of(0, properties.outboxBatchSize())
        ).forEach(message -> {
            message.recoverStaleClaim(Instant.now(), properties.claimTimeout());
            repository.save(message);
        }));
    }

    private void publish(ClaimedMessage message) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    message.topic(), null, message.executionId().toString(), message.payload(), List.of(
                    header(ExecutionKafkaHeaders.MESSAGE_VERSION, "1"),
                    header(ExecutionKafkaHeaders.MESSAGE_TYPE, message.messageType()),
                    header(ExecutionKafkaHeaders.ENGINE_ID, message.engineId().toString())
            ));
            kafkaTemplate.send(record).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            transactionTemplate.executeWithoutResult(status -> repository.findByMessageId(message.messageId()).ifPresent(entity -> {
                if (entity.getState() == TaskExecutionOutboxState.PUBLISHING) {
                    entity.published(Instant.now());
                    repository.save(entity);
                }
            }));
        } catch (Exception exception) {
            transactionTemplate.executeWithoutResult(status -> repository.findByMessageId(message.messageId()).ifPresent(entity -> {
                if (entity.getState() == TaskExecutionOutboxState.PUBLISHING) {
                    entity.failed(Instant.now(), exception.getMessage());
                    repository.save(entity);
                }
            }));
        }
    }

    private static RecordHeader header(String name, String value) {
        return new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private record ClaimedMessage(
            UUID messageId,
            UUID executionId,
            UUID engineId,
            String topic,
            String messageType,
            String payload
    ) {
        static ClaimedMessage from(TaskExecutionOutboxMessage message) {
            return new ClaimedMessage(
                    message.getMessageId(), message.getExecutionId(), message.getEngineId(),
                    message.getTopic(), message.getMessageType(), message.getPayload()
            );
        }
    }
}
