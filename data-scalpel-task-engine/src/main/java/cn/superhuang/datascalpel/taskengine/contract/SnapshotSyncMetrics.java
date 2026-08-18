package cn.superhuang.datascalpel.taskengine.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record SnapshotSyncMetrics(
        long sourceRows,
        long targetRows,
        long insertedRows,
        long updatedRows,
        long deletedRows,
        long unchangedRows,
        long retainedTargetOnlyRows
) implements NodeExecutionMetrics {
    public SnapshotSyncMetrics {
        if (sourceRows < 0 || targetRows < 0 || insertedRows < 0 || updatedRows < 0
                || deletedRows < 0 || unchangedRows < 0 || retainedTargetOnlyRows < 0) {
            throw new IllegalArgumentException("Snapshot Sync metrics must be non-negative");
        }
        if (sourceRows != insertedRows + updatedRows + unchangedRows
                || targetRows != deletedRows + retainedTargetOnlyRows + updatedRows + unchangedRows) {
            throw new IllegalArgumentException("Snapshot Sync metrics are inconsistent");
        }
    }

    @JsonIgnore
    public long rowsWritten() {
        return insertedRows + updatedRows + deletedRows;
    }
}
