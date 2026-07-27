package cn.superhuang.data.scalpel.dispatcher.repository;

import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatcherTaskExecutionRepository extends JpaRepository<DispatcherTaskExecution, UUID> {
    Optional<DispatcherTaskExecution> findByExecutionIdAndAttempt(UUID executionId, int attempt);
    Optional<DispatcherTaskExecution> findByExecutionId(UUID executionId);
    long countByState(DispatcherExecutionState state);
    long countByStateIn(Collection<DispatcherExecutionState> states);
    boolean existsByStateIn(Collection<DispatcherExecutionState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from DispatcherTaskExecution execution where execution.id = :id")
    Optional<DispatcherTaskExecution> findByIdForUpdate(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from DispatcherTaskExecution execution where execution.state = 'QUEUED' "
            + "order by execution.queuedAt, execution.executionId")
    List<DispatcherTaskExecution> findQueuedForUpdate(Pageable pageable);

    List<DispatcherTaskExecution> findAllByStateIn(Collection<DispatcherExecutionState> states);
}
