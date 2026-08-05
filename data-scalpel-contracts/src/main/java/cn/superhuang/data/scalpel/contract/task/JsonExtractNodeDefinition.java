package cn.superhuang.data.scalpel.contract.task;

public record JsonExtractNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        JsonExtractConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JSON_EXTRACT;
    }
}
