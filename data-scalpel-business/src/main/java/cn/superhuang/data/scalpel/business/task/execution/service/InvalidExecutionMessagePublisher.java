package cn.superhuang.data.scalpel.business.task.execution.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;

@FunctionalInterface
public interface InvalidExecutionMessagePublisher {
    void publish(ConsumerRecord<String, String> source, String safeReason);
}
