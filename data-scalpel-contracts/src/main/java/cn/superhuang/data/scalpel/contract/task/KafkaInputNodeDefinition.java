package cn.superhuang.data.scalpel.contract.task;

public record KafkaInputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        KafkaInputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.KAFKA_INPUT;
    }
}
