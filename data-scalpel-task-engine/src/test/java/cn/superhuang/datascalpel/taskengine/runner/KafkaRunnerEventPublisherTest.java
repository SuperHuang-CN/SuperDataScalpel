package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionKafkaHeaders;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.RunnerEventChannel;
import cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerKafkaSecurityProtocol;
import cn.superhuang.data.scalpel.contract.execution.RunnerStartedEvent;
import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class KafkaRunnerEventPublisherTest {

    @Test
    void publishesStrictJsonWithExecutionKeyAndStableHeaders() throws Exception {
        UUID engineId = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID executionId = UUID.fromString("20000000-0000-0000-0000-000000000002");
        RunnerStartedEvent event = new RunnerStartedEvent(
                1,
                UUID.fromString("30000000-0000-0000-0000-000000000003"),
                ExecutionMessageType.RUNNER_STARTED,
                Instant.parse("2026-07-17T12:00:00Z"),
                engineId,
                executionId,
                UUID.fromString("40000000-0000-0000-0000-000000000004"),
                1,
                "local-test"
        );
        RunnerEventChannel channel = new RunnerEventChannel(
                "kafka:9092", "datascalpel.runner.local", RunnerKafkaSecurityProtocol.PLAINTEXT,
                "runner-" + executionId);
        MockProducer<String, String> producer = new MockProducer<>(
                true, null, new StringSerializer(), new StringSerializer());
        var objectMapper = JsonSupport.strictObjectMapper();

        try (KafkaRunnerEventPublisher publisher = new KafkaRunnerEventPublisher(channel, objectMapper, producer)) {
            publisher.publish(event);
        }

        assertEquals(1, producer.history().size());
        ProducerRecord<String, String> record = producer.history().getFirst();
        assertEquals(channel.topic(), record.topic());
        assertEquals(executionId.toString(), record.key());
        Map<String, String> headers = new HashMap<>();
        for (Header header : record.headers()) {
            headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
        }
        assertEquals(Map.of(
                ExecutionKafkaHeaders.MESSAGE_VERSION, "1",
                ExecutionKafkaHeaders.MESSAGE_TYPE, ExecutionMessageType.RUNNER_STARTED.name(),
                ExecutionKafkaHeaders.ENGINE_ID, engineId.toString()
        ), headers);

        RunnerExecutionEvent decoded = objectMapper.readValue(record.value(), RunnerExecutionEvent.class);
        assertInstanceOf(RunnerStartedEvent.class, decoded);
        assertEquals(event, decoded);
    }
}
