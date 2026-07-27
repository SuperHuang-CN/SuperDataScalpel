package cn.superhuang.data.scalpel.dispatcher.messaging;

import cn.superhuang.data.scalpel.contract.execution.ExecutionKafkaHeaders;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageEnvelope;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DispatcherKafkaRecordSupport {
    private DispatcherKafkaRecordSupport() { }

    public static void validate(ConsumerRecord<String, String> record, ExecutionMessageEnvelope message) {
        if (!message.executionId().toString().equals(record.key())) {
            throw new IllegalArgumentException("Kafka Key 与 executionId 不一致");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(ExecutionKafkaHeaders.MESSAGE_VERSION, value(record, ExecutionKafkaHeaders.MESSAGE_VERSION));
        headers.put(ExecutionKafkaHeaders.MESSAGE_TYPE, value(record, ExecutionKafkaHeaders.MESSAGE_TYPE));
        headers.put(ExecutionKafkaHeaders.ENGINE_ID, value(record, ExecutionKafkaHeaders.ENGINE_ID));
        ExecutionKafkaHeaders.requireMatch(message, Map.copyOf(headers));
    }

    private static String value(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
