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

    boolean existsByAggregateIdAndExecutionIdAndMessageType(UUID aggregateId, UUID executionId, String messageType);

    boolean existsByEngineIdAndStateIn(UUID engineId, Collection<TaskExecutionOutboxState> states);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select message from TaskExecutionOutboxMessage message where message.state in :states "
            + "and message.nextAttemptAt <= :now "
            + "and (message.messageType in ('SUBMIT_EXECUTION', 'START_STREAMING_EXECUTION') or not exists (select s.id from TaskExecutionOutboxMessage s "
            + "where s.aggregateId = message.aggregateId and s.executionId = message.executionId "
            + "and s.messageType in ('SUBMIT_EXECUTION', 'START_STREAMING_EXECUTION') and s.state <> 'PUBLISHED')) "
            + "order by message.nextAttemptAt, message.createdAt")
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
