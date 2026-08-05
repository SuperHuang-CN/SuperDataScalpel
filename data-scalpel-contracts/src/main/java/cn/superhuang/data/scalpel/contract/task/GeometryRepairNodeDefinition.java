package cn.superhuang.data.scalpel.contract.task;

public record GeometryRepairNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        GeometryRepairConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_REPAIR;
    }
}
