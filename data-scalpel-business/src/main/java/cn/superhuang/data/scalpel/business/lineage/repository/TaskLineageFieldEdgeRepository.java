package cn.superhuang.data.scalpel.business.lineage.repository;

import cn.superhuang.data.scalpel.business.lineage.domain.TaskLineageFieldEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskLineageFieldEdgeRepository extends JpaRepository<TaskLineageFieldEdge, UUID> {
    List<TaskLineageFieldEdge> findAllBySnapshotIdIn(Collection<UUID> snapshotIds);

    List<TaskLineageFieldEdge> findAllByTargetAssetFieldIdIn(Collection<UUID> targetFieldIds);

    List<TaskLineageFieldEdge> findAllBySourceAssetFieldIdIn(Collection<UUID> sourceFieldIds);

    List<TaskLineageFieldEdge> findAllByTargetAssetFieldIdIn(Collection<UUID> targetFieldIds, Pageable pageable);

    List<TaskLineageFieldEdge> findAllBySourceAssetFieldIdIn(Collection<UUID> sourceFieldIds, Pageable pageable);
}
