package cn.superhuang.data.scalpel.business.task.repository;

import cn.superhuang.data.scalpel.business.task.domain.TaskTmqConsumerGroupCleanup;
import cn.superhuang.data.scalpel.business.task.domain.TmqConsumerGroupCleanupState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TaskTmqConsumerGroupCleanupRepository
        extends JpaRepository<TaskTmqConsumerGroupCleanup, UUID> {
    Optional<TaskTmqConsumerGroupCleanup> findByDataSourceIdAndTopicNameAndConsumerGroupId(
            UUID dataSourceId, String topicName, String consumerGroupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TaskTmqConsumerGroupCleanup>
    findFirstByStateInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<TmqConsumerGroupCleanupState> states, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TaskTmqConsumerGroupCleanup> findFirstByStateAndClaimedAtLessThanEqualOrderByClaimedAtAsc(
            TmqConsumerGroupCleanupState state, Instant claimedBefore);

    long countByTaskIdAndStateIn(UUID taskId, Collection<TmqConsumerGroupCleanupState> states);
}
