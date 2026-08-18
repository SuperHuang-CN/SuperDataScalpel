package cn.superhuang.data.scalpel.business.assistant.repository;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSet;
import cn.superhuang.data.scalpel.business.assistant.domain.AssistantChangeSetStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssistantChangeSetRepository extends JpaRepository<AssistantChangeSet, UUID> {

    Optional<AssistantChangeSet> findByIdAndOwnerUsername(UUID id, String ownerUsername);

    Optional<AssistantChangeSet> findFirstBySessionIdAndStatusOrderByCreatedAtDesc(
            UUID sessionId,
            AssistantChangeSetStatus status
    );

    Optional<AssistantChangeSet> findFirstBySessionIdOrderByCreatedAtDesc(UUID sessionId);

    List<AssistantChangeSet> findAllBySessionIdAndStatus(UUID sessionId, AssistantChangeSetStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select changeSet from AssistantChangeSet changeSet where changeSet.id = :id")
    Optional<AssistantChangeSet> findLockedById(@Param("id") UUID id);
}
