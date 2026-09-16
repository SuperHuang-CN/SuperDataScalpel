package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CanvasTaskDefinitionRepository extends JpaRepository<CanvasTaskDefinition, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from CanvasTaskDefinition d where d.taskId = :taskId")
    Optional<CanvasTaskDefinition> findByTaskIdForUpdate(UUID taskId);

    @org.springframework.data.jpa.repository.Query("select d.taskId from CanvasTaskDefinition d order by d.taskId")
    org.springframework.data.domain.Slice<UUID> findTaskIds(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select count(d) > 0 from CanvasTaskDefinition d "
            + "where d.fileReferenceVersion is null or d.fileReferenceVersion <> d.version")
    boolean existsUnindexedFileReferences();

    Optional<CanvasTaskDefinition> findByTaskId(UUID taskId);

    List<CanvasTaskDefinition> findAllByTaskIdIn(Collection<UUID> taskIds);
}
