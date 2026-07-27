package cn.superhuang.data.scalpel.business.task.execution.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class KafkaInvalidExecutionMessagePublisher implements InvalidExecutionMessagePublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ExecutionKafkaProperties properties;

    public KafkaInvalidExecutionMessagePublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ExecutionKafkaProperties properties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(ConsumerRecord<String, String> source, String safeReason) {
        ProducerRecord<String, String> invalid = new ProducerRecord<>(
                properties.invalidMessageTopic(), source.key(), source.value());
        invalid.headers().add(new RecordHeader(
                "datascalpel-invalid-reason", safeReason.getBytes(StandardCharsets.UTF_8)));
        invalid.headers().add(new RecordHeader(
                "datascalpel-source-topic", source.topic().getBytes(StandardCharsets.UTF_8)));
        try {
            kafkaTemplate.send(invalid).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("发送无效执行消息时被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("无法确认无效执行消息已写入 Kafka", exception);
        }
    }
}
