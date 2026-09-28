package cn.superhuang.data.scalpel.dispatcher.messaging;

import cn.superhuang.data.scalpel.contract.execution.*;
import cn.superhuang.data.scalpel.dispatcher.messaging.command.DispatcherCommandRecordReceiver;
import cn.superhuang.data.scalpel.dispatcher.messaging.command.DispatcherCommandService;
import cn.superhuang.data.scalpel.dispatcher.messaging.runner.DispatcherRunnerEventService;
import cn.superhuang.data.scalpel.dispatcher.messaging.runner.DispatcherRunnerRecordReceiver;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DispatcherEngineRoutingTest {
    private final DispatcherMessageJsonCodec codec = mock(DispatcherMessageJsonCodec.class);

    @Test void validCommandOnAnotherEnginesListenerCannotReachExecutionService() {
        var engine = UUID.randomUUID();
        var run = UUID.randomUUID();
        var prefix = "task-runs/" + run + "/attempts/1/";
        var command = new SubmitExecutionCommand(1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION,
                Instant.now(), engine, UUID.randomUUID(), run, 1, UUID.randomUUID(), ExecutionTaskType.SPARK_CANVAS,
                1, Instant.now().plusSeconds(60), new ExecutionArtifactLocation(prefix + "manifest.json",
                "a".repeat(64), prefix + "result.json", prefix + "console.log"));
        var service = mock(DispatcherCommandService.class);
        var receiver = new DispatcherCommandRecordReceiver(codec, service);
        when(codec.readCommand("payload")).thenReturn(command);
        var record = record("command.target", command);
        assertThatThrownBy(() -> receiver.receive(record, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Topic");
        verifyNoInteractions(service);
        receiver.receive(record, engine);
        verify(service).accept(command, new MessageCoordinates("command.target", 0, 7));
    }

    @Test void validRunnerEventOnAnotherEnginesListenerCannotUpdateExecution() {
        var engine = UUID.randomUUID();
        var event = new RunnerStartedEvent(1, UUID.randomUUID(), ExecutionMessageType.RUNNER_STARTED,
                Instant.now(), engine, UUID.randomUUID(), UUID.randomUUID(), 1, "spark-test");
        var service = mock(DispatcherRunnerEventService.class);
        var receiver = new DispatcherRunnerRecordReceiver(codec, service);
        when(codec.readRunnerEvent("payload")).thenReturn(event);
        var record = record("runner.target", event);
        assertThatThrownBy(() -> receiver.receive(record, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Topic");
        verifyNoInteractions(service);
        receiver.receive(record, engine);
        verify(service).accept(event, new MessageCoordinates("runner.target", 0, 7));
    }

    private static ConsumerRecord<String, String> record(String topic, ExecutionMessageEnvelope message) {
        var record = new ConsumerRecord<String, String>(topic, 0, 7, message.executionId().toString(), "payload");
        ExecutionKafkaHeaders.from(message).forEach((key, value) -> record.headers().add(key, value.getBytes(StandardCharsets.UTF_8)));
        return record;
    }

    @Test void sharedTopicRoutesDifferentEnginesButRejectsForeignAndWrongTopics() {
        var registrations = mock(cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository.class);
        var service = mock(DispatcherRunnerEventService.class);
        var receiver = new DispatcherRunnerRecordReceiver(codec, service);
        for (int i = 0; i < 2; i++) {
            var engine = UUID.randomUUID();
            var event = new RunnerStartedEvent(1, UUID.randomUUID(), ExecutionMessageType.RUNNER_STARTED,
                    Instant.now(), engine, UUID.randomUUID(), UUID.randomUUID(), 1, "spark-test");
            when(codec.readRunnerEvent("payload")).thenReturn(event);
            var registration = mock(cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration.class);
            when(registration.getRunnerEventTopic()).thenReturn("runner.shared");
            when(registrations.findByEngineId(engine)).thenReturn(java.util.Optional.of(registration));
            receiver.receiveShared(record("runner.shared", event), registrations);
            verify(service).accept(event, new MessageCoordinates("runner.shared", 0, 7));
            assertThatThrownBy(() -> receiver.receiveShared(record("runner.other", event), registrations)).isInstanceOf(IllegalArgumentException.class);
            when(registrations.findByEngineId(engine)).thenReturn(java.util.Optional.empty());
            assertThatThrownBy(() -> receiver.receiveShared(record("runner.shared", event), registrations)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoMoreInteractions(service);
    }
}
