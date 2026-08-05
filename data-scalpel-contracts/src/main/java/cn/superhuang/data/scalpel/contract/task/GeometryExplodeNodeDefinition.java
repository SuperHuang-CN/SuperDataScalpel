package cn.superhuang.data.scalpel.contract.task;

public record GeometryExplodeNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        GeometryExplodeConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_EXPLODE;
    }
}
