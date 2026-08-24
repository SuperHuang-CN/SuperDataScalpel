package cn.superhuang.data.scalpel.business.lineage.repository;

import cn.superhuang.data.scalpel.business.lineage.domain.TaskLineageFieldUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskLineageFieldUsageRepository extends JpaRepository<TaskLineageFieldUsage, UUID> {
    List<TaskLineageFieldUsage> findAllBySnapshotIdIn(Collection<UUID> snapshotIds);

    List<TaskLineageFieldUsage> findAllByAssetFieldIdIn(Collection<UUID> assetFieldIds);

    List<TaskLineageFieldUsage> findAllByAssetFieldIdIn(Collection<UUID> assetFieldIds, Pageable pageable);

    List<TaskLineageFieldUsage> findAllBySnapshotIdAndFlowKey(UUID snapshotId, String flowKey);

    List<TaskLineageFieldUsage> findAllBySnapshotIdAndFlowKey(UUID snapshotId, String flowKey, Pageable pageable);
}
