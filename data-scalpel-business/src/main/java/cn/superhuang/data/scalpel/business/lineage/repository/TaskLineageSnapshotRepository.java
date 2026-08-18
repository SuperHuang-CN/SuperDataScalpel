package cn.superhuang.data.scalpel.business.lineage.repository;

import cn.superhuang.data.scalpel.business.lineage.domain.TaskLineageSnapshot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskLineageSnapshotRepository extends JpaRepository<TaskLineageSnapshot, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select snapshot from TaskLineageSnapshot snapshot where snapshot.taskId = :taskId and snapshot.retiredAt is null")
    List<TaskLineageSnapshot> findCurrentByTaskIdForUpdate(@Param("taskId") UUID taskId);

    Optional<TaskLineageSnapshot> findFirstByTaskIdAndDefinitionVersionAndContentSha256OrderByGenerationDesc(
            UUID taskId,
            int definitionVersion,
            String contentSha256
    );

    @Query("select coalesce(max(snapshot.generation), 0) from TaskLineageSnapshot snapshot where snapshot.taskId = :taskId and snapshot.definitionVersion = :definitionVersion")
    int findMaximumGeneration(
            @Param("taskId") UUID taskId,
            @Param("definitionVersion") int definitionVersion
    );

    List<TaskLineageSnapshot> findAllByIdIn(Collection<UUID> ids);

    Optional<TaskLineageSnapshot> findFirstByTaskIdAndRetiredAtIsNullOrderByGenerationDesc(UUID taskId);
}
