package cn.superhuang.data.scalpel.business.lineage.repository;

import cn.superhuang.data.scalpel.business.lineage.domain.TaskLineageFieldEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskLineageFieldEdgeRepository extends JpaRepository<TaskLineageFieldEdge, UUID> {
    List<TaskLineageFieldEdge> findAllBySnapshotIdIn(Collection<UUID> snapshotIds);
}
