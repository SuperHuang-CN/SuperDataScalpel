package cn.superhuang.data.scalpel.contract.task;

public record StreamJoinNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        StreamJoinConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.STREAM_JOIN;
    }
}
