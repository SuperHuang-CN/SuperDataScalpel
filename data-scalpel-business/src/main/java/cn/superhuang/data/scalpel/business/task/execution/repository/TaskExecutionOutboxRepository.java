package cn.superhuang.data.scalpel.business.task.execution.repository;

import cn.superhuang.data.scalpel.business.task.execution.domain.TaskExecutionOutboxMessage;
import cn.superhuang.data.scalpel.business.task.execution.domain.TaskExecutionOutboxState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskExecutionOutboxRepository extends JpaRepository<TaskExecutionOutboxMessage, UUID> {

    Optional<TaskExecutionOutboxMessage> findByMessageId(UUID messageId);

    boolean existsByEngineIdAndStateIn(UUID engineId, Collection<TaskExecutionOutboxState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from TaskExecutionOutboxMessage message where message.state in :states "
            + "and message.nextAttemptAt <= :now order by message.nextAttemptAt, message.createdAt")
    List<TaskExecutionOutboxMessage> findDueForUpdate(
            Collection<TaskExecutionOutboxState> states,
            Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from TaskExecutionOutboxMessage message where message.state = 'PUBLISHING' "
            + "and message.claimedAt < :before order by message.claimedAt")
    List<TaskExecutionOutboxMessage> findStaleClaimsForUpdate(Instant before, Pageable pageable);
}
