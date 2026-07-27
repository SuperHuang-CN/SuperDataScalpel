package cn.superhuang.data.scalpel.dispatcher.messaging;

import cn.superhuang.data.scalpel.dispatcher.management.DispatcherTopics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DispatcherKafkaListenerManagerTest {

    @Test
    void plansExecutionAndRunnerControlTopicsWithLocalSafeDefaults() {
        var topics = DispatcherKafkaListenerManager.topicsToEnsure(
                new DispatcherTopics("command", "runner-event", "admin-event"));

        assertThat(topics).extracting(topic -> topic.name())
                .containsExactly("command", "runner-event", "admin-event", "runner-event.control");
        assertThat(topics).allSatisfy(topic -> {
            assertThat(topic.numPartitions()).isEqualTo(1);
            assertThat(topic.replicationFactor()).isEqualTo((short) 1);
        });
    }
}
