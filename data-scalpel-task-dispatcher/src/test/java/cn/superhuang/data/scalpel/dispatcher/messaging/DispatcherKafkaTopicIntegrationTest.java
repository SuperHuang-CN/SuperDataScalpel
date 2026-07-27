package cn.superhuang.data.scalpel.dispatcher.messaging;

import cn.superhuang.data.scalpel.dispatcher.management.DispatcherTopics;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "DATASCALPEL_KAFKA_INTEGRATION", matches = "true")
class DispatcherKafkaTopicIntegrationTest {

    @Test
    void ensuresPreviouslyMissingTopicsOnTheConfiguredKafkaCluster() throws Exception {
        String bootstrapServers = requiredEnvironment("DATASCALPEL_KAFKA_BOOTSTRAP_SERVERS");
        String suffix = UUID.randomUUID().toString();
        DispatcherTopics topics = new DispatcherTopics(
                "datascalpel.execution.command.it." + suffix,
                "datascalpel.runner.event.it." + suffix,
                "datascalpel.execution.event.it." + suffix
        );
        Map<String, Object> adminConfiguration = Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) Duration.ofSeconds(15).toMillis(),
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, (int) Duration.ofSeconds(10).toMillis()
        );
        Map<String, Object> producerConfiguration = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
        DefaultKafkaProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(producerConfiguration);
        KafkaAdmin kafkaAdmin = new KafkaAdmin(adminConfiguration);
        DispatcherKafkaListenerManager manager = new DispatcherKafkaListenerManager(
                null, null, null, null, kafkaAdmin, new KafkaTemplate<>(producerFactory), true);
        List<String> names = List.of(topics.commandTopic(), topics.runnerEventTopic(), topics.adminEventTopic());

        try {
            assertThat(manager.readiness(topics).ready()).isTrue();
            try (Admin admin = Admin.create(adminConfiguration)) {
                assertThat(admin.describeTopics(names).allTopicNames().get(15, TimeUnit.SECONDS).keySet())
                        .containsExactlyInAnyOrderElementsOf(names);
            }
        } finally {
            producerFactory.destroy();
            try (Admin admin = Admin.create(adminConfiguration)) {
                admin.deleteTopics(names).all().get(15, TimeUnit.SECONDS);
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 未配置");
        }
        return value.trim();
    }
}
