package cn.superhuang.data.scalpel.contract.task;

public record GeometryValidateNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        GeometryValidateConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_VALIDATE;
    }
}
