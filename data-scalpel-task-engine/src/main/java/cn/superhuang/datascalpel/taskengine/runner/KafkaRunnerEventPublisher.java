package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionKafkaHeaders;
import cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.RunnerEventChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

final class KafkaRunnerEventPublisher implements RunnerEventPublisher {
    private static final int SEND_TIMEOUT_SECONDS = 120;

    private final RunnerEventChannel channel;
    private final ObjectMapper objectMapper;
    private final Producer<String, String> producer;

    KafkaRunnerEventPublisher(RunnerEventChannel channel, ObjectMapper objectMapper) {
        this(channel, objectMapper, new KafkaProducer<>(producerProperties(channel)));
    }

    KafkaRunnerEventPublisher(
            RunnerEventChannel channel,
            ObjectMapper objectMapper,
            Producer<String, String> producer
    ) {
        this.channel = channel;
        this.objectMapper = objectMapper;
        this.producer = producer;
    }

    @Override
    public void publish(RunnerExecutionEvent event) throws Exception {
        String payload = objectMapper.writeValueAsString(event);
        ProducerRecord<String, String> record = new ProducerRecord<>(
                channel.topic(), null, event.executionId().toString(), payload, null);
        for (Map.Entry<String, String> header : ExecutionKafkaHeaders.from(event).entrySet()) {
            record.headers().add(new RecordHeader(header.getKey(), header.getValue().getBytes(StandardCharsets.UTF_8)));
        }
        Exception last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                producer.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                producer.flush();
                return;
            } catch (Exception exception) {
                last = exception;
                if (attempt < 3) Thread.sleep(250L * attempt);
            }
        }
        throw last == null ? new IllegalStateException("Runner 事件发送失败") : last;
    }

    @Override
    public void close() {
        producer.close(Duration.ofSeconds(5));
    }

    private static Properties producerProperties(RunnerEventChannel channel) {
        Properties result = new Properties();
        result.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, channel.bootstrapServers());
        result.put(ProducerConfig.CLIENT_ID_CONFIG, channel.clientId());
        result.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        result.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        result.put(ProducerConfig.ACKS_CONFIG, "all");
        result.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        result.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
        result.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "120000");
        result.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "60000");
        result.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "120000");
        result.put(CommonClientConfigs.SOCKET_CONNECTION_SETUP_TIMEOUT_MS_CONFIG, "30000");
        result.put(CommonClientConfigs.SOCKET_CONNECTION_SETUP_TIMEOUT_MAX_MS_CONFIG, "120000");
        result.put("security.protocol", channel.securityProtocol().name());
        return result;
    }
}
