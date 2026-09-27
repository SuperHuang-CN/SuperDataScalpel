package cn.superhuang.data.scalpel.dispatcher.messaging.command;

import cn.superhuang.data.scalpel.contract.execution.ExecutionCommand;
import cn.superhuang.data.scalpel.dispatcher.messaging.DispatcherKafkaRecordSupport;
import cn.superhuang.data.scalpel.dispatcher.messaging.DispatcherMessageJsonCodec;
import cn.superhuang.data.scalpel.dispatcher.messaging.MessageCoordinates;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
public class DispatcherCommandRecordReceiver {
    private final DispatcherMessageJsonCodec codec;
    private final DispatcherCommandService service;

    public DispatcherCommandRecordReceiver(DispatcherMessageJsonCodec codec, DispatcherCommandService service) {
        this.codec = codec;
        this.service = service;
    }

    public void receive(ConsumerRecord<String, String> record) {
        receive(record, null);
    }

    public void receiveShared(ConsumerRecord<String, String> record,
            cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository registrations) {
        ExecutionCommand command = codec.readCommand(record.value());
        DispatcherKafkaRecordSupport.validate(record, command);
        var registration = registrations.findByEngineId(command.engineId())
                .orElseThrow(() -> new IllegalArgumentException("命令引擎不属于此 Dispatcher"));
        if (!record.topic().equals(registration.getCommandTopic())) {
            throw new IllegalArgumentException("命令 Topic 与引擎注册不一致");
        }
        service.accept(command, new MessageCoordinates(record.topic(), record.partition(), record.offset()));
    }

    public void receive(ConsumerRecord<String, String> record, java.util.UUID expectedEngineId) {
        ExecutionCommand command = codec.readCommand(record.value());
        DispatcherKafkaRecordSupport.validate(record, command);
        if (expectedEngineId != null && !expectedEngineId.equals(command.engineId())) {
            throw new IllegalArgumentException("命令引擎身份与 Topic 所属引擎不一致");
        }
        service.accept(command, new MessageCoordinates(record.topic(), record.partition(), record.offset()));
    }
}
