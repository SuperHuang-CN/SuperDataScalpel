package cn.superhuang.data.scalpel.contract.task;

public record KafkaOutputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        KafkaOutputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.KAFKA_OUTPUT;
    }
}
