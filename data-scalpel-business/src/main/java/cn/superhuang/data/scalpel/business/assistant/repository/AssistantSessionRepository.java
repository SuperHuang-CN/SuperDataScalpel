package cn.superhuang.data.scalpel.business.assistant.repository;

import cn.superhuang.data.scalpel.business.assistant.domain.AssistantSession;
import cn.superhuang.data.scalpel.search.SearchRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface AssistantSessionRepository extends SearchRepository<AssistantSession, UUID> {

    Optional<AssistantSession> findByIdAndOwnerUsername(UUID id, String ownerUsername);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AssistantSession> findLockedByIdAndOwnerUsername(UUID id, String ownerUsername);
}
