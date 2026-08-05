package cn.superhuang.data.scalpel.contract.task;

public record DeduplicateNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        DeduplicateConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.DEDUPLICATE;
    }
}
