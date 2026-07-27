package cn.superhuang.data.scalpel.dispatcher.messaging;

public record MessageCoordinates(String topic, int partition, long offset) {
    public MessageCoordinates {
        if (topic == null || topic.isBlank() || partition < 0 || offset < 0) {
            throw new IllegalArgumentException("Kafka 消息坐标无效");
        }
    }
}
