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
        ExecutionCommand command = codec.readCommand(record.value());
        DispatcherKafkaRecordSupport.validate(record, command);
        service.accept(command, new MessageCoordinates(record.topic(), record.partition(), record.offset()));
    }
}
