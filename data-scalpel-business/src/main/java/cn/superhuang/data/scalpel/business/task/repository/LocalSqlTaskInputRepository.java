package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskInput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LocalSqlTaskInputRepository extends JpaRepository<LocalSqlTaskInput, UUID> {

    List<LocalSqlTaskInput> findAllByTaskIdOrderBySortOrderAsc(UUID taskId);

    List<LocalSqlTaskInput> findAllByTaskIdInAndModelIdOrderByTaskIdAscSortOrderAsc(
            Collection<UUID> taskIds,
            UUID modelId
    );

    boolean existsByModelId(UUID modelId);

    void deleteAllByTaskId(UUID taskId);
}
