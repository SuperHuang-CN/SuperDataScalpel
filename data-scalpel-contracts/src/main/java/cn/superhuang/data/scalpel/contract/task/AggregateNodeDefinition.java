package cn.superhuang.data.scalpel.contract.task;

public record AggregateNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        AggregateConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.AGGREGATE;
    }
}
