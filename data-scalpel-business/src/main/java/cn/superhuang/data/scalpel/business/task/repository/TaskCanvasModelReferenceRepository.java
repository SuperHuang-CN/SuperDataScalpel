package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.TaskCanvasModelReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskCanvasModelReferenceRepository extends JpaRepository<TaskCanvasModelReference, UUID> {

    List<TaskCanvasModelReference> findAllByTaskIdOrderByNodeId(UUID taskId);

    List<TaskCanvasModelReference> findAllByTaskIdInAndModelIdOrderByTaskIdAscNodeIdAsc(
            Collection<UUID> taskIds,
            UUID modelId
    );

    boolean existsByModelId(UUID modelId);

    @Modifying
    @Query("delete from TaskCanvasModelReference item where item.taskId = :taskId")
    void deleteAllByTaskId(@Param("taskId") UUID taskId);
}
