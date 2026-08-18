package cn.superhuang.data.scalpel.contract.task;

public record ModelSnapshotSyncOutputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        ModelSnapshotSyncOutputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.MODEL_SNAPSHOT_SYNC_OUTPUT;
    }
}
