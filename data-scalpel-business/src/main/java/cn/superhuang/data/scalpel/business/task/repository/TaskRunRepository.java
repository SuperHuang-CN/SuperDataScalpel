package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunJarCleanupStatus;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRunRepository extends SearchRepository<TaskRun, UUID> {

    boolean existsByTaskId(UUID taskId);

    boolean existsByTaskIdAndStatusIn(UUID taskId, Collection<TaskRunStatus> statuses);

    boolean existsByComputeEngineIdAndStatusIn(UUID computeEngineId, Collection<TaskRunStatus> statuses);

    Optional<TaskRun> findByScheduleIdAndScheduledFireAt(UUID scheduleId, Instant scheduledFireAt);

    Optional<TaskRun> findFirstByStreamingDeploymentIdOrderByAttemptDesc(UUID streamingDeploymentId);

    List<TaskRun> findAllByStatusIn(Collection<TaskRunStatus> statuses);

    List<TaskRun> findAllByTaskTypeAndStatusIn(TaskType taskType, Collection<TaskRunStatus> statuses);

    List<TaskRun> findAllByTaskTypeAndUserJarCleanupStatusAndStatusIn(
            TaskType taskType, TaskRunJarCleanupStatus cleanupStatus, Collection<TaskRunStatus> statuses);

    List<TaskRun> findAllByTaskTypeInAndUserJarCleanupStatusAndStatusIn(
            Collection<TaskType> taskTypes, TaskRunJarCleanupStatus cleanupStatus,
            Collection<TaskRunStatus> statuses);

    Optional<TaskRun> findFirstByQualityTargetModelIdOrderByQueuedAtDesc(UUID modelId);

    Optional<TaskRun> findFirstByQualityTargetModelIdAndStatusAndQualityConclusionIsNotNullAndQualityRuleSnapshotAtIsNotNullOrderByEndedAtDesc(
            UUID modelId,
            TaskRunStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from TaskRun run where run.id = :id")
    Optional<TaskRun> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from TaskRun run where run.executionRunId = :runId")
    Optional<TaskRun> findByExecutionRunIdForUpdate(UUID runId);
}
