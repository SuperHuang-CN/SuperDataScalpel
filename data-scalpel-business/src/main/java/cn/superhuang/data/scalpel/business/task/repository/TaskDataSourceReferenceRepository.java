package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.TaskDataSourceReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskDataSourceReferenceRepository extends JpaRepository<TaskDataSourceReference, UUID> {

    boolean existsByDataSourceId(UUID dataSourceId);

    List<TaskDataSourceReference> findAllByTaskIdOrderByLocationKeyAsc(UUID taskId);

    List<TaskDataSourceReference> findAllByTaskIdInAndDataSourceIdOrderByTaskIdAscLocationKeyAsc(
            Collection<UUID> taskIds,
            UUID dataSourceId
    );

    @Modifying
    @Query("delete from TaskDataSourceReference item where item.taskId = :taskId")
    void deleteAllByTaskId(@Param("taskId") UUID taskId);
}
