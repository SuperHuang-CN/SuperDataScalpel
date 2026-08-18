package cn.superhuang.data.scalpel.contract.task;

public record JdbcSnapshotSyncOutputNodeDefinition(
        String id,
        String name,
        CanvasNodeLayout layout,
        JdbcSnapshotSyncOutputConfiguration configuration
) implements CanvasNodeDefinition {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.JDBC_SNAPSHOT_SYNC_OUTPUT;
    }
}
