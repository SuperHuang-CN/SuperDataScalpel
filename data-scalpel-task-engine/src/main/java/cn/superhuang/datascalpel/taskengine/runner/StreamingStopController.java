package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.RunnerControlChannel;
import cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

final class StreamingStopController implements AutoCloseable {
    private final ObjectMapper objectMapper;
    private final Consumer<String, String> consumer;
    private final UUID engineId;
    private final UUID executionId;
    private final UUID runId;
    private final int attempt;
    private final UUID deploymentId;

    StreamingStopController(
            RunnerControlChannel channel,
            ObjectMapper objectMapper,
            UUID engineId,
            UUID executionId,
            UUID runId,
            int attempt,
            UUID deploymentId
    ) {
        this(channel, objectMapper, engineId, executionId, runId, attempt, deploymentId,
                new KafkaConsumer<>(consumerProperties(channel)));
    }

    StreamingStopController(
            RunnerControlChannel channel,
            ObjectMapper objectMapper,
            UUID engineId,
            UUID executionId,
            UUID runId,
            int attempt,
            UUID deploymentId,
            Consumer<String, String> consumer
    ) {
        this.objectMapper = objectMapper;
        this.consumer = consumer;
        this.engineId = engineId;
        this.executionId = executionId;
        this.runId = runId;
        this.attempt = attempt;
        this.deploymentId = deploymentId;
        consumer.subscribe(List.of(channel.topic()));
    }

    StopStreamingExecutionCommand poll(Duration timeout) {
        ConsumerRecords<String, String> records = consumer.poll(timeout);
        StopStreamingExecutionCommand matching = null;
        for (ConsumerRecord<String, String> record : records) {
            StopStreamingExecutionCommand command;
            try {
                command = objectMapper.readValue(record.value(), StopStreamingExecutionCommand.class);
            } catch (Exception invalidMessage) {
                continue;
            }
            if (engineId.equals(command.engineId())
                    && executionId.equals(command.executionId())
                    && runId.equals(command.runId())
                    && attempt == command.attempt()
                    && deploymentId.equals(command.deploymentId())) {
                matching = command;
                break;
            }
        }
        if (!records.isEmpty()) consumer.commitSync();
        return matching;
    }

    @Override
    public void close() {
        consumer.close(Duration.ofSeconds(5));
    }

    private static Properties consumerProperties(RunnerControlChannel channel) {
        Properties result = new Properties();
        result.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, channel.bootstrapServers());
        result.put(ConsumerConfig.CLIENT_ID_CONFIG, channel.clientId());
        result.put(ConsumerConfig.GROUP_ID_CONFIG, channel.groupId());
        result.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        result.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        result.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        result.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        result.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        result.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "60000");
        result.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "120000");
        result.put(CommonClientConfigs.SOCKET_CONNECTION_SETUP_TIMEOUT_MS_CONFIG, "30000");
        result.put(CommonClientConfigs.SOCKET_CONNECTION_SETUP_TIMEOUT_MAX_MS_CONFIG, "120000");
        result.put("security.protocol", channel.securityProtocol().name());
        return result;
    }
}
