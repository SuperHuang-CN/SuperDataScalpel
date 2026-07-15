package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.search.SearchRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TaskRunRepository extends SearchRepository<TaskRun, UUID> {

    boolean existsByTaskId(UUID taskId);

    boolean existsByTaskIdAndStatusIn(UUID taskId, Collection<TaskRunStatus> statuses);

    List<TaskRun> findAllByStatusIn(Collection<TaskRunStatus> statuses);
}
