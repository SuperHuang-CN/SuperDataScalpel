package cn.superhuang.data.scalpel.contract.task;

public record GeometrySimplifyNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        GeometrySimplifyConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_SIMPLIFY;
    }
}
