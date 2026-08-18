package cn.superhuang.datascalpel.taskengine.contract;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SnapshotSyncMetrics.class, name = "SNAPSHOT_SYNC")
})
public sealed interface NodeExecutionMetrics permits SnapshotSyncMetrics {
}
