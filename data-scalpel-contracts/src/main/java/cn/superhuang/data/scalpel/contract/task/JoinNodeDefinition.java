package cn.superhuang.data.scalpel.contract.task;

public record JoinNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        JoinConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JOIN;
    }
}
