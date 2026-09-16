package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.TaskCanvasFileReference;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.UUID;

public interface TaskCanvasFileReferenceRepository extends JpaRepository<TaskCanvasFileReference, UUID> {
    void deleteAllByTaskId(UUID taskId);
    boolean existsByFileTableIdIn(Collection<UUID> ids);

    @Query("select count(r) > 0 from TaskCanvasFileReference r, DataTask t "
            + "where r.taskId = t.id and r.fileTableId in :ids and t.status in :statuses")
    boolean existsProtectedReference(Collection<UUID> ids, Collection<TaskStatus> statuses);
}
