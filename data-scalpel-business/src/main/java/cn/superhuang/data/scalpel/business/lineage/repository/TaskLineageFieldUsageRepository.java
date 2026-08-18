package cn.superhuang.data.scalpel.business.lineage.repository;

import cn.superhuang.data.scalpel.business.lineage.domain.TaskLineageFieldUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskLineageFieldUsageRepository extends JpaRepository<TaskLineageFieldUsage, UUID> {
    List<TaskLineageFieldUsage> findAllBySnapshotIdIn(Collection<UUID> snapshotIds);
}
