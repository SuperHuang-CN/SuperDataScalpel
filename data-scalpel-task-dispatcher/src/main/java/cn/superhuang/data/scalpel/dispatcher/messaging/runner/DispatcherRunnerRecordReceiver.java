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
        RunnerExecutionEvent event = codec.readRunnerEvent(record.value());
        DispatcherKafkaRecordSupport.validate(record, event);
        service.accept(event, new MessageCoordinates(record.topic(), record.partition(), record.offset()));
    }
}
