package cn.superhuang.data.scalpel.dispatcher.repository;

import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherEventOutbox;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherOutboxState;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispatcherEventOutboxRepository extends JpaRepository<DispatcherEventOutbox, UUID> {
    Optional<DispatcherEventOutbox> findByMessageId(UUID messageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from DispatcherEventOutbox event where event.state in :states and event.nextAttemptAt <= :now "
            + "order by event.nextAttemptAt, event.createdAt")
    List<DispatcherEventOutbox> findDueForUpdate(
            Collection<DispatcherOutboxState> states,
            Instant now,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from DispatcherEventOutbox event where event.state = 'PUBLISHING' "
            + "and event.claimedAt < :staleBefore order by event.claimedAt")
    List<DispatcherEventOutbox> findStaleClaimsForUpdate(Instant staleBefore, Pageable pageable);
}
