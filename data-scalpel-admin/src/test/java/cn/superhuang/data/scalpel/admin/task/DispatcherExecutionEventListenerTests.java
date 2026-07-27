package cn.superhuang.data.scalpel.admin.task;

import cn.superhuang.data.scalpel.business.task.execution.service.DispatcherEventApplicationService;
import cn.superhuang.data.scalpel.business.task.execution.service.DispatcherExecutionEventListener;
import cn.superhuang.data.scalpel.business.task.execution.service.ExecutionMessageJsonCodec;
import cn.superhuang.data.scalpel.business.task.execution.service.InvalidExecutionMessagePublisher;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionKafkaHeaders;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DispatcherExecutionEventListenerTests {

    private final ExecutionMessageJsonCodec codec = new ExecutionMessageJsonCodec(new ObjectMapper());

    @Test
    void rethrowsDatabaseFailureSoKafkaCanRetryValidEvent() {
        DispatcherExecutionEvent event = event();
        ConsumerRecord<String, String> record = validRecord(event, codec.write(event));
        StubApplicationService application = new StubApplicationService();
        application.failure = new DataAccessResourceFailureException("database unavailable");
        StubInvalidPublisher invalid = new StubInvalidPublisher();
        DispatcherExecutionEventListener listener = new DispatcherExecutionEventListener(codec, application, invalid);

        assertThatThrownBy(() -> listener.receive(record))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(application.calls).isEqualTo(1);
        assertThat(invalid.calls).isZero();
    }

    @Test
    void sendsProtocolInvalidMessageToDeadLetterPublisher() {
        StubApplicationService application = new StubApplicationService();
        StubInvalidPublisher invalid = new StubInvalidPublisher();
        DispatcherExecutionEventListener listener = new DispatcherExecutionEventListener(codec, application, invalid);

        listener.receive(new ConsumerRecord<>("admin.events", 0, 1, "bad-key", "invalid-payload"));

        assertThat(invalid.calls).isEqualTo(1);
        assertThat(invalid.reason).isNotBlank();
        assertThat(application.calls).isZero();
    }

    @Test
    void rethrowsWhenDeadLetterPublicationCannotBeConfirmed() {
        StubInvalidPublisher invalid = new StubInvalidPublisher();
        invalid.failure = new IllegalStateException("broker unavailable");
        DispatcherExecutionEventListener listener = new DispatcherExecutionEventListener(
                codec, new StubApplicationService(), invalid);

        assertThatThrownBy(() -> listener.receive(
                new ConsumerRecord<>("admin.events", 0, 2, "bad-key", "invalid-payload")))
                .isSameAs(invalid.failure);
    }

    private static DispatcherExecutionEvent event() {
        return new DispatcherExecutionEvent(
                1, UUID.randomUUID(), ExecutionMessageType.EXECUTION_RUNNING, Instant.now(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 1,
                ExecutionBackendType.LOCAL_DOCKER, "container", null,
                Instant.now(), null, null, null);
    }

    private static ConsumerRecord<String, String> validRecord(DispatcherExecutionEvent event, String payload) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "admin.events", 0, 0, event.executionId().toString(), payload);
        addHeader(record, ExecutionKafkaHeaders.MESSAGE_VERSION, Integer.toString(event.messageVersion()));
        addHeader(record, ExecutionKafkaHeaders.MESSAGE_TYPE, event.messageType().name());
        addHeader(record, ExecutionKafkaHeaders.ENGINE_ID, event.engineId().toString());
        return record;
    }

    private static void addHeader(ConsumerRecord<String, String> record, String name, String value) {
        record.headers().add(new RecordHeader(name, value.getBytes(StandardCharsets.UTF_8)));
    }

    private static final class StubApplicationService extends DispatcherEventApplicationService {
        private int calls;
        private RuntimeException failure;

        private StubApplicationService() { super(null, null, null, null); }

        @Override
        public Outcome accept(DispatcherExecutionEvent event) {
            calls++;
            if (failure != null) throw failure;
            return Outcome.APPLIED;
        }
    }

    private static final class StubInvalidPublisher implements InvalidExecutionMessagePublisher {
        private int calls;
        private String reason;
        private RuntimeException failure;

        @Override
        public void publish(ConsumerRecord<String, String> source, String safeReason) {
            calls++;
            reason = safeReason;
            if (failure != null) throw failure;
        }
    }
}
