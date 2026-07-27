package cn.superhuang.data.scalpel.contract.task;

public record FileDatasetInputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        FileDatasetInputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.FILE_DATASET_INPUT;
    }
}
