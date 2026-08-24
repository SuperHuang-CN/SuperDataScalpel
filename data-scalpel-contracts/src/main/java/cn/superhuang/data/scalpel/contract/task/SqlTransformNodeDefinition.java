package cn.superhuang.data.scalpel.contract.task;

public record SqlTransformNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        SqlTransformConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SQL_TRANSFORM;
    }
}
