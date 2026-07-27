package cn.superhuang.data.scalpel.business.task.execution.service;

import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionKafkaHeaders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class DispatcherExecutionEventListener {

    private final ExecutionMessageJsonCodec codec;
    private final DispatcherEventApplicationService applicationService;
    private final InvalidExecutionMessagePublisher invalidMessagePublisher;

    public DispatcherExecutionEventListener(
            ExecutionMessageJsonCodec codec,
            DispatcherEventApplicationService applicationService,
            InvalidExecutionMessagePublisher invalidMessagePublisher
    ) {
        this.codec = codec;
        this.applicationService = applicationService;
        this.invalidMessagePublisher = invalidMessagePublisher;
    }

    @KafkaListener(
            topics = "#{'${data-scalpel.execution.kafka.admin-event-topics:datascalpel.execution.event}'.split(',')}",
            groupId = "${data-scalpel.execution.kafka.consumer-group:data-scalpel-admin-execution-v1}"
    )
    public void receive(ConsumerRecord<String, String> record) {
        DispatcherExecutionEvent event;
        try {
            event = codec.readDispatcherEvent(record.value());
            if (!event.executionId().toString().equals(record.key())) {
                throw new IllegalArgumentException("Kafka Key 与 executionId 不一致");
            }
            ExecutionKafkaHeaders.requireMatch(event, identityHeaders(record));
        } catch (JacksonException | IllegalArgumentException exception) {
            invalidMessagePublisher.publish(record, safeReason(exception));
            return;
        }

        // Database and other infrastructure failures must escape the listener so that
        // the Kafka container does not commit the offset and can retry the valid event.
        applicationService.accept(event);
    }

    private static Map<String, String> identityHeaders(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(ExecutionKafkaHeaders.MESSAGE_VERSION, headerValue(record, ExecutionKafkaHeaders.MESSAGE_VERSION));
        headers.put(ExecutionKafkaHeaders.MESSAGE_TYPE, headerValue(record, ExecutionKafkaHeaders.MESSAGE_TYPE));
        headers.put(ExecutionKafkaHeaders.ENGINE_ID, headerValue(record, ExecutionKafkaHeaders.ENGINE_ID));
        return Map.copyOf(headers);
    }

    private static String headerValue(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String safeReason(Throwable error) {
        String value = error.getMessage() == null ? "执行事件无法解析" : error.getMessage();
        value = value.replaceAll("(?i)(password|secret|token)=[^\\s,;]+", "$1=***");
        return value.substring(0, Math.min(500, value.length()));
    }
}
