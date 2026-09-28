package cn.superhuang.data.scalpel.dispatcher.messaging.runner;

import cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent;
import cn.superhuang.data.scalpel.dispatcher.messaging.DispatcherKafkaRecordSupport;
import cn.superhuang.data.scalpel.dispatcher.messaging.DispatcherMessageJsonCodec;
import cn.superhuang.data.scalpel.dispatcher.messaging.MessageCoordinates;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
public class DispatcherRunnerRecordReceiver {
    private final DispatcherMessageJsonCodec codec;
    private final DispatcherRunnerEventService service;

    public DispatcherRunnerRecordReceiver(DispatcherMessageJsonCodec codec, DispatcherRunnerEventService service) {
        this.codec = codec;
        this.service = service;
    }

    public void receive(ConsumerRecord<String, String> record) {
        receive(record, null);
    }

    public void receiveShared(ConsumerRecord<String, String> record,
            cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository registrations) {
        RunnerExecutionEvent event = codec.readRunnerEvent(record.value());
        DispatcherKafkaRecordSupport.validate(record, event);
        var registration = registrations.findByEngineId(event.engineId())
                .orElseThrow(() -> new IllegalArgumentException("Runner 引擎不属于此 Dispatcher"));
        if (!record.topic().equals(registration.getRunnerEventTopic())) {
            throw new IllegalArgumentException("Runner Topic 与引擎注册不一致");
        }
        service.accept(event, new MessageCoordinates(record.topic(), record.partition(), record.offset()));
    }

    public void receive(ConsumerRecord<String, String> record, java.util.UUID expectedEngineId) {
        RunnerExecutionEvent event = codec.readRunnerEvent(record.value());
        DispatcherKafkaRecordSupport.validate(record, event);
        if (expectedEngineId != null && !expectedEngineId.equals(event.engineId())) {
            throw new IllegalArgumentException("Runner 引擎身份与 Topic 所属引擎不一致");
        }
        service.accept(event, new MessageCoordinates(record.topic(), record.partition(), record.offset()));
    }
}
