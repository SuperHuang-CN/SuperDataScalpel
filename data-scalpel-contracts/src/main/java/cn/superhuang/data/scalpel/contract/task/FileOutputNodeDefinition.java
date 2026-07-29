package cn.superhuang.data.scalpel.contract.task;

public record FileOutputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        FileOutputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.FILE_OUTPUT;
    }
}
