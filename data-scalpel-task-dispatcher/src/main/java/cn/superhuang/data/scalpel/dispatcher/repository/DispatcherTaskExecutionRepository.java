package cn.superhuang.data.scalpel.dispatcher.repository;

import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface DispatcherTaskExecutionRepository extends JpaRepository<DispatcherTaskExecution, UUID> {
    Optional<DispatcherTaskExecution> findByExecutionIdAndAttempt(UUID executionId, int attempt);
    Optional<DispatcherTaskExecution> findByExecutionId(UUID executionId);
    long countByState(DispatcherExecutionState state);
    long countByStateIn(Collection<DispatcherExecutionState> states);
    boolean existsByStateIn(Collection<DispatcherExecutionState> states);

    long countByEngineIdAndState(UUID engineId, DispatcherExecutionState state);
    long countByEngineIdAndStateIn(UUID engineId, Collection<DispatcherExecutionState> states);
    boolean existsByEngineIdAndStateIn(UUID engineId, Collection<DispatcherExecutionState> states);
    List<DispatcherTaskExecution> findAllByEngineIdAndStateIn(UUID engineId, Collection<DispatcherExecutionState> states);
    Page<DispatcherTaskExecution> findAllByEngineIdAndStateIn(UUID engineId, Collection<DispatcherExecutionState> states, Pageable pageable);
    Page<DispatcherTaskExecution> findAllByEngineIdAndState(UUID engineId, DispatcherExecutionState state, Pageable pageable);

    @Query("select count(e) from DispatcherTaskExecution e where e.engineId = :engineId "
            + "and e.state not in :active and e.externalCleanupCompleted = false and e.submissionStartedAt is not null "
            + "and (e.externalTerminationConfirmed is null or e.externalTerminationConfirmed = false)")
    long countUnconfirmedTerminalExecutions(UUID engineId, Collection<DispatcherExecutionState> active);

    @Query("select count(e) from DispatcherTaskExecution e where e.engineId = :engineId "
            + "and e.externalCleanupCompleted = false and (e.externalExecutionId is not null or e.submissionStartedAt is not null)")
    long countPendingCleanup(UUID engineId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from DispatcherTaskExecution e where e.engineId = :engineId and e.state = 'QUEUED' "
            + "order by e.queuedAt, e.executionId")
    List<DispatcherTaskExecution> findQueuedForUpdate(UUID engineId, Pageable pageable);

    @Query("select count(e) from DispatcherTaskExecution e where e.state not in :active "
            + "and e.externalCleanupCompleted = false and e.submissionStartedAt is not null "
            + "and (e.externalTerminationConfirmed is null or e.externalTerminationConfirmed = false)")
    long countUnconfirmedTerminalExecutions(Collection<DispatcherExecutionState> active);

    @Query("select e from DispatcherTaskExecution e where e.state in :states "
            + "and (e.nextMaintenanceAt is null or e.nextMaintenanceAt <= :now) "
            + "order by e.nextMaintenanceAt nulls first, e.queuedAt, e.executionId")
    List<DispatcherTaskExecution> findDueObservations(Collection<DispatcherExecutionState> states, Instant now, Pageable pageable);

    @Query("select e from DispatcherTaskExecution e where e.engineId = :engineId and e.state in :states "
            + "and (e.nextMaintenanceAt is null or e.nextMaintenanceAt <= :now) "
            + "order by e.nextMaintenanceAt nulls first, e.queuedAt, e.executionId")
    List<DispatcherTaskExecution> findDueObservations(UUID engineId, Collection<DispatcherExecutionState> states, Instant now, Pageable pageable);

    @Query("select e from DispatcherTaskExecution e where e.state in :states "
            + "and e.externalCleanupCompleted = false "
            + "and (e.externalExecutionId is not null or e.submissionStartedAt is not null) "
            + "and (e.nextMaintenanceAt is null or e.nextMaintenanceAt <= :now) "
            + "order by e.nextMaintenanceAt nulls first, e.queuedAt, e.executionId")
    List<DispatcherTaskExecution> findDueCleanup(Collection<DispatcherExecutionState> states, Instant now, Pageable pageable);

    @Query("select e from DispatcherTaskExecution e where e.engineId = :engineId and e.state in :states "
            + "and e.externalCleanupCompleted = false and (e.externalExecutionId is not null or e.submissionStartedAt is not null) "
            + "and (e.nextMaintenanceAt is null or e.nextMaintenanceAt <= :now) "
            + "order by e.nextMaintenanceAt nulls first, e.queuedAt, e.executionId")
    List<DispatcherTaskExecution> findDueCleanup(UUID engineId, Collection<DispatcherExecutionState> states, Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from DispatcherTaskExecution execution where execution.id = :id")
    Optional<DispatcherTaskExecution> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from DispatcherTaskExecution execution where execution.state = 'QUEUED' "
            + "order by execution.queuedAt, execution.executionId")
    List<DispatcherTaskExecution> findQueuedForUpdate(Pageable pageable);

    List<DispatcherTaskExecution> findAllByStateIn(Collection<DispatcherExecutionState> states);

    Page<DispatcherTaskExecution> findAllByStateIn(Collection<DispatcherExecutionState> states, Pageable pageable);

    Page<DispatcherTaskExecution> findAllByState(DispatcherExecutionState state, Pageable pageable);
}
